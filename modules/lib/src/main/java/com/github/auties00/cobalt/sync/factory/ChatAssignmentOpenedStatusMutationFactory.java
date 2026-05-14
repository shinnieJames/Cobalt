package com.github.auties00.cobalt.sync.factory;

import com.alibaba.fastjson2.JSON;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.sync.SyncActionValueBuilder;
import com.github.auties00.cobalt.model.sync.action.chat.ChatAssignmentOpenedStatusAction;
import com.github.auties00.cobalt.model.sync.action.chat.ChatAssignmentOpenedStatusActionBuilder;
import com.github.auties00.cobalt.model.sync.data.SyncdOperation;
import com.github.auties00.cobalt.sync.SyncPendingMutation;
import com.github.auties00.cobalt.sync.crypto.DecryptedMutation;

import java.time.Instant;
import java.util.List;

/**
 * Builds outgoing chat-assignment-opened-status sync mutations.
 *
 * <p>Mirrors the {@code createChatOpenedMutations} export of WhatsApp Web's
 * {@code WAWebChatAssignmentOpenedStatusSync} module. The factory is the
 * outgoing-mutation counterpart of
 * {@link com.github.auties00.cobalt.sync.handler.ChatAssignmentOpenedStatusHandler}.
 */
public final class ChatAssignmentOpenedStatusMutationFactory {
    /**
     * Constructs a chat-assignment-opened-status mutation factory.
     */
    public ChatAssignmentOpenedStatusMutationFactory() {

    }

    /**
     * Builds a pending SET mutation that records whether the given agent has
     * opened the given chat.
     *
     * <p>Per WhatsApp Web
     * {@code WAWebChatAssignmentOpenedStatusSync.default.createChatOpenedMutations},
     * the mutation carries a {@link ChatAssignmentOpenedStatusAction} whose
     * {@code chatOpened} flag records the agent's open state. The index is
     * {@code ["agentChatAssignmentOpenedStatus", chatJid, agentId]}.
     *
     * @param chatJid     the JID of the chat
     * @param agentId     the agent identifier
     * @param chatOpened  {@code true} when the agent has opened the chat
     * @param timestamp   the mutation timestamp
     * @return the pending mutation for the opened-state change
     */
    @WhatsAppWebExport(moduleName = "WAWebChatAssignmentOpenedStatusSync", exports = "createChatOpenedMutations", adaptation = WhatsAppAdaptation.ADAPTED)
    public SyncPendingMutation createChatOpenedMutation(
            Jid chatJid,
            String agentId,
            boolean chatOpened,
            Instant timestamp
    ) {
        var action = new ChatAssignmentOpenedStatusActionBuilder()
                .chatOpened(chatOpened)
                .build();
        var value = new SyncActionValueBuilder()
                .timestamp(timestamp)
                .chatAssignmentOpenedStatus(action)
                .build();
        var index = JSON.toJSONString(List.of(ChatAssignmentOpenedStatusAction.ACTION_NAME, chatJid.toString(), agentId));
        var mutation = new DecryptedMutation.Trusted(
                index,
                value,
                SyncdOperation.SET,
                timestamp,
                ChatAssignmentOpenedStatusAction.ACTION_VERSION
        );
        return new SyncPendingMutation(mutation, 0);
    }
}
