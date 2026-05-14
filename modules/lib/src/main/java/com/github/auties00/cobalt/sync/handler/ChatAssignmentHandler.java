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
 * Handles chat assignment sync actions.
 *
 * <p>This handler processes mutations that assign chats to agents
 * in business accounts. It corresponds to the {@code "agentChatAssignment"}
 * action in the {@code Regular} collection.
 *
 * <p>Per WhatsApp Web, this handler extends {@code ChatSyncdActionBase}
 * with {@code chatJidIndex = 1} and collection {@code Regular}.
 *
 * <p>Index format: {@code ["agentChatAssignment", "chatJid"]}
 */
@WhatsAppWebModule(moduleName = "WAWebChatAssignmentSync")
public final class ChatAssignmentHandler implements WebAppStateActionHandler {

    /**
     * Creates a new {@code ChatAssignmentHandler}.
     */
    @WhatsAppWebExport(moduleName = "WAWebChatAssignmentSync", exports = "default", adaptation = WhatsAppAdaptation.ADAPTED)
    public ChatAssignmentHandler() {

    }

    /**
     * Returns the action name for chat assignment sync.
     * @return the action name string
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebChatAssignmentSync", exports = "getAction", adaptation = WhatsAppAdaptation.DIRECT)
    public String actionName() {
        return ChatAssignmentAction.ACTION_NAME;
    }

    /**
     * Returns the sync collection for this handler.
     * @return the {@link SyncPatchType#REGULAR} collection
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebChatAssignmentSync", exports = "collectionName", adaptation = WhatsAppAdaptation.DIRECT)
    public SyncPatchType collectionName() {
        return ChatAssignmentAction.COLLECTION_NAME;
    }

    /**
     * Returns the mutation format version for chat assignment.
     * @return the version number
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebChatAssignmentSync", exports = "getVersion", adaptation = WhatsAppAdaptation.DIRECT)
    public int version() {
        return ChatAssignmentAction.ACTION_VERSION;
    }

    /**
     * Applies a single chat assignment mutation and returns a detailed result.
     *
     * <p>Per WhatsApp Web {@code applyMutations}: for each mutation in the batch,
     * extracts {@code indexParts[1]} as the chat JID. If absent, returns malformed.
     * For {@code SET} operations, extracts the {@code chatAssignment.deviceAgentID},
     * verifies the agent exists in the agent collection (when non-empty), resolves
     * the chat, removes existing assignments for that chat, and adds the new
     * assignment. Non-SET operations return unsupported.
     *
     * <p>After all mutations are processed, WA Web calls batch store operations
     * ({@code bulkCreateOrMerge}, {@code bulkRemove}), collection updates
     * ({@code processChatAssignments}, {@code remove}), system message creation,
     * notification triggering, and orphan checking. In Cobalt, the store update
     * is applied inline per mutation via the {@code chatAssignmentStates} map.
     * @param client   the WhatsApp client instance
     * @param mutation the mutation to apply
     * @return the detailed application result
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebChatAssignmentSync", exports = "applyMutations", adaptation = WhatsAppAdaptation.ADAPTED)
    public MutationApplicationResult applyMutation(WhatsAppClient client, DecryptedMutation.Trusted mutation) {
        try {
            var indexArray = JSON.parseArray(mutation.index());
            // WAWebChatAssignmentSync.applyMutations: var t=e.indexParts, n=t[1]; if(!n) return a.malformedActionIndex().
            // The slot-missing case must yield MALFORMED, not FAILED via the outer catch.
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
            var chat = client.store().findChatByJid(chatJid); // ADAPTED: WAWebChatAssignmentSync.applyMutations: resolveChatForMutationIndex, Cobalt uses findChatByJid
            if (chat.isEmpty()) {
                return MutationApplicationResult.orphan(chatJidString, "Chat");
            }

            var resolvedChatJid = chat.get().toJid();
            // ADAPTED: WAWebChatAssignmentSync.applyMutations, batch bulkCreateOrMerge/bulkRemove/processChatAssignments simplified to a typed-quintet read-modify-write keyed by chat JID
            if (agentId.isEmpty()) {
                client.store().removeChatAssignment(resolvedChatJid); // ADAPTED: WAWebChatAssignmentSync.applyMutations: getAgentCollectionForChatId(_).filter(e => e.id !== d).forEach(e => l.push(...)) + bulkRemove(l), removes all existing assignments for this chat
            } else {
                var existing = client.store().findChatAssignment(resolvedChatJid).orElse(null);
                client.store().putChatAssignment(new ChatAssignmentBuilder() // ADAPTED: WAWebChatAssignmentSync.applyMutations: i.push({id: _.toJid()+"_"+d, chatId: _.toJid(), agentId: d, chatOpenedByAgent: false}) + bulkCreateOrMerge(i)
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
