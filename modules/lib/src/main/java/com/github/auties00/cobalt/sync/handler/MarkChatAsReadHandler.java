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
import com.github.auties00.cobalt.model.sync.action.chat.MarkChatAsReadAction;
import com.github.auties00.cobalt.model.sync.action.chat.MarkChatAsReadActionBuilder;
import com.github.auties00.cobalt.model.sync.data.SyncdOperation;
import com.github.auties00.cobalt.sync.crypto.DecryptedMutation;

/**
 * Handles mark chat as read sync actions.
 *
 * <p>This handler processes incoming mutations that mark a chat as read or
 * unread, resolves conflicts between local and remote mutations using message
 * range comparison, and builds outgoing mutations for user-initiated read-state
 * actions.
 *
 * <p>The action is identified by the {@code "markChatAsRead"} action name in
 * {@code SyncActionValue.markChatAsReadAction}. The mutation index format is
 * {@code ["markChatAsRead", chatJid]}.
 *
 * <p>Per WhatsApp Web, this handler extends {@code ChatMessageRangeSyncdActionBase},
 * which provides shared message-range-based conflict resolution. In Cobalt this
 * logic is inlined since Java does not use the same inheritance hierarchy.
 */
@WhatsAppWebModule(moduleName = "WAWebMarkChatAsReadSync")
public final class MarkChatAsReadHandler implements WebAppStateActionHandler {

    /**
     * Private constructor to enforce singleton pattern.
     */
    @WhatsAppWebExport(moduleName = "WAWebMarkChatAsReadSync", exports = "default", adaptation = WhatsAppAdaptation.ADAPTED)
    public MarkChatAsReadHandler() {

    }

    /**
     * Returns the action name for mark chat as read actions.
     * @return the action name {@code "markChatAsRead"}
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebMarkChatAsReadSync", exports = "getAction", adaptation = WhatsAppAdaptation.DIRECT)
    public String actionName() {
        return MarkChatAsReadAction.ACTION_NAME;
    }

    /**
     * Returns the sync collection for mark chat as read actions.
     *
     * <p>Per WhatsApp Web, the handler's {@code collectionName} is set to
     * {@code WASyncdConst.CollectionName.RegularLow} in the constructor.
     * @return {@link SyncPatchType#REGULAR_LOW}
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebMarkChatAsReadSync", exports = "collectionName", adaptation = WhatsAppAdaptation.DIRECT)
    public SyncPatchType collectionName() {
        return MarkChatAsReadAction.COLLECTION_NAME;
    }

    /**
     * Returns the mutation format version for mark chat as read actions.
     * @return the version number {@code 3}
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebMarkChatAsReadSync", exports = "getVersion", adaptation = WhatsAppAdaptation.DIRECT)
    public int version() {
        return MarkChatAsReadAction.ACTION_VERSION;
    }

    /**
     * Applies a mark chat as read mutation and returns a detailed result.
     *
     * <p>Per WhatsApp Web {@code WAWebMarkChatAsReadSync.applyMutations}, for each
     * mutation with {@code operation === "set"}:
     * <ol>
     *   <li>Extracts the chat JID from {@code indexParts[1]}; returns
     *       {@code malformedActionIndex} if empty</li>
     *   <li>Validates the sync action value via {@code validateSyncActionValue}
     *       (checks {@code read} is not {@code null} and {@code messageRange} is
     *       present and valid); returns {@code malformedActionValue} otherwise</li>
     *   <li>Validates the JID is a valid WID via {@code WAWebWid.isWid}</li>
     *   <li>Resolves the chat via {@code WAWebSyncdGetChat.resolveChatForMutationIndex}</li>
     *   <li>Delegates to {@code $MarkChatAsReadSync$p_3} which compares the local
     *       and remote message ranges and conditionally updates the chat's read
     *       state via {@code frontendSendAndReceive("updateChatReadStatus", ...)}</li>
     * </ol>
     *
     * <p>Non-{@code SET} operations return {@code Unsupported}. Exceptions are
     * caught and return {@code Failed}.
     *
     * <p>In Cobalt, the active-message-range gating from {@code $MarkChatAsReadSync$p_3}
     * is skipped because Cobalt does not maintain browser-side IndexedDB active
     * message ranges. The read-state change is applied directly: when
     * {@code read == true} the chat is marked as not unread with a zero unread count,
     * and when {@code read == false} the chat is marked as unread with an unread
     * count of {@code -1} (matching {@code WAWebConstantsDeprecated.MARKED_AS_UNREAD}).
     * The WA Web {@code _} helper's orphan branch for
     * {@code RangeBEnclosesRangeA}/{@code RangesNotEnclosing} is likewise skipped
     * because it is driven by the active-range comparison that Cobalt omits.
     * @param client   the WhatsApp client instance
     * @param mutation the mutation to apply
     * @return the detailed application result
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebMarkChatAsReadSync", exports = {"applyMutations", "validateSyncActionValue", "$MarkChatAsReadSync$p_3", "$MarkChatAsReadSync$p_1", "$MarkChatAsReadSync$p_2", "getMessageRange"}, adaptation = WhatsAppAdaptation.ADAPTED)
    public MutationApplicationResult applyMutation(WhatsAppClient client, DecryptedMutation.Trusted mutation) {
        if (mutation.operation() != SyncdOperation.SET) {
            return MutationApplicationResult.unsupported();
        }

        try {
            if (!(mutation.value().action().orElse(null) instanceof MarkChatAsReadAction action)) {
                return SyncdIndexUtils.malformedActionValue(collectionName().name());
            }

            var chatJidString = JSON.parseArray(mutation.index()).getString(1);
            if (chatJidString == null || chatJidString.isEmpty()) {
                return SyncdIndexUtils.malformedActionIndex(collectionName().name(), actionName());
            }

            var chatJid = Jid.of(chatJidString);
            if (chatJid == null) { // ADAPTED: Jid.of returns null for null input; WA Web uses isWid() validation
                return SyncdIndexUtils.malformedActionIndex(collectionName().name(), actionName());
            }

            var chat = client.store().findChatByJid(chatJid);
            if (chat.isEmpty()) {
                return MutationApplicationResult.orphan(chatJidString, "Chat");
            }

            // In Cobalt, MarkChatAsReadAction.read() null-coalesces to false, so a missing read
            // is treated as "mark as unread" which is still a meaningful state. The messageRange
            // validation is skipped because Cobalt does not maintain active message ranges
            // (browser-specific IndexedDB concern). See $MarkChatAsReadSync$p_3 — the core
            // read-state change is always applied.
            // ADAPTED: Cobalt applies the read-state change directly on the local chat, matching
            // the backend behavior that $p_1 would have triggered (unreadCount=0 / markedAsUnread=false
            // for read=true; unreadCount=-1 / markedAsUnread=true for read=false, per
            if (action.read()) {
                chat.get().setMarkedAsUnread(false); // ADAPTED: $p_1 -> backend updateChatReadStatus clears markedAsUnread
                chat.get().setUnreadCount(0); // ADAPTED: $p_1 -> backend updateChatReadStatus zeroes unreadCount
            } else {
                chat.get().setMarkedAsUnread(true); // ADAPTED: $p_1 -> backend updateChatReadStatus sets markedAsUnread
                chat.get().setUnreadCount(-1);
            }

            return MutationApplicationResult.success();
        } catch (Exception e) {
            return MutationApplicationResult.failed();
        }
    }

    /**
     * Resolves conflicts between a local pending mark-chat-as-read mutation and
     * an incoming remote mark-chat-as-read mutation using message range comparison.
     *
     * <p>Per WhatsApp Web {@code WAWebMarkChatAsReadSync.resolveConflicts}:
     * <ol>
     *   <li>Decodes the local and remote {@code markChatAsReadAction} values</li>
     *   <li>Compares their message ranges via
     *       {@code WAWebMessageRangeUtils.compareMessageRanges(remote, local)}</li>
     *   <li>Resolves based on the enclosure type:
     *     <ul>
     *       <li>{@code RangeAEnclosesRangeB} (remote encloses local): apply remote, drop local</li>
     *       <li>{@code RangeBEnclosesRangeA} (local encloses remote): skip remote</li>
     *       <li>{@code RangesAreEqual}: timestamp tiebreaker ({@code local <= remote}
     *           means apply remote)</li>
     *       <li>{@code RangesNotEnclosing}: merge the two ranges, pick the {@code read}
     *           value from the newer mutation, and return
     *           {@code SKIP_REMOTE_DROP_LOCAL} with the merged mutation</li>
     *     </ul>
     *   </li>
     * </ol>
     *
     * <p>In WA Web, the merged mutation is applied directly inside
     * {@code lockForMessageRangeSync} by calling {@code addActiveMessageRange} and
     * {@code $MarkChatAsReadSync$p_1}. In Cobalt, the merged mutation is returned
     * to the caller via {@link ConflictResolution#merged(DecryptedMutation.Trusted)}
     * so that application and resolution remain decoupled.
     * @param localMutation  the local pending mutation
     * @param remoteMutation the incoming remote mutation
     * @return the conflict resolution indicating which mutation to keep and
     *         optionally a merged mutation
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebMarkChatAsReadSync", exports = "resolveConflicts", adaptation = WhatsAppAdaptation.ADAPTED)
    public ConflictResolution resolveConflicts(DecryptedMutation.Trusted localMutation, DecryptedMutation.Trusted remoteMutation) {
        var localAction = localMutation.value().action()
                .filter(a -> a instanceof MarkChatAsReadAction)
                .map(a -> (MarkChatAsReadAction) a)
                .orElse(null);
        var remoteAction = remoteMutation.value().action()
                .filter(a -> a instanceof MarkChatAsReadAction)
                .map(a -> (MarkChatAsReadAction) a)
                .orElse(null);

        if (localAction == null || remoteAction == null) { // ADAPTED: WA Web uses WANullthrows which would throw; Cobalt gracefully falls back to apply remote
            return ConflictResolution.of(ConflictResolutionState.APPLY_REMOTE_DROP_LOCAL); // ADAPTED: defensive fallback
        }

        var localRange = localAction.messageRange().orElse(null);
        var remoteRange = remoteAction.messageRange().orElse(null);

        if (localRange == null || remoteRange == null) { // ADAPTED: WA Web uses WANullthrows; Cobalt gracefully falls back
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
                var localWins = localMutation.timestamp().compareTo(remoteMutation.timestamp()) > 0;
                var read = localWins ? localAction.read() : remoteAction.read();
                var mergedRange = MessageRangeUtils.mergeMessageRanges(remoteRange, localRange);
                var mergedAction = new MarkChatAsReadActionBuilder()
                        .read(read)
                        .messageRange(mergedRange)
                        .build();
                var mergedValue = new SyncActionValueBuilder()
                        .timestamp(remoteMutation.timestamp()) // ADAPTED: WA Web spreads all of l; in practice only timestamp and markChatAsReadAction are meaningful for this handler's collection
                        .markChatAsReadAction(mergedAction)
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
