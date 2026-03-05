package com.github.auties00.cobalt.sync.handler;

import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.model.sync.SyncPatchType;
import com.github.auties00.cobalt.sync.crypto.DecryptedMutation;

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
        return "favorites";
    }

    @Override
    public SyncPatchType collectionName() {
        return SyncPatchType.REGULAR_HIGH;
    }

    @Override
    public int version() {
        return 1;
    }

    @Override
    public boolean applyMutation(WhatsAppClient client, DecryptedMutation.Trusted mutation) {
        return true;
    }
}
