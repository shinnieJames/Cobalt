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
 * Handles favorites sync actions.
 *
 * <p>Per WhatsApp Web, the favorites handler extends {@code AccountSyncdActionBase}
 * and manages the user's favorite chats collection. It uses collection
 * {@code RegularHigh}, action name {@code "favorites"}, and version {@code 1}.
 *
 * <p>The handler applies only the mutation with the latest timestamp from a batch,
 * replacing the entire favorites collection rather than processing each mutation
 * sequentially.
 */
@WhatsAppWebModule(moduleName = "WAWebFavoritesSync")
public final class FavoritesHandler implements WebAppStateActionHandler {

    /**
     * Logger for diagnostic messages.
     */
    private static final Logger LOGGER = Logger.getLogger(FavoritesHandler.class.getName());

    /**
     * Private constructor to enforce singleton pattern.
     */
    public FavoritesHandler() {

    }

    /**
     * Returns the action name for favorites sync.
     * @return the action name {@code "favorites"}
     */
    @WhatsAppWebExport(moduleName = "WAWebFavoritesSync", exports = "default", adaptation = WhatsAppAdaptation.DIRECT)
    @Override
    public String actionName() {
        return FavoritesAction.ACTION_NAME;
    }

    /**
     * Returns the sync collection for favorites.
     * @return {@link SyncPatchType#REGULAR_HIGH}
     */
    @WhatsAppWebExport(moduleName = "WAWebFavoritesSync", exports = "default", adaptation = WhatsAppAdaptation.DIRECT)
    @Override
    public SyncPatchType collectionName() {
        return FavoritesAction.COLLECTION_NAME;
    }

    /**
     * Returns the mutation format version for favorites.
     * @return the version number {@code 1}
     */
    @WhatsAppWebExport(moduleName = "WAWebFavoritesSync", exports = "default", adaptation = WhatsAppAdaptation.DIRECT)
    @Override
    public int version() {
        return FavoritesAction.ACTION_VERSION;
    }

    /**
     * Applies a batch of favorites mutations, keeping only the latest by timestamp.
     *
     * <p>Per WhatsApp Web {@code WAWebFavoritesSync.applyMutations}: iterates all mutations,
     * returning {@code malformedActionValue} for non-SET operations and mutations
     * whose {@code favoritesAction.favorites} is {@code null}. Tracks the mutation
     * with the latest timestamp and applies only that one to the store. All valid
     * mutations receive a {@code Success} result.
     *
     * <p>After identifying the latest mutation, resolves each favorite's JID via
     * the chat store, with LID-to-phone fallback, and replaces the entire
     * favorites collection in the store.
     * @param client    the WhatsApp client instance
     * @param mutations the batch of mutations to apply
     * @return a list of results parallel to the input
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
     * Applies a single favorites mutation and returns a detailed result.
     *
     * <p>Validates the operation type and action type, then resolves and
     * stores the favorites. This method is the single-mutation entry point
     * that performs the same validation as the batch path but always applies
     * the provided mutation.
     * @param client   the WhatsApp client instance
     * @param mutation the mutation to apply
     * @return the detailed application result
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
     * Applies the favorites from the latest (or only) mutation to the store.
     *
     * <p>Per WhatsApp Web {@code WAWebFavoritesSync.applyMutations}: after identifying
     * the latest mutation, extracts the favorites list, filters out entries with
     * {@code null} IDs, resolves each via the chat store (with LID-to-phone
     * fallback), and replaces the entire favorites collection.
     *
     * <p>Resolution order per WA Web:
     * <ol>
     *   <li>{@code resolveChatForMutationIndex(createWid(id))} — chat table lookup</li>
     *   <li>If failed and {@code isLidMigrated() && wid.isLid()}: {@code getPhoneNumber(wid)}</li>
     *   <li>Otherwise: use raw ID as-is</li>
     * </ol>
     * @param client   the WhatsApp client instance
     * @param mutation the mutation containing the favorites to apply
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
