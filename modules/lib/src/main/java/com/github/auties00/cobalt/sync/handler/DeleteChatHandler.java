package com.github.auties00.cobalt.sync.handler;

import com.alibaba.fastjson2.JSON;
import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.sync.ConflictResolution;
import com.github.auties00.cobalt.model.sync.ConflictResolutionState;
import com.github.auties00.cobalt.model.sync.MutationApplicationResult;
import com.github.auties00.cobalt.model.sync.SyncActionMessageRange;
import com.github.auties00.cobalt.model.sync.SyncActionState;
import com.github.auties00.cobalt.model.sync.SyncActionValueBuilder;
import com.github.auties00.cobalt.model.sync.SyncPatchType;
import com.github.auties00.cobalt.sync.SyncPendingMutation;
import com.github.auties00.cobalt.model.sync.action.chat.DeleteChatAction;
import com.github.auties00.cobalt.model.sync.action.chat.DeleteChatActionBuilder;
import com.github.auties00.cobalt.model.sync.data.SyncdOperation;
import com.github.auties00.cobalt.sync.crypto.DecryptedMutation;

import java.time.Instant;
import java.util.List;

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
 *
 * @implNote WAWebDeleteChatSync — singleton instance exported as {@code default};
 *           extends {@code ChatMessageRangeSyncdActionBase} with
 *           {@code collectionName = RegularHigh}, {@code chatJidIndex = 1},
 *           {@code getVersion() = 6}, {@code getAction() = "deleteChat"}
 */
public final class DeleteChatHandler implements WebAppStateActionHandler {

    /**
     * Singleton instance of the delete chat handler.
     *
     * <p>Per WhatsApp Web, {@code WAWebDeleteChatSync} exports a single instance
     * ({@code var f = new _(); l.default = f}).
     *
     * @implNote WAWebDeleteChatSync.default — module-level singleton
     */
    public static final DeleteChatHandler INSTANCE = new DeleteChatHandler();

    /**
     * Private constructor to enforce singleton pattern.
     *
     * @implNote WAWebDeleteChatSync — class constructor sets
     *           {@code collectionName = RegularHigh}, {@code chatJidIndex = 1}
     */
    private DeleteChatHandler() {

    }

    /**
     * Returns the action name for delete chat actions.
     *
     * @implNote WAWebDeleteChatSync.getAction — returns
     *           {@code WASyncdConst.Actions.DeleteChat} ({@code "deleteChat"})
     * @return the action name {@code "deleteChat"}
     */
    @Override
    public String actionName() {
        return DeleteChatAction.ACTION_NAME; // WAWebDeleteChatSync.getAction -> WASyncdConst.Actions.DeleteChat
    }

    /**
     * Returns the sync collection for delete chat actions.
     *
     * <p>Per WhatsApp Web, the delete chat handler's {@code collectionName} is set to
     * {@code WASyncdConst.CollectionName.RegularHigh} in the constructor.
     *
     * @implNote WAWebDeleteChatSync.collectionName — set in constructor to
     *           {@code WASyncdConst.CollectionName.RegularHigh}
     * @return {@link SyncPatchType#REGULAR_HIGH}
     */
    @Override
    public SyncPatchType collectionName() {
        return DeleteChatAction.COLLECTION_NAME; // WAWebDeleteChatSync.collectionName = WASyncdConst.CollectionName.RegularHigh
    }

    /**
     * Returns the mutation format version for delete chat actions.
     *
     * @implNote WAWebDeleteChatSync.getVersion — returns {@code 6}
     * @return the version number {@code 6}
     */
    @Override
    public int version() {
        return DeleteChatAction.ACTION_VERSION; // WAWebDeleteChatSync.getVersion -> 6
    }

    /**
     * Applies a delete chat mutation to local state.
     *
     * <p>Delegates to {@link #applyMutationResult(WhatsAppClient, DecryptedMutation.Trusted)}
     * and returns {@code true} if the result is {@link SyncActionState#SUCCESS}.
     *
     * @implNote WAWebDeleteChatSync.applyMutations — per-mutation inner logic,
     *           success check on the returned {@code SyncActionState}
     * @param client   the WhatsApp client instance
     * @param mutation the mutation to apply
     * @return {@code true} if the mutation was applied successfully
     */
    @Override
    public boolean applyMutation(WhatsAppClient client, DecryptedMutation.Trusted mutation) {
        return applyMutationResult(client, mutation).actionState() == SyncActionState.SUCCESS; // WAWebDeleteChatSync.applyMutations
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
     *
     * @implNote WAWebDeleteChatSync.applyMutations — per-mutation inner function
     * @param client   the WhatsApp client instance
     * @param mutation the mutation to apply
     * @return the detailed application result
     */
    @Override
    public MutationApplicationResult applyMutationResult(WhatsAppClient client, DecryptedMutation.Trusted mutation) {
        if (mutation.operation() != SyncdOperation.SET) { // WAWebDeleteChatSync.applyMutations: e.operation === "set" check, else return Unsupported
            return MutationApplicationResult.unsupported(); // WAWebDeleteChatSync.applyMutations: l++, {actionState: Unsupported}
        }

        try { // WAWebDeleteChatSync.applyMutations: try/catch wrapping per-mutation logic
            var indexParts = JSON.parseArray(mutation.index()); // WAWebDeleteChatSync.applyMutations: var t = e.indexParts
            var chatJidString = indexParts.getString(1); // WAWebDeleteChatSync.applyMutations: var s = t[1]
            var deleteMediaString = indexParts.getString(2); // WAWebDeleteChatSync.applyMutations: var u = t[2]

            if (chatJidString == null || chatJidString.isEmpty() // WAWebDeleteChatSync.applyMutations: if (!s || !u || !isWid(s))
                    || deleteMediaString == null || deleteMediaString.isEmpty()) {
                return malformedActionIndex(); // WAWebDeleteChatSync.applyMutations: return a.malformedActionIndex()
            }

            Jid chatJid; // WAWebDeleteChatSync.applyMutations: r("WAWebWid").isWid(s)
            try {
                chatJid = Jid.of(chatJidString); // WAWebDeleteChatSync.applyMutations: o("WAWebWidFactory").createWid(s)
            } catch (Exception e) {
                return malformedActionIndex(); // ADAPTED: Jid.of throws for invalid JIDs; WA Web uses isWid() upfront
            }

            if (chatJid == null) { // ADAPTED: Jid.of returns null for null input
                return malformedActionIndex(); // WAWebDeleteChatSync.applyMutations: !isWid(s)
            }

            // WAWebDeleteChatSync.applyMutations: var n = e.value
            // WAWebDeleteChatSync.getMessageRange: return value.deleteChatAction?.messageRange
            if (!(mutation.value().action().orElse(null) instanceof DeleteChatAction deleteChatAction)) { // WAWebDeleteChatSync.getMessageRange
                return malformedActionValue(); // WAWebDeleteChatSync.applyMutations: malformedActionValue when messageRange null
            }

            // WAWebDeleteChatSync.applyMutations: var c = validateMessageRange(getMessageRange(n), collectionName, getAction())
            var messageRange = deleteChatAction.messageRange().orElse(null); // WAWebDeleteChatSync.getMessageRange
            if (messageRange == null) { // WAWebDeleteChatSync.applyMutations: if (c == null) return i++, malformedActionValue(collectionName)
                return malformedActionValue(); // WAWebDeleteChatSync.applyMutations: malformedActionValue(a.collectionName)
            }

            // WAWebDeleteChatSync.applyMutations: var d = yield resolveChatForMutationIndex(createWid(s))
            var chat = client.store().findChatByJid(chatJid); // WAWebDeleteChatSync.applyMutations: resolveChatForMutationIndex
            if (chat.isEmpty()) { // WAWebDeleteChatSync.applyMutations: if (!d.success) return {actionState: Orphan, orphanModel: d.orphanModel}
                return MutationApplicationResult.orphan(chatJidString, "Chat"); // WAWebDeleteChatSync.applyMutations: {actionState: Orphan, orphanModel: d.orphanModel}
            }

            // WAWebDeleteChatSync.applyMutations: var m = createWid(d.chat.id)
            // WAWebDeleteChatSync.applyMutations: var p = replaceMessageRangeRemoteJid(m, c)
            // WAWebDeleteChatSync.applyMutations: return a.$DeleteChatSync$p_1(m, p, u==="0", n)

            // WAWebDeleteChatSync.$DeleteChatSync$p_1:
            //   encodes the SyncActionValue, adds active message range,
            //   constructs local message range, compares with incoming range:
            //   - RangeAEnclosesRangeB or RangesNotEnclosing: deleteChat(wid, messageRange) (partial delete)
            //   - RangeBEnclosesRangeA or RangesAreEqual: deleteChat(wid) (full delete)
            //
            // WAWebDeleteChatSync.deleteChat:
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
            client.store().removeChat(chat.get()); // ADAPTED: WAWebDeleteChatSync.$DeleteChatSync$p_1 -> deleteChat

            return MutationApplicationResult.success(); // WAWebDeleteChatSync.$DeleteChatSync$p_1: {actionState: Success}
        } catch (Exception e) { // WAWebDeleteChatSync.applyMutations: catch(e) { return {actionState: Failed} }
            return MutationApplicationResult.failed(); // WAWebDeleteChatSync.applyMutations: {actionState: SyncActionState.Failed}
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
     *
     * @implNote WAWebDeleteChatSync.resolveConflicts
     * @param localMutation  the local pending mutation
     * @param remoteMutation the incoming remote mutation
     * @return the conflict resolution indicating which mutation to keep
     */
    @Override
    public ConflictResolution resolveConflicts(DecryptedMutation.Trusted localMutation, DecryptedMutation.Trusted remoteMutation) {
        var localAction = localMutation.value().action() // WAWebDeleteChatSync.resolveConflicts: var c = nullthrows(i.deleteChatAction) — i decoded from e.binarySyncAction
                .filter(a -> a instanceof DeleteChatAction)
                .map(a -> (DeleteChatAction) a)
                .orElse(null);
        var remoteAction = remoteMutation.value().action() // WAWebDeleteChatSync.resolveConflicts: var p = nullthrows(l?.deleteChatAction) — l decoded from t.binarySyncData
                .filter(a -> a instanceof DeleteChatAction)
                .map(a -> (DeleteChatAction) a)
                .orElse(null);

        if (localAction == null || remoteAction == null) { // ADAPTED: WA Web uses nullthrows which would throw; Cobalt gracefully falls back to apply remote
            return ConflictResolution.of(ConflictResolutionState.APPLY_REMOTE_DROP_LOCAL); // ADAPTED: defensive fallback
        }

        var localRange = localAction.messageRange().orElse(null); // WAWebDeleteChatSync.resolveConflicts: nullthrows(c.messageRange)
        var remoteRange = remoteAction.messageRange().orElse(null); // WAWebDeleteChatSync.resolveConflicts: nullthrows(p.messageRange)

        if (localRange == null || remoteRange == null) { // ADAPTED: WA Web uses nullthrows; Cobalt gracefully falls back
            return ConflictResolution.of(ConflictResolutionState.APPLY_REMOTE_DROP_LOCAL); // ADAPTED: defensive fallback
        }

        return switch (MessageRangeUtils.compareMessageRanges(remoteRange, localRange)) { // WAWebDeleteChatSync.resolveConflicts: compareMessageRanges(nullthrows(p.messageRange), nullthrows(c.messageRange))
            case RANGE_A_ENCLOSES_RANGE_B -> // WAWebDeleteChatSync.resolveConflicts: case RangeAEnclosesRangeB -> ApplyRemoteAndDropLocal
                    ConflictResolution.of(ConflictResolutionState.APPLY_REMOTE_DROP_LOCAL);
            case RANGE_B_ENCLOSES_RANGE_A -> // WAWebDeleteChatSync.resolveConflicts: case RangeBEnclosesRangeA -> SkipRemote
                    ConflictResolution.of(ConflictResolutionState.SKIP_REMOTE);
            case RANGES_ARE_EQUAL -> // WAWebDeleteChatSync.resolveConflicts: case RangesAreEqual -> timestamp tiebreaker
                    localMutation.timestamp().compareTo(remoteMutation.timestamp()) <= 0 // WAWebDeleteChatSync.resolveConflicts: s <= u (local <= remote)
                            ? ConflictResolution.of(ConflictResolutionState.APPLY_REMOTE_DROP_LOCAL) // WAWebDeleteChatSync.resolveConflicts: ApplyRemoteAndDropLocal
                            : ConflictResolution.of(ConflictResolutionState.SKIP_REMOTE); // WAWebDeleteChatSync.resolveConflicts: SkipRemote
            case RANGES_NOT_ENCLOSING -> { // WAWebDeleteChatSync.resolveConflicts: case RangesNotEnclosing
                var mergedRange = MessageRangeUtils.mergeMessageRanges(remoteRange, localRange); // WAWebDeleteChatSync.resolveConflicts: mergeMessageRanges(nullthrows(p.messageRange), nullthrows(c.messageRange))
                var mergedAction = new DeleteChatActionBuilder() // WAWebDeleteChatSync.resolveConflicts: var g = {messageRange: f}
                        .messageRange(mergedRange) // WAWebDeleteChatSync.resolveConflicts: messageRange: f
                        .build();
                var mergedValue = new SyncActionValueBuilder() // WAWebDeleteChatSync.resolveConflicts: extends({}, l, {deleteChatAction: g})
                        .timestamp(remoteMutation.timestamp()) // WAWebDeleteChatSync.resolveConflicts: timestamp from remote value (l)
                        .deleteChatAction(mergedAction) // WAWebDeleteChatSync.resolveConflicts: deleteChatAction: g
                        .build();
                var merged = new DecryptedMutation.Trusted( // WAWebDeleteChatSync.resolveConflicts: extends({}, e, {binarySyncAction: h}); delete y.id
                        localMutation.index(), // WAWebDeleteChatSync.resolveConflicts: from local (e.index)
                        mergedValue, // WAWebDeleteChatSync.resolveConflicts: merged binary value
                        localMutation.operation(), // WAWebDeleteChatSync.resolveConflicts: from local (e.operation)
                        localMutation.timestamp(), // WAWebDeleteChatSync.resolveConflicts: from local (e.timestamp)
                        localMutation.actionVersion() // WAWebDeleteChatSync.resolveConflicts: from local (e.version)
                );
                // WAWebDeleteChatSync.resolveConflicts: lockForMessageRangeSync -> addActiveMessageRange + deleteChat
                // ADAPTED: In WA Web, the merged mutation is applied to the chat DB immediately
                // during conflict resolution via lockForMessageRangeSync. In Cobalt, the merged
                // mutation is returned for the caller to apply, separating resolution from application.
                yield ConflictResolution.merged(merged); // WAWebDeleteChatSync.resolveConflicts: return SkipRemoteAndDropLocal
            }
        };
    }

    /**
     * Builds a pending mutation that deletes a chat.
     *
     * <p>Per WhatsApp Web {@code WAWebDeleteChatSync.getDeleteChatMutation}:
     * <pre>{@code
     * getDeleteChatMutation(timestamp, chatWid, deleteMediaFiles) {
     *   var indexJid = yield getChatJidMutationIndexForChat(chatWid, Actions.DeleteChat);
     *   var indexWid = createWid(indexJid);
     *   var forwardRange = yield constructForwardMovingMessageRange(chatWid, indexJid);
     *   var indexArgs = buildDeleteChatIndexArgs(indexWid, deleteMediaFiles);
     *   // merges with any existing pending DeleteChat mutation for the same index
     *   return buildDeleteChatMutation({timestamp, indexWid, mergedRange, deleteMediaFiles});
     * }
     * buildDeleteChatIndexArgs(t, n) { return [t.toJid(), n ? "1" : "0"] }
     * }</pre>
     *
     * <p>The index format is {@code ["deleteChat", chatJid, deleteMedia]} where
     * {@code deleteMedia} is written as {@code "1"} when {@code true} and
     * {@code "0"} when {@code false}, matching {@code buildDeleteChatIndexArgs}.
     *
     * <p>In Cobalt the caller supplies the message range because Cobalt does
     * not maintain the active-message-range infrastructure (browser-specific
     * IndexedDB concern). The WAM telemetry commit
     * ({@code MdSyncdDogfoodingFeatureUsageWamEvent}) is intentionally omitted.
     *
     * @implNote WAWebDeleteChatSync.getDeleteChatMutation,
     *           WAWebDeleteChatSync.buildDeleteChatMutation,
     *           WAWebDeleteChatSync.buildDeleteChatIndexArgs,
     *           WAWebSyncdActionUtils.buildPendingMutation
     * @param timestamp        the mutation timestamp
     * @param chatJid          the JID of the chat to delete
     * @param deleteMediaFiles whether media files should be deleted
     * @param messageRange     the message range covering the messages to
     *                         delete; may be {@code null} when the chat has
     *                         no messages and the caller wants a full delete
     * @return the pending mutation for the delete-chat action
     */
    public SyncPendingMutation getDeleteChatMutation(
            Instant timestamp,
            Jid chatJid,
            boolean deleteMediaFiles,
            SyncActionMessageRange messageRange
    ) {
        var actionBuilder = new DeleteChatActionBuilder(); // WAWebDeleteChatSync.buildDeleteChatMutation: value: {deleteChatAction: {messageRange: r}}
        if (messageRange != null) { // WAWebDeleteChatSync.buildDeleteChatMutation: messageRange: r (mergedRange)
            actionBuilder.messageRange(messageRange);
        }
        var action = actionBuilder.build();
        var value = new SyncActionValueBuilder() // WAWebSyncdActionUtils.buildPendingMutation: encodeProtobuf(SyncActionValueSpec, {...l, timestamp: i})
                .timestamp(timestamp) // WAWebSyncdActionUtils.buildPendingMutation: timestamp: e (encoder overlay)
                .deleteChatAction(action) // WAWebDeleteChatSync.buildDeleteChatMutation: {deleteChatAction: ...}
                .build();
        var index = JSON.toJSONString(List.of( // WAWebSyncdActionUtils.buildIndex: JSON.stringify([action].concat(indexArgs))
                actionName(), // WAWebDeleteChatSync.getAction: "deleteChat"
                chatJid.toString(), // WAWebDeleteChatSync.buildDeleteChatIndexArgs: t.toJid()
                deleteMediaFiles ? "1" : "0" // WAWebDeleteChatSync.buildDeleteChatIndexArgs: n ? "1" : "0"
        ));
        var mutation = new DecryptedMutation.Trusted( // WAWebSyncdActionUtils.buildPendingMutation: return {collection, index, binarySyncAction, version, operation, timestamp, action}
                index,
                value,
                SyncdOperation.SET, // WAWebDeleteChatSync.buildDeleteChatMutation: operation: SyncdMutation$SyncdOperation.SET
                timestamp,
                version() // WAWebDeleteChatSync.buildDeleteChatMutation: version: this.getVersion()
        );
        return new SyncPendingMutation(mutation, 0); // ADAPTED: WA Web returns the raw mutation object; Cobalt wraps it in SyncPendingMutation for the outgoing queue
    }
}
