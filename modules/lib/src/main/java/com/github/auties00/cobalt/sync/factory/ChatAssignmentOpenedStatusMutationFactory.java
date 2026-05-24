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
 * Builds outgoing app-state mutations that record whether a Business agent has opened a chat.
 *
 * @apiNote
 * Drives the team-inbox "agent has read this chat" signal that
 * {@code WAWebBizChatAssignmentOpenedAction} surfaces: when an agent opens
 * (or closes) an assigned chat, the resulting mutation is pushed via
 * {@link com.github.auties00.cobalt.sync.WebAppStateService} so the
 * primary device and other agent devices see the same open state. The
 * factory is the outgoing-mutation counterpart of
 * {@link com.github.auties00.cobalt.sync.handler.ChatAssignmentOpenedStatusHandler}.
 *
 * @implNote
 * This implementation accepts only a single triple per call, unlike WA Web's
 * batched {@code createChatOpenedMutations(list)}; Cobalt callers loop at
 * their own level when several open-state transitions must be flushed
 * together.
 */
public final class ChatAssignmentOpenedStatusMutationFactory {
    /**
     * Creates an instance with no collaborators.
     *
     * @apiNote
     * The factory is stateless; a single instance may be shared across the
     * lifetime of the client.
     */
    public ChatAssignmentOpenedStatusMutationFactory() {

    }

    /**
     * Returns a SET mutation that records whether the given agent has the chat open.
     *
     * @apiNote
     * The mutation index follows
     * {@snippet :
     *     ["agentChatAssignmentOpenedStatus", chatJid.toString(), agentId]
     * }
     * and the {@link ChatAssignmentOpenedStatusAction} sub-message carries
     * the {@code chatOpened} flag. The composite index allows multiple
     * agents to record open state for the same chat independently.
     *
     * @implNote
     * This implementation pins the action version through
     * {@link ChatAssignmentOpenedStatusAction#ACTION_VERSION}, which is the
     * shared {@code CHAT_ASSIGNMENT_SYNC_VERSION} constant; the receive-side
     * handler orphans the mutation if the underlying chat-assignment row
     * does not yet exist in the agent collection.
     *
     * @param chatJid    the chat {@link Jid} whose open state is being recorded
     * @param agentId    the identifier of the agent whose state changed
     * @param chatOpened {@code true} when the agent has the chat open, {@code false} when they have left it
     * @param timestamp  the mutation timestamp
     * @return the pending mutation ready to be queued for outbound app-state sync
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
