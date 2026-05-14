package com.github.auties00.cobalt.sync.factory;

import com.alibaba.fastjson2.JSON;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.sync.SyncActionValueBuilder;
import com.github.auties00.cobalt.model.sync.action.chat.ChatAssignmentAction;
import com.github.auties00.cobalt.model.sync.action.chat.ChatAssignmentActionBuilder;
import com.github.auties00.cobalt.model.sync.data.SyncdOperation;
import com.github.auties00.cobalt.sync.SyncPendingMutation;
import com.github.auties00.cobalt.sync.crypto.DecryptedMutation;

import java.time.Instant;
import java.util.List;

/**
 * Builds outgoing chat-assignment sync mutations.
 *
 * <p>Mirrors the {@code createChatAssignmentMutations} export of WhatsApp
 * Web's {@code WAWebChatAssignmentSync} module. The factory is the
 * outgoing-mutation counterpart of
 * {@link com.github.auties00.cobalt.sync.handler.ChatAssignmentHandler}.
 */
public final class ChatAssignmentMutationFactory {
    /**
     * Constructs a chat-assignment mutation factory.
     */
    public ChatAssignmentMutationFactory() {

    }

    /**
     * Builds a pending SET mutation that assigns the given chat to the given
     * agent.
     *
     * <p>Per WhatsApp Web
     * {@code WAWebChatAssignmentSync.default.createChatAssignmentMutations},
     * the mutation carries a {@link ChatAssignmentAction} whose
     * {@code deviceAgentID} is the target agent id (an empty string
     * unassigns the chat). The mutation index is
     * {@code ["agentChatAssignment", chatJid]}.
     *
     * @param chatJid   the JID of the chat being assigned
     * @param agentId   the target agent id, or {@code ""} to unassign
     * @param timestamp the mutation timestamp
     * @return the pending mutation for the chat assignment
     */
    @WhatsAppWebExport(moduleName = "WAWebChatAssignmentSync", exports = "createChatAssignmentMutations", adaptation = WhatsAppAdaptation.ADAPTED)
    public SyncPendingMutation createChatAssignmentMutation(
            Jid chatJid,
            String agentId,
            Instant timestamp
    ) {
        var action = new ChatAssignmentActionBuilder()
                .deviceAgentID(agentId)
                .build();
        var value = new SyncActionValueBuilder()
                .timestamp(timestamp)
                .chatAssignment(action)
                .build();
        var index = JSON.toJSONString(List.of(ChatAssignmentAction.ACTION_NAME, chatJid.toString()));
        var mutation = new DecryptedMutation.Trusted(
                index,
                value,
                SyncdOperation.SET,
                timestamp,
                ChatAssignmentAction.ACTION_VERSION
        );
        return new SyncPendingMutation(mutation, 0);
    }
}
