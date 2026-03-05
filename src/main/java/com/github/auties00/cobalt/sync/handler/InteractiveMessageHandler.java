package com.github.auties00.cobalt.sync.handler;

import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.model.sync.SyncPatchType;
import com.github.auties00.cobalt.sync.crypto.DecryptedMutation;

/**
 * Handles interactive message actions.
 *
 * <p>Index format: ["interactive_message_action", ...]
 */
public final class InteractiveMessageHandler implements WebAppStateActionHandler {
    public static final InteractiveMessageHandler INSTANCE = new InteractiveMessageHandler();

    private InteractiveMessageHandler() {

    }

    @Override
    public String actionName() {
        return "interactive_message_action";
    }

    @Override
    public SyncPatchType collectionName() {
        return SyncPatchType.REGULAR_LOW;
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
