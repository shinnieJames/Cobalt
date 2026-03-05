package com.github.auties00.cobalt.sync.handler;

import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.model.sync.SyncPatchType;
import com.github.auties00.cobalt.sync.crypto.DecryptedMutation;

/**
 * Handles avatar updated actions.
 *
 * <p>Index format: ["avatar_updated_action", ...]
 */
public final class AvatarUpdatedHandler implements WebAppStateActionHandler {
    public static final AvatarUpdatedHandler INSTANCE = new AvatarUpdatedHandler();

    private AvatarUpdatedHandler() {

    }

    @Override
    public String actionName() {
        return "avatar_updated_action";
    }

    @Override
    public SyncPatchType collectionName() {
        return SyncPatchType.REGULAR;
    }

    @Override
    public int version() {
        return 7;
    }

    @Override
    public boolean applyMutation(WhatsAppClient client, DecryptedMutation.Trusted mutation) {
        return true;
    }
}
