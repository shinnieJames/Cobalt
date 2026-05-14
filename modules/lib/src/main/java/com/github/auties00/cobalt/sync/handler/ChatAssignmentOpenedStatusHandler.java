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
import com.github.auties00.cobalt.model.sync.action.chat.ChatAssignmentOpenedStatusAction;
import com.github.auties00.cobalt.model.sync.data.SyncdOperation;
import com.github.auties00.cobalt.sync.crypto.DecryptedMutation;

/**
 * Handles chat assignment opened status sync actions.
 *
 * <p>This handler processes mutations that track whether an assigned chat
 * has been opened by the agent. It corresponds to the
 * {@code "agentChatAssignmentOpenedStatus"} action in the {@code Regular}
 * collection.
 *
 * <p>Index format: {@code ["agentChatAssignmentOpenedStatus", "chatJid", "agentId"]}
 *
 * <p>Per WhatsApp Web, this handler extends {@code ChatSyncdActionBase}
 * with {@code chatJidIndex = 1} and collection {@code Regular}.
 */
@WhatsAppWebModule(moduleName = "WAWebChatAssignmentOpenedStatusSync")
public final class ChatAssignmentOpenedStatusHandler implements WebAppStateActionHandler {

    /**
     * Creates a new {@code ChatAssignmentOpenedStatusHandler}.
     */
    @WhatsAppWebExport(moduleName = "WAWebChatAssignmentOpenedStatusSync", exports = "default", adaptation = WhatsAppAdaptation.ADAPTED)
    public ChatAssignmentOpenedStatusHandler() {

    }

    /**
     * Returns the action name for chat assignment opened status sync.
     * @return the action name string
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebChatAssignmentOpenedStatusSync", exports = "getAction", adaptation = WhatsAppAdaptation.DIRECT)
    public String actionName() {
        return ChatAssignmentOpenedStatusAction.ACTION_NAME;
    }

    /**
     * Returns the sync collection for this handler.
     * @return the {@link SyncPatchType#REGULAR} collection
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebChatAssignmentOpenedStatusSync", exports = "collectionName", adaptation = WhatsAppAdaptation.DIRECT)
    public SyncPatchType collectionName() {
        return ChatAssignmentOpenedStatusAction.COLLECTION_NAME;
    }

    /**
     * Returns the mutation format version for chat assignment opened status.
     * @return the version number
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebChatAssignmentOpenedStatusSync", exports = "getVersion", adaptation = WhatsAppAdaptation.DIRECT)
    public int version() {
        return ChatAssignmentOpenedStatusAction.ACTION_VERSION;
    }

    /**
     * Applies a single chat assignment opened status mutation and returns a detailed result.
     *
     * <p>Per WhatsApp Web {@code applyMutations}: for each mutation in the batch,
     * extracts {@code indexParts[1]} as the chat JID and {@code indexParts[2]} as
     * the agent ID. If either is absent, returns malformed. For {@code SET} operations,
     * resolves the chat, extracts the {@code chatAssignmentOpenedStatus.chatOpened}
     * value, verifies the assignment exists in the ChatAssignment collection, and
     * accumulates the update. Non-SET operations return unsupported.
     *
     * <p>After all mutations are processed, WA Web calls
     * {@code WAWebBizChatAssignmentOpenedAction.updateLocalOpenedState(accumulator)}
     * which updates the ChatAssignment collection models and bulk-merges to IDB.
     * In Cobalt, the store update is applied inline per mutation via the
     * {@code chatAssignmentOpenedStates} map.
     * @param client   the WhatsApp client instance
     * @param mutation the mutation to apply
     * @return the detailed application result
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebChatAssignmentOpenedStatusSync", exports = "applyMutations", adaptation = WhatsAppAdaptation.ADAPTED)
    public MutationApplicationResult applyMutation(WhatsAppClient client, DecryptedMutation.Trusted mutation) {
        try {
            var indexArray = JSON.parseArray(mutation.index());
            // WAWebChatAssignmentOpenedStatusSync.applyMutations: var t=e.indexParts, n=t[1], i=t[2]; if(n==null||i==null) return r.malformedActionIndex().
            // Slots 1 and 2 are read unconditionally; mirror the undefined-checks with an explicit size guard.
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
            var chat = client.store().findChatByJid(chatJid); // ADAPTED: WAWebChatAssignmentOpenedStatusSync.applyMutations: resolveChatForMutationIndex(createWid(n))
            if (chat.isEmpty()) {
                return MutationApplicationResult.orphan(chatJidString, "Chat");
            }

            if (!(mutation.value().action().orElse(null) instanceof ChatAssignmentOpenedStatusAction action)) {
                return SyncdIndexUtils.malformedActionValue(collectionName().name());
            }

            var resolvedChatJid = chat.get().toJid();
            var existing = client.store().findChatAssignment(resolvedChatJid).orElse(null); // ADAPTED: WAWebChatAssignmentOpenedStatusSync.applyMutations: var m = ChatAssignmentCollection.get(d); if (m == null)
            if (existing == null || !agentId.equals(existing.agentId().orElse(null))) {
                return MutationApplicationResult.orphan(resolvedChatJid + "_" + agentId, "ChatAssignment");
            }

            var chatOpened = action.chatOpened(); // ADAPTED: WAWebChatAssignmentOpenedStatusSync.applyMutations: var c = u.chatOpened; if (c == null) return malformedActionValue, Cobalt coalesces null to false per project convention
            client.store().putChatAssignment(new ChatAssignmentBuilder() // ADAPTED: WAWebChatAssignmentOpenedStatusSync.applyMutations: updateLocalOpenedState — Cobalt updates the unified ChatAssignment record's opened flag
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
