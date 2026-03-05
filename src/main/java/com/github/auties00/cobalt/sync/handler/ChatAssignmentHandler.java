package com.github.auties00.cobalt.sync.handler;

import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.model.sync.SyncPatchType;
import com.github.auties00.cobalt.sync.crypto.DecryptedMutation;

/**
 * Handles chat assignment actions.
 *
 * <p>This handler processes mutations that assign chats to agents
 * in business accounts.
 *
 * <p>Index format: ["chatAssignmentAction", "chatJid"]
 */
public final class ChatAssignmentHandler implements WebAppStateActionHandler {
    public static final ChatAssignmentHandler INSTANCE = new ChatAssignmentHandler();

    private ChatAssignmentHandler() {

    }

    @Override
    public String actionName() {
        return "chatAssignmentAction";
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
        // Not handled
        return true;
    }
}
