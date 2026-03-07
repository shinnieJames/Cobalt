package com.github.auties00.cobalt.sync.handler;

import com.alibaba.fastjson2.JSON;
import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.sync.SyncPatchType;
import com.github.auties00.cobalt.model.sync.action.chat.ChatAssignmentAction;
import com.github.auties00.cobalt.model.sync.data.SyncdOperation;
import com.github.auties00.cobalt.sync.crypto.DecryptedMutation;

/**
 * Handles chat assignment actions.
 *
 * <p>This handler processes mutations that assign chats to agents
 * in business accounts.
 *
 * <p>Index format: ["agentChatAssignment", "chatJid"]
 */
public final class ChatAssignmentHandler implements WebAppStateActionHandler {
    /**
     * The singleton instance of {@code ChatAssignmentHandler}.
     */
    public static final ChatAssignmentHandler INSTANCE = new ChatAssignmentHandler();

    private ChatAssignmentHandler() {

    }

    @Override
    public String actionName() {
        return ChatAssignmentAction.ACTION_NAME;
    }

    @Override
    public SyncPatchType collectionName() {
        return ChatAssignmentAction.COLLECTION_NAME;
    }

    @Override
    public int version() {
        return ChatAssignmentAction.ACTION_VERSION;
    }

    @Override
    public boolean applyMutation(WhatsAppClient client, DecryptedMutation.Trusted mutation) {
        var indexArray = JSON.parseArray(mutation.index());
        var chatJidString = indexArray.getString(1);
        if (chatJidString == null || chatJidString.isEmpty()) {
            return true;
        }

        if (mutation.operation() != SyncdOperation.SET) {
            return true;
        }

        if (!(mutation.value().action().orElse(null) instanceof ChatAssignmentAction action)) {
            return true;
        }

        var chatJid = Jid.of(chatJidString);
        var chat = client.store().findChatByJid(chatJid);
        if (chat.isEmpty()) {
            return false;
        }

        return true;
    }
}
