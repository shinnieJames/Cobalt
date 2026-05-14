package com.github.auties00.cobalt.sync.handler;

import com.alibaba.fastjson2.JSON;
import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.sync.ConflictResolution;
import com.github.auties00.cobalt.model.sync.ConflictResolutionState;
import com.github.auties00.cobalt.model.sync.MutationApplicationResult;
import com.github.auties00.cobalt.model.sync.SyncActionValueBuilder;
import com.github.auties00.cobalt.model.sync.SyncPatchType;
import com.github.auties00.cobalt.model.sync.action.chat.DeleteChatAction;
import com.github.auties00.cobalt.model.sync.action.chat.DeleteChatActionBuilder;
import com.github.auties00.cobalt.model.sync.data.SyncdOperation;
import com.github.auties00.cobalt.sync.crypto.DecryptedMutation;

/**
 * Handles delete chat sync actions.
 *
 * <p>This handler processes mutations that delete entire chats.
 * Per WhatsApp Web, the delete chat handler extends
 * {@code ChatMessageRangeSyncdActionBase} and uses message-range-based
 * conflict resolution.
 *
 * <p>Index format: {@code ["deleteChat", chatJid, deleteMedia]}
 * where {@code deleteMedia} is {@code "0"} (delete media) or {@code "1"}
 * (keep media).
 */
@WhatsAppWebModule(moduleName = "WAWebDeleteChatSync")
public final class DeleteChatHandler implements WebAppStateActionHandler {

    /**
     * Private constructor to enforce singleton pattern.
     */
    @WhatsAppWebExport(moduleName = "WAWebDeleteChatSync", exports = "default", adaptation = WhatsAppAdaptation.ADAPTED)
    public DeleteChatHandler() {

    }

    /**
     * Returns the action name for delete chat actions.
     * @return the action name {@code "deleteChat"}
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebDeleteChatSync", exports = "getAction", adaptation = WhatsAppAdaptation.DIRECT)
    public String actionName() {
        return DeleteChatAction.ACTION_NAME;
    }

    /**
     * Returns the sync collection for delete chat actions.
     *
     * <p>Per WhatsApp Web, the delete chat handler's {@code collectionName} is set to
     * {@code WASyncdConst.CollectionName.RegularHigh} in the constructor.
     * @return {@link SyncPatchType#REGULAR_HIGH}
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebDeleteChatSync", exports = "collectionName", adaptation = WhatsAppAdaptation.DIRECT)
    public SyncPatchType collectionName() {
        return DeleteChatAction.COLLECTION_NAME;
    }

    /**
     * Returns the mutation format version for delete chat actions.
     * @return the version number {@code 6}
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebDeleteChatSync", exports = "getVersion", adaptation = WhatsAppAdaptation.DIRECT)
    public int version() {
        return DeleteChatAction.ACTION_VERSION;
    }

    /**
     * Applies a delete chat mutation and returns a detailed result.
     *
     * <p>Per WhatsApp Web {@code WAWebDeleteChatSync.applyMutations}, for each
     * mutation with {@code operation === "set"}:
     * <ol>
     *   <li>Extracts the index parts: {@code [action, chatJid, deleteMedia]}</li>
     *   <li>Validates all index parts exist and the chat JID is valid via
     *       {@code WAWebWid.isWid}</li>
     *   <li>Validates the message range via
     *       {@code WAWebMessageRangeUtils.validateMessageRange}</li>
     *   <li>Resolves the chat via
     *       {@code WAWebSyncdGetChat.resolveChatForMutationIndex}</li>
     *   <li>Replaces remote JIDs in the message range and compares ranges
     *       via {@code $DeleteChatSync$p_1}</li>
     *   <li>Based on the range comparison, either deletes all messages
     *       (full delete) or deletes messages within the range (partial delete)</li>
     * </ol>
     *
     * <p>Non-{@code SET} operations return {@code Unsupported}. Exceptions are
     * caught and return {@code Failed}.
     * @param client   the WhatsApp client instance
     * @param mutation the mutation to apply
     * @return the detailed application result
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebDeleteChatSync", exports = {"applyMutations", "getMessageRange", "$DeleteChatSync$p_1", "deleteChat"}, adaptation = WhatsAppAdaptation.ADAPTED)
    public MutationApplicationResult applyMutation(WhatsAppClient client, DecryptedMutation.Trusted mutation) {
        if (mutation.operation() != SyncdOperation.SET) {
            return MutationApplicationResult.unsupported();
        }

        try {
            var indexParts = JSON.parseArray(mutation.index());
            var chatJidString = indexParts.getString(1);
            var deleteMediaString = indexParts.getString(2);

            if (chatJidString == null || chatJidString.isEmpty()
                    || deleteMediaString == null || deleteMediaString.isEmpty()) {
                return SyncdIndexUtils.malformedActionIndex(collectionName().name(), actionName());
            }

            Jid chatJid;
            try {
                chatJid = Jid.of(chatJidString);
            } catch (Exception e) {
                return SyncdIndexUtils.malformedActionIndex(collectionName().name(), actionName()); // ADAPTED: Jid.of throws for invalid JIDs; WA Web uses isWid() upfront
            }

            if (chatJid == null) { // ADAPTED: Jid.of returns null for null input
                return SyncdIndexUtils.malformedActionIndex(collectionName().name(), actionName());
            }

            if (!(mutation.value().action().orElse(null) instanceof DeleteChatAction deleteChatAction)) {
                return SyncdIndexUtils.malformedActionValue(collectionName().name());
            }

            var messageRange = deleteChatAction.messageRange().orElse(null);
            if (messageRange == null) {
                return SyncdIndexUtils.malformedActionValue(collectionName().name());
            }

            var chat = client.store().findChatByJid(chatJid);
            if (chat.isEmpty()) {
                return MutationApplicationResult.orphan(chatJidString, "Chat");
            }

            //   encodes the SyncActionValue, adds active message range,
            //   constructs local message range, compares with incoming range:
            //   - RangeAEnclosesRangeB or RangesNotEnclosing: deleteChat(wid, messageRange) (partial delete)
            //   - RangeBEnclosesRangeA or RangesAreEqual: deleteChat(wid) (full delete)
            //
            //   - With messageRange: queryAndRemoveMessagesInMessageRange (partial)
            //   - Without messageRange: deleteFromStorage (full delete)
            //
            // ADAPTED: WAWebDeleteChatSync.$DeleteChatSync$p_1 performs message-range comparison
            // against the local chat's current message range to decide between partial and full delete.
            // Cobalt simplifies to a full chat removal because:
            // 1. addActiveMessageRange is an IndexedDB-specific optimization (browser concern)
            // 2. constructMessageRange requires querying per-message timestamps from storage
            // 3. Partial message deletion within a range is not yet supported by the Chat abstraction
            // 4. Thread deletion, add-on cleanup, and AI thread deletion are browser-specific UI concerns
            // 5. deleteChatFromInitialSyncBoundary is a history sync boundary concern
            client.store().removeChat(chat.get());

            return MutationApplicationResult.success();
        } catch (Exception e) {
            return MutationApplicationResult.failed();
        }
    }

    /**
     * Resolves conflicts between a local pending delete chat mutation and an
     * incoming remote delete chat mutation using message range comparison.
     *
     * <p>Per WhatsApp Web {@code WAWebDeleteChatSync.resolveConflicts}:
     * <ol>
     *   <li>Decodes the local and remote {@code deleteChatAction} values</li>
     *   <li>Compares their message ranges via
     *       {@code WAWebMessageRangeUtils.compareMessageRanges(remote, local)}</li>
     *   <li>Resolves based on the enclosure type:
     *     <ul>
     *       <li>{@code RangeAEnclosesRangeB} (remote encloses local): apply remote, drop local</li>
     *       <li>{@code RangeBEnclosesRangeA} (local encloses remote): skip remote</li>
     *       <li>{@code RangesAreEqual}: timestamp tiebreaker ({@code local <= remote}
     *           means apply remote)</li>
     *       <li>{@code RangesNotEnclosing}: merge the two ranges, apply the merged mutation
     *           to local state via {@code lockForMessageRangeSync}, and return
     *           {@code SKIP_REMOTE_DROP_LOCAL} with the merged mutation</li>
     *     </ul>
     *   </li>
     * </ol>
     * @param localMutation  the local pending mutation
     * @param remoteMutation the incoming remote mutation
     * @return the conflict resolution indicating which mutation to keep
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebDeleteChatSync", exports = "resolveConflicts", adaptation = WhatsAppAdaptation.ADAPTED)
    public ConflictResolution resolveConflicts(DecryptedMutation.Trusted localMutation, DecryptedMutation.Trusted remoteMutation) {
        var localAction = localMutation.value().action()
                .filter(a -> a instanceof DeleteChatAction)
                .map(a -> (DeleteChatAction) a)
                .orElse(null);
        var remoteAction = remoteMutation.value().action()
                .filter(a -> a instanceof DeleteChatAction)
                .map(a -> (DeleteChatAction) a)
                .orElse(null);

        if (localAction == null || remoteAction == null) { // ADAPTED: WA Web uses nullthrows which would throw; Cobalt gracefully falls back to apply remote
            return ConflictResolution.of(ConflictResolutionState.APPLY_REMOTE_DROP_LOCAL); // ADAPTED: defensive fallback
        }

        var localRange = localAction.messageRange().orElse(null);
        var remoteRange = remoteAction.messageRange().orElse(null);

        if (localRange == null || remoteRange == null) { // ADAPTED: WA Web uses nullthrows; Cobalt gracefully falls back
            return ConflictResolution.of(ConflictResolutionState.APPLY_REMOTE_DROP_LOCAL); // ADAPTED: defensive fallback
        }

        return switch (MessageRangeUtils.compareMessageRanges(remoteRange, localRange)) {
            case RANGE_A_ENCLOSES_RANGE_B ->
                    ConflictResolution.of(ConflictResolutionState.APPLY_REMOTE_DROP_LOCAL);
            case RANGE_B_ENCLOSES_RANGE_A ->
                    ConflictResolution.of(ConflictResolutionState.SKIP_REMOTE);
            case RANGES_ARE_EQUAL ->
                    localMutation.timestamp().compareTo(remoteMutation.timestamp()) <= 0
                            ? ConflictResolution.of(ConflictResolutionState.APPLY_REMOTE_DROP_LOCAL)
                            : ConflictResolution.of(ConflictResolutionState.SKIP_REMOTE);
            case RANGES_NOT_ENCLOSING -> {
                var mergedRange = MessageRangeUtils.mergeMessageRanges(remoteRange, localRange);
                var mergedAction = new DeleteChatActionBuilder()
                        .messageRange(mergedRange)
                        .build();
                var mergedValue = new SyncActionValueBuilder()
                        .timestamp(remoteMutation.timestamp())
                        .deleteChatAction(mergedAction)
                        .build();
                var merged = new DecryptedMutation.Trusted(
                        localMutation.index(),
                        mergedValue,
                        localMutation.operation(),
                        localMutation.timestamp(),
                        localMutation.actionVersion()
                );
                // ADAPTED: In WA Web, the merged mutation is applied to the chat DB immediately
                // during conflict resolution via lockForMessageRangeSync. In Cobalt, the merged
                // mutation is returned for the caller to apply, separating resolution from application.
                yield ConflictResolution.merged(merged);
            }
        };
    }

}
