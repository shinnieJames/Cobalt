package com.github.auties00.cobalt.sync.handler;

import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.sync.MutationApplicationResult;
import com.github.auties00.cobalt.model.sync.SyncPatchType;
import com.github.auties00.cobalt.model.sync.action.media.FavoritesAction;
import com.github.auties00.cobalt.model.sync.data.SyncdOperation;
import com.github.auties00.cobalt.sync.crypto.DecryptedMutation;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;

/**
 * Applies the {@code favorites} app-state sync action that replaces the
 * user's pinned-favourites collection across linked devices.
 *
 * @apiNote
 * Drives the chat-list "Favourites" section: the primary device fans out
 * the entire ordered favourites list through the
 * {@link SyncPatchType#REGULAR_HIGH} collection. The action is replace,
 * not merge; only the most recent mutation in a batch is applied so
 * intermediate edits performed in quick succession on the primary do not
 * surface partially.
 *
 * @implNote
 * This implementation deduplicates the batch by tracking the latest
 * mutation by timestamp and applies it once, mirroring WA Web's
 * {@code WAWebFavoritesSync.applyMutations}. JID resolution mirrors the
 * WA Web order: chat-table lookup first, LID-to-phone fallback when the
 * raw JID has the LID server, then the raw JID itself; the
 * {@code resolveChatForMutationIndex} cache used by WA Web is replaced
 * by direct
 * {@link com.github.auties00.cobalt.store.WhatsAppStore#findChatByJid(Jid)}
 * calls, and the {@code frontendFireAndForget("setFavoriteCollection")}
 * RPC is replaced by a direct store write.
 */
@WhatsAppWebModule(moduleName = "WAWebFavoritesSync")
public final class FavoritesHandler implements WebAppStateActionHandler {

    /**
     * The {@link Logger} that records the malformed and unsupported
     * mutation tallies after {@link #applyMutationBatch} completes.
     */
    private static final Logger LOGGER = Logger.getLogger(FavoritesHandler.class.getName());

    /**
     * Constructs a new singleton {@link FavoritesHandler}.
     */
    public FavoritesHandler() {

    }

    /**
     * {@inheritDoc}
     */
    @WhatsAppWebExport(moduleName = "WAWebFavoritesSync", exports = "default", adaptation = WhatsAppAdaptation.DIRECT)
    @Override
    public String actionName() {
        return FavoritesAction.ACTION_NAME;
    }

    /**
     * {@inheritDoc}
     */
    @WhatsAppWebExport(moduleName = "WAWebFavoritesSync", exports = "default", adaptation = WhatsAppAdaptation.DIRECT)
    @Override
    public SyncPatchType collectionName() {
        return FavoritesAction.COLLECTION_NAME;
    }

    /**
     * {@inheritDoc}
     */
    @WhatsAppWebExport(moduleName = "WAWebFavoritesSync", exports = "default", adaptation = WhatsAppAdaptation.DIRECT)
    @Override
    public int version() {
        return FavoritesAction.ACTION_VERSION;
    }

    /**
     * {@inheritDoc}
     *
     * @implNote
     * This implementation walks the batch, classifying each entry,
     * tracking the {@link DecryptedMutation.Trusted} with the highest
     * timestamp and applying only that one through
     * {@link #applyLatestMutation(WhatsAppClient, DecryptedMutation.Trusted)};
     * every other valid entry receives
     * {@link MutationApplicationResult#success()}, mirroring WA Web's
     * "all valid mutations succeed but only the latest mutates state"
     * shape. Unsupported and malformed counts are logged through
     * {@link #LOGGER} after the loop, matching WA Web's
     * {@code WALogger.WARN} emission.
     */
    @WhatsAppWebExport(moduleName = "WAWebFavoritesSync", exports = "default", adaptation = WhatsAppAdaptation.ADAPTED)
    @Override
    public List<MutationApplicationResult> applyMutationBatch(WhatsAppClient client, List<DecryptedMutation.Trusted> mutations) {
        DecryptedMutation.Trusted latest = null;
        var unsupportedCount = 0;
        var malformedCount = 0;
        var results = new ArrayList<MutationApplicationResult>(mutations.size());
        for (var mutation : mutations) {
            if (mutation.operation() != SyncdOperation.SET) {
                unsupportedCount++;
                results.add(SyncdIndexUtils.malformedActionValue(collectionName().name()));
                continue;
            }

            if (!(mutation.value().action().orElse(null) instanceof FavoritesAction)) {
                malformedCount++;
                results.add(SyncdIndexUtils.malformedActionValue(collectionName().name()));
                continue;
            }

            if (latest == null || mutation.timestamp().compareTo(latest.timestamp()) > 0) {
                latest = mutation;
            }
            results.add(MutationApplicationResult.success());
        }

        if (unsupportedCount > 0) {
            LOGGER.warning("favorites sync: " + unsupportedCount + " operations not supported");
        }
        if (malformedCount > 0) {
            LOGGER.warning("favorites sync: " + malformedCount + " malformed mutations");
        }

        if (latest != null) {
            applyLatestMutation(client, latest);
        }

        return results;
    }

    /**
     * {@inheritDoc}
     *
     * @implNote
     * This implementation runs the same validation as the batch path but
     * unconditionally applies the supplied mutation, so callers that
     * dispatch a single {@link FavoritesAction} outside a batch still
     * see the favourites collection mutated.
     */
    @WhatsAppWebExport(moduleName = "WAWebFavoritesSync", exports = "default", adaptation = WhatsAppAdaptation.ADAPTED)
    @Override
    public MutationApplicationResult applyMutation(WhatsAppClient client, DecryptedMutation.Trusted mutation) {
        if (mutation.operation() != SyncdOperation.SET) {
            return SyncdIndexUtils.malformedActionValue(collectionName().name());
        }

        if (!(mutation.value().action().orElse(null) instanceof FavoritesAction)) {
            return SyncdIndexUtils.malformedActionValue(collectionName().name());
        }

        applyLatestMutation(client, mutation);
        return MutationApplicationResult.success();
    }

    /**
     * Replaces the favourites collection with the entries carried by the
     * supplied mutation.
     *
     * @apiNote
     * Invoked by both {@link #applyMutationBatch(WhatsAppClient, List)}
     * and {@link #applyMutation(WhatsAppClient, DecryptedMutation.Trusted)}
     * once the latest valid mutation has been identified.
     *
     * @implNote
     * This implementation skips entries whose raw id is absent rather
     * than failing the entire batch; for each surviving entry the chat
     * table is consulted first, then a LID-to-phone fallback is attempted
     * via
     * {@link com.github.auties00.cobalt.store.WhatsAppStore#findPhoneByLid(Jid)}
     * when the raw {@link Jid} carries the LID server, and the raw JID is
     * preserved as a final fallback. WA Web's
     * {@code resolveChatForMutationIndex} cache is replaced by direct
     * store calls because Cobalt's batch sizes are smaller and the cache
     * would be overhead.
     *
     * @param client   the {@link WhatsAppClient} whose store will be
     *                 mutated
     * @param mutation the source mutation; its
     *                 {@link FavoritesAction#favorites()} list is
     *                 resolved and persisted in iteration order
     */
    @WhatsAppWebExport(moduleName = "WAWebFavoritesSync", exports = "default", adaptation = WhatsAppAdaptation.ADAPTED)
    private void applyLatestMutation(WhatsAppClient client, DecryptedMutation.Trusted mutation) {
        var action = (FavoritesAction) mutation.value().action().orElseThrow();
        var favorites = new ArrayList<Jid>();
        for (var favorite : action.favorites()) {
            var rawId = favorite.id().orElse(null);
            if (rawId == null) {
                continue;
            }

            var rawJid = Jid.of(rawId);
            var resolved = client.store().findChatByJid(rawJid)
                    .map(entry -> entry.jid())
                    .or(() -> rawJid.hasLidServer() ? client.store().findPhoneByLid(rawJid) : Optional.<Jid>empty())
                    .orElse(rawJid);
            favorites.add(resolved);
        }

        client.store().setFavoriteChats(favorites);
    }

}
