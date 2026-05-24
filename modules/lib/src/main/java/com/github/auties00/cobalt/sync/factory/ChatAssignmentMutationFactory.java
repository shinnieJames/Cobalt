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
 * Builds outgoing app-state mutations that assign a chat to (or unassign it from) a Business agent.
 *
 * @apiNote
 * Drives the WhatsApp Business team-inbox feature: SMB admins assign chats
 * to specific agents through the Web UI, and the resulting mutations are
 * pushed via {@code WAWebBizChatAssignmentAction} so every agent device sees
 * the same assignment state. The factory is the outgoing-mutation
 * counterpart of
 * {@link com.github.auties00.cobalt.sync.handler.ChatAssignmentHandler}.
 *
 * @implNote
 * This implementation does not perform the WA Web batch-style fan-out of
 * {@code createChatAssignmentMutations}, which loops over a list of
 * {@code {agentId, chatId}} pairs; Cobalt callers loop at their own level
 * and invoke {@link #createChatAssignmentMutation} once per pair.
 */
public final class ChatAssignmentMutationFactory {
    /**
     * Creates an instance with no collaborators.
     *
     * @apiNote
     * The factory is stateless; a single instance may be shared across the
     * lifetime of the client.
     */
    public ChatAssignmentMutationFactory() {

    }

    /**
     * Returns a SET mutation that assigns the given chat to the given agent.
     *
     * @apiNote
     * Pass {@code agentId == ""} to unassign the chat; the receive-side
     * handler treats an empty {@code deviceAgentID} as a removal and
     * collects the chat into the bulk-remove path of
     * {@code WAWebSchemaChatAssignment.bulkRemove}. The mutation index follows
     * {@snippet :
     *     ["agentChatAssignment", chatJid.toString()]
     * }
     * and the {@link ChatAssignmentAction} sub-message carries
     * {@code deviceAgentID}.
     *
     * @implNote
     * This implementation pins the action version through
     * {@link ChatAssignmentAction#ACTION_VERSION}, which matches the
     * {@code CHAT_ASSIGNMENT_SYNC_VERSION} constant exposed by
     * {@code WAWebProtobufSyncAction} and consumed by both
     * {@code createChatAssignmentMutations} and the receive-side
     * {@code applyMutations} branch.
     *
     * @param chatJid   the chat {@link Jid} being assigned
     * @param agentId   the target agent identifier, or empty string to unassign
     * @param timestamp the mutation timestamp
     * @return the pending mutation ready to be queued for outbound app-state sync
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
