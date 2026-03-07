package com.github.auties00.cobalt.sync.handler;

import com.alibaba.fastjson2.JSON;
import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.sync.SyncPatchType;
import com.github.auties00.cobalt.model.sync.action.chat.ChatAssignmentOpenedStatusAction;
import com.github.auties00.cobalt.model.sync.data.SyncdOperation;
import com.github.auties00.cobalt.sync.crypto.DecryptedMutation;

/**
 * Handles chat assignment opened status actions.
 *
 * <p>This handler processes mutations that track whether an assigned chat
 * has been opened by the agent.
 *
 * <p>Index format: ["agentChatAssignmentOpenedStatus", "chatJid", "agentId"]
 */
public final class ChatAssignmentOpenedStatusHandler implements WebAppStateActionHandler {
    /**
     * The singleton instance of {@code ChatAssignmentOpenedStatusHandler}.
     */
    public static final ChatAssignmentOpenedStatusHandler INSTANCE = new ChatAssignmentOpenedStatusHandler();

    private ChatAssignmentOpenedStatusHandler() {

    }

    @Override
    public String actionName() {
        return ChatAssignmentOpenedStatusAction.ACTION_NAME;
    }

    @Override
    public SyncPatchType collectionName() {
        return ChatAssignmentOpenedStatusAction.COLLECTION_NAME;
    }

    @Override
    public int version() {
        return ChatAssignmentOpenedStatusAction.ACTION_VERSION;
    }

    @Override
    public boolean applyMutation(WhatsAppClient client, DecryptedMutation.Trusted mutation) {
        var indexArray = JSON.parseArray(mutation.index());
        var chatJidString = indexArray.getString(1);
        var agentId = indexArray.getString(2);
        if (chatJidString == null || agentId == null) {
            return true;
        }

        if (mutation.operation() != SyncdOperation.SET) {
            return true;
        }

        var chatJid = Jid.of(chatJidString);
        var chat = client.store().findChatByJid(chatJid);
        if (chat.isEmpty()) {
            return false;
        }

        if (!(mutation.value().action().orElse(null) instanceof ChatAssignmentOpenedStatusAction)) {
            return true;
        }

        return true;
    }
}
