package com.github.auties00.cobalt.sync.handler;

import com.alibaba.fastjson2.JSON;
import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.chat.ChatAssignmentBuilder;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.sync.MutationApplicationResult;
import com.github.auties00.cobalt.model.sync.SyncPatchType;
import com.github.auties00.cobalt.model.sync.action.chat.ChatAssignmentAction;
import com.github.auties00.cobalt.model.sync.data.SyncdOperation;
import com.github.auties00.cobalt.sync.crypto.DecryptedMutation;

/**
 * Routes a business chat to a specific agent in response to {@code agentChatAssignment} sync mutations.
 *
 * @apiNote
 * Drives the Business inbox agent-routing surface where a single chat
 * is assigned to (or unassigned from) a named agent. When the
 * assignment changes on another device, the server replays it here
 * and Cobalt embedders observe the result through
 * {@link com.github.auties00.cobalt.store.WhatsAppStore#findChatAssignment(Jid)}.
 *
 * @implNote
 * This implementation collapses WA Web's per-batch
 * {@code bulkCreateOrMerge} / {@code bulkRemove} /
 * {@code processChatAssignments} pipeline into per-mutation read-modify-write
 * operations on the typed quintet keyed by chat JID. WA Web's
 * downstream side effects (system-message creation,
 * {@code triggerChatAssignmentNotification}, and
 * {@code checkOrphanChatAssignments}) are intentionally omitted
 * because Cobalt has no UI notification or system-message pipeline
 * here.
 */
@WhatsAppWebModule(moduleName = "WAWebChatAssignmentSync")
public final class ChatAssignmentHandler implements WebAppStateActionHandler {

    /**
     * Constructs the singleton chat-assignment handler.
     *
     * @apiNote
     * Instantiated once by the sync handler registry. Embedders do not
     * normally construct this directly.
     */
    @WhatsAppWebExport(moduleName = "WAWebChatAssignmentSync", exports = "default", adaptation = WhatsAppAdaptation.ADAPTED)
    public ChatAssignmentHandler() {

    }

    @Override
    @WhatsAppWebExport(moduleName = "WAWebChatAssignmentSync", exports = "getAction", adaptation = WhatsAppAdaptation.DIRECT)
    public String actionName() {
        return ChatAssignmentAction.ACTION_NAME;
    }

    @Override
    @WhatsAppWebExport(moduleName = "WAWebChatAssignmentSync", exports = "collectionName", adaptation = WhatsAppAdaptation.DIRECT)
    public SyncPatchType collectionName() {
        return ChatAssignmentAction.COLLECTION_NAME;
    }

    @Override
    @WhatsAppWebExport(moduleName = "WAWebChatAssignmentSync", exports = "getVersion", adaptation = WhatsAppAdaptation.DIRECT)
    public int version() {
        return ChatAssignmentAction.ACTION_VERSION;
    }

    /**
     * {@inheritDoc}
     *
     * @apiNote
     * Validates the JSON index {@code ["agentChatAssignment", chatJid]}
     * and the {@link ChatAssignmentAction#deviceAgentID()} payload,
     * verifies the agent exists in the store (when non-empty), then
     * either drops the existing assignment for the chat (when the
     * agent id is the empty string, mirroring WA Web's "unassign"
     * sentinel) or upserts a new
     * {@link com.github.auties00.cobalt.model.chat.ChatAssignment}.
     * The previous {@code opened} flag is preserved across the upsert
     * so reassignment does not reset the agent-opened indicator.
     * Returns {@link MutationApplicationResult#unsupported()} for
     * non-{@code SET} operations and orphan results when the agent or
     * chat is not in the store.
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebChatAssignmentSync", exports = "applyMutations", adaptation = WhatsAppAdaptation.ADAPTED)
    public MutationApplicationResult applyMutation(WhatsAppClient client, DecryptedMutation.Trusted mutation) {
        try {
            var indexArray = JSON.parseArray(mutation.index());
            if (indexArray.size() <= 1) {
                return SyncdIndexUtils.malformedActionIndex(collectionName().name(), actionName());
            }
            var chatJidString = indexArray.getString(1);
            if (chatJidString == null || chatJidString.isEmpty()) {
                return SyncdIndexUtils.malformedActionIndex(collectionName().name(), actionName());
            }

            if (mutation.operation() != SyncdOperation.SET) {
                return MutationApplicationResult.unsupported();
            }

            if (!(mutation.value().action().orElse(null) instanceof ChatAssignmentAction action)) {
                return SyncdIndexUtils.malformedActionValue(collectionName().name());
            }

            var agentId = action.deviceAgentID().orElse("");
            if (!agentId.isEmpty() && client.store().findAgentState(agentId).isEmpty()) {
                return MutationApplicationResult.orphan(agentId, "Agent");
            }

            var chatJid = Jid.of(chatJidString);
            var chat = client.store().findChatByJid(chatJid);
            if (chat.isEmpty()) {
                return MutationApplicationResult.orphan(chatJidString, "Chat");
            }

            var resolvedChatJid = chat.get().toJid();
            if (agentId.isEmpty()) {
                client.store().removeChatAssignment(resolvedChatJid);
            } else {
                var existing = client.store().findChatAssignment(resolvedChatJid).orElse(null);
                client.store().putChatAssignment(new ChatAssignmentBuilder()
                        .chatJid(resolvedChatJid)
                        .agentId(agentId)
                        .opened(existing != null && existing.opened())
                        .build());
            }
            return MutationApplicationResult.success();
        } catch (Exception e) {
            return MutationApplicationResult.failed();
        }
    }

}
