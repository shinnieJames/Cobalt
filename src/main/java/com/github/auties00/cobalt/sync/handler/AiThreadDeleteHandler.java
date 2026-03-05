package com.github.auties00.cobalt.sync.handler;

import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.model.sync.SyncPatchType;
import com.github.auties00.cobalt.sync.crypto.DecryptedMutation;

/**
 * Handles AI thread delete actions.
 *
 * <p>Index format: ["ai_thread_delete", ...]
 */
public final class AiThreadDeleteHandler implements WebAppStateActionHandler {
    public static final AiThreadDeleteHandler INSTANCE = new AiThreadDeleteHandler();

    private AiThreadDeleteHandler() {

    }

    @Override
    public String actionName() {
        return "ai_thread_delete";
    }

    @Override
    public SyncPatchType collectionName() {
        return SyncPatchType.REGULAR_HIGH;
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
