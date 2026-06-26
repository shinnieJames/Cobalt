package com.github.auties00.cobalt.sync.handler;

import com.alibaba.fastjson2.JSON;
import com.github.auties00.cobalt.client.linked.LinkedWhatsAppClient;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.chat.ChatAssignmentBuilder;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.sync.mutation.MutationApplicationResult;
import com.github.auties00.cobalt.model.sync.SyncPatchType;
import com.github.auties00.cobalt.model.sync.action.chat.ChatAssignmentOpenedStatusAction;
import com.github.auties00.cobalt.model.sync.data.SyncdOperation;
import com.github.auties00.cobalt.store.linked.LinkedWhatsAppBusinessStore;
import com.github.auties00.cobalt.sync.crypto.DecryptedMutation;

/**
 * Tracks whether the assigned agent has opened the assigned chat from {@code agentChatAssignmentOpenedStatus} sync mutations.
 *
 * <p>This handler drives the Business inbox indicator that shows whether the
 * agent currently assigned to a chat has opened it. When the agent opens the
 * chat on another device, the server replays the resulting
 * {@link ChatAssignmentOpenedStatusAction} here, and the result becomes
 * observable via the {@code opened} flag on
 * {@link com.github.auties00.cobalt.model.chat.ChatAssignment} as surfaced by
 * {@link LinkedWhatsAppBusinessStore#findChatAssignment(Jid)}.
 *
 * @implNote
 * This implementation reuses the same
 * {@link com.github.auties00.cobalt.model.chat.ChatAssignment}
 * record managed by {@link ChatAssignmentHandler} rather than the
 * separate {@code ChatAssignmentCollection} entry WA Web tracks; the
 * upsert preserves the existing {@code agentId} and only mutates the
 * {@code opened} flag. WA Web's batched
 * {@code updateLocalOpenedState} accumulator is collapsed into
 * per-mutation read-modify-write here.
 */
@WhatsAppWebModule(moduleName = "WAWebChatAssignmentOpenedStatusSync")
public final class ChatAssignmentOpenedStatusHandler implements WebAppStateActionHandler {

    /**
     * Constructs the singleton chat-assignment-opened-status handler.
     *
     * <p>The sync handler registry instantiates this once during client
     * bootstrap.
     */
    @WhatsAppWebExport(moduleName = "WAWebChatAssignmentOpenedStatusSync", exports = "default", adaptation = WhatsAppAdaptation.ADAPTED)
    public ChatAssignmentOpenedStatusHandler() {

    }

    @Override
    @WhatsAppWebExport(moduleName = "WAWebChatAssignmentOpenedStatusSync", exports = "getAction", adaptation = WhatsAppAdaptation.DIRECT)
    public String actionName() {
        return ChatAssignmentOpenedStatusAction.ACTION_NAME;
    }

    @Override
    @WhatsAppWebExport(moduleName = "WAWebChatAssignmentOpenedStatusSync", exports = "collectionName", adaptation = WhatsAppAdaptation.DIRECT)
    public SyncPatchType collectionName() {
        return ChatAssignmentOpenedStatusAction.COLLECTION_NAME;
    }

    @Override
    @WhatsAppWebExport(moduleName = "WAWebChatAssignmentOpenedStatusSync", exports = "getVersion", adaptation = WhatsAppAdaptation.DIRECT)
    public int version() {
        return ChatAssignmentOpenedStatusAction.ACTION_VERSION;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Validates the JSON index
     * {@code ["agentChatAssignmentOpenedStatus", chatJid, agentId]}, resolves
     * the chat by JID, confirms the existing assignment matches the same
     * agent, then upserts the
     * {@link com.github.auties00.cobalt.model.chat.ChatAssignment} with the new
     * {@code opened} flag. Returns
     * {@link MutationApplicationResult#unsupported()} for non-{@code SET}
     * operations and orphan results when the chat or its existing assignment is
     * not in the store.
     *
     * @implNote
     * This implementation reads {@link ChatAssignmentOpenedStatusAction#chatOpened()}
     * which coalesces a missing wire field to {@code false}; WA Web
     * treats a missing {@code chatOpened} as malformed
     * ({@link SyncdIndexUtils#malformedActionValue(String)}). The
     * Cobalt model accessor is lossy on the boolean wire field so the
     * malformed branch is unreachable here. The orphan key
     * {@code "<chatJid>_<agentId>"} mirrors WA Web's
     * {@code ChatAssignmentCollection} composite id.
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebChatAssignmentOpenedStatusSync", exports = "applyMutations", adaptation = WhatsAppAdaptation.ADAPTED)
    public MutationApplicationResult applyMutation(LinkedWhatsAppClient client, DecryptedMutation.Trusted mutation) {
        try {
            var indexArray = JSON.parseArray(mutation.index());
            if (indexArray.size() <= 2) {
                return SyncdIndexUtils.malformedActionIndex(collectionName().name(), actionName());
            }
            var chatJidString = indexArray.getString(1);
            var agentId = indexArray.getString(2);
            if (chatJidString == null || agentId == null) {
                return SyncdIndexUtils.malformedActionIndex(collectionName().name(), actionName());
            }

            if (mutation.operation() != SyncdOperation.SET) {
                return MutationApplicationResult.unsupported();
            }

            var chatJid = Jid.of(chatJidString);
            var chat = client.store().chatStore().findChatByJid(chatJid);
            if (chat.isEmpty()) {
                return MutationApplicationResult.orphan(chatJidString, "Chat");
            }

            if (!(mutation.value().flatMap(sav -> sav.action()).orElse(null) instanceof ChatAssignmentOpenedStatusAction action)) {
                return SyncdIndexUtils.malformedActionValue(collectionName().name());
            }

            var resolvedChatJid = chat.get().toJid();
            var existing = client.store().businessStore().findChatAssignment(resolvedChatJid).orElse(null);
            if (existing == null || !agentId.equals(existing.agentId().orElse(null))) {
                return MutationApplicationResult.orphan(resolvedChatJid + "_" + agentId, "ChatAssignment");
            }

            var chatOpened = action.chatOpened();
            client.store().businessStore().putChatAssignment(new ChatAssignmentBuilder()
                    .chatJid(resolvedChatJid)
                    .agentId(existing.agentId().orElse(null))
                    .opened(chatOpened)
                    .build());
            return MutationApplicationResult.success();
        } catch (Exception e) {
            return MutationApplicationResult.failed();
        }
    }

}
