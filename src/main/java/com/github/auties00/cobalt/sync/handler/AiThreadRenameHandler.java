package com.github.auties00.cobalt.sync.handler;

import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.model.sync.SyncPatchType;
import com.github.auties00.cobalt.sync.crypto.DecryptedMutation;

/**
 * Handles AI thread rename actions.
 *
 * <p>Index format: ["ai_thread_rename", ...]
 */
public final class AiThreadRenameHandler implements WebAppStateActionHandler {
    public static final AiThreadRenameHandler INSTANCE = new AiThreadRenameHandler();

    private AiThreadRenameHandler() {

    }

    @Override
    public String actionName() {
        return "ai_thread_rename";
    }

    @Override
    public SyncPatchType collectionName() {
        return SyncPatchType.REGULAR_LOW;
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
