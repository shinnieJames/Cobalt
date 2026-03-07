package com.github.auties00.cobalt.sync.handler;

import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.model.sync.SyncPatchType;
import com.github.auties00.cobalt.model.sync.action.media.AvatarUpdatedAction;
import com.github.auties00.cobalt.model.sync.data.SyncdOperation;
import com.github.auties00.cobalt.sync.crypto.DecryptedMutation;

/**
 * Handles avatar updated actions.
 *
 * <p>Per WhatsApp Web {@code WAWebStickersAvatarUpdatedSyncAction}, only SET
 * operations are supported. The handler validates that the action value contains
 * a non-null {@code eventType}.
 *
 * <p>Index format: ["avatar_updated_action"]
 */
public final class AvatarUpdatedHandler implements WebAppStateActionHandler {
    public static final AvatarUpdatedHandler INSTANCE = new AvatarUpdatedHandler();

    private AvatarUpdatedHandler() {

    }

    @Override
    public String actionName() {
        return AvatarUpdatedAction.ACTION_NAME;
    }

    @Override
    public SyncPatchType collectionName() {
        return AvatarUpdatedAction.COLLECTION_NAME;
    }

    @Override
    public int version() {
        return AvatarUpdatedAction.ACTION_VERSION;
    }

    @Override
    public boolean applyMutation(WhatsAppClient client, DecryptedMutation.Trusted mutation) {
        if (mutation.operation() != SyncdOperation.SET) {
            return true;
        }

        if (!(mutation.value().action().orElse(null) instanceof AvatarUpdatedAction action)) {
            return true;
        }

        if (action.eventType().isEmpty()) {
            return true;
        }

        return true;
    }
}
