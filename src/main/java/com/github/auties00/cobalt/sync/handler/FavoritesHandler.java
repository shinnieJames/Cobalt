package com.github.auties00.cobalt.sync.handler;

import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.sync.SyncPatchType;
import com.github.auties00.cobalt.model.sync.action.media.FavoritesAction;
import com.github.auties00.cobalt.model.sync.data.SyncdOperation;
import com.github.auties00.cobalt.sync.crypto.DecryptedMutation;

import java.util.ArrayList;
import java.util.List;

/**
 * Handles favorites actions.
 *
 * <p>Index format: ["favorites", ...]
 */
public final class FavoritesHandler implements WebAppStateActionHandler {
    public static final FavoritesHandler INSTANCE = new FavoritesHandler();

    private FavoritesHandler() {

    }

    @Override
    public String actionName() {
        return FavoritesAction.ACTION_NAME;
    }

    @Override
    public SyncPatchType collectionName() {
        return FavoritesAction.COLLECTION_NAME;
    }

    @Override
    public int version() {
        return FavoritesAction.ACTION_VERSION;
    }

    @Override
    public boolean applyMutation(WhatsAppClient client, DecryptedMutation.Trusted mutation) {
        // Web: non-SET is treated as malformed (acknowledged, not orphaned)
        if (mutation.operation() != SyncdOperation.SET) {
            return true;
        }

        if (!(mutation.value().action().orElse(null) instanceof FavoritesAction action)) {
            return false;
        }

        var jids = action.favorites()
                .stream()
                .flatMap(fav -> fav.id().stream())
                .map(Jid::of)
                .toList();
        client.store().setFavoriteChats(jids);
        return true;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Per WhatsApp Web {@code WAWebFavoritesSync.applyMutations}: iterates
     * all mutations to find the one with the highest timestamp, then applies
     * only that mutation. Non-SET and malformed mutations are acknowledged
     * (not orphaned) but do not participate in the timestamp comparison.
     */
    @Override
    public List<Boolean> applyMutationBatch(WhatsAppClient client, List<DecryptedMutation.Trusted> mutations) {
        DecryptedMutation.Trusted latest = null;
        var results = new ArrayList<Boolean>(mutations.size());
        for (var mutation : mutations) {
            if (mutation.operation() != SyncdOperation.SET) {
                // Web: non-SET increments unsupported counter, returns malformedActionValue
                results.add(true);
                continue;
            }

            if (!(mutation.value().action().orElse(null) instanceof FavoritesAction)) {
                // Web: missing favorites increments malformed counter, returns malformedActionValue
                results.add(true);
                continue;
            }

            if (latest == null || mutation.timestamp().compareTo(latest.timestamp()) > 0) {
                latest = mutation;
            }
            results.add(true);
        }

        if (latest != null) {
            // Apply only the latest-timestamped mutation
            applyMutation(client, latest);
        }

        return results;
    }
}
