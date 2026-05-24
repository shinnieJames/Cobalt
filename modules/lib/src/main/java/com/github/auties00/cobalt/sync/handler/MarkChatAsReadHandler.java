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
 * Applies the {@code markChatAsRead} app-state sync action that flips a
 * chat's read or unread state across the user's linked devices.
 *
 * @apiNote
 * Drives the chat-list "Mark as read" / "Mark as unread" affordance:
 * when the primary device toggles the read state the resulting bit
 * fans out across the {@link SyncPatchType#REGULAR_LOW} collection so
 * companions render the same unread badge. The mutation index keys
 * each entry by the chat JID, formatted as
 * {@snippet :
 *     ["markChatAsRead", chatJid]
 * }
 *
 * @implNote
 * This implementation applies the read-state change directly on the
 * local {@link com.github.auties00.cobalt.model.chat.Chat}, replacing
 * WA Web's
 * {@code frontendSendAndReceive("updateChatReadStatus", ...)} RPC.
 * For {@code read = true} the chat is marked as not unread with
 * {@code unreadCount = 0}; for {@code read = false} it is marked as
 * unread with {@code unreadCount = -1}, the
 * {@code WAWebConstantsDeprecated.MARKED_AS_UNREAD} sentinel that WA
 * Web uses on its own chat table. The
 * {@code addActiveMessageRange} bookkeeping and the
 * {@code RangeBEnclosesRangeA / RangesNotEnclosing} orphan branch
 * driven by {@code $MarkChatAsReadSync$p_3} are not modelled because
 * Cobalt does not maintain browser-side IndexedDB active message
 * ranges.
 */
@WhatsAppWebModule(moduleName = "WAWebMarkChatAsReadSync")
public final class MarkChatAsReadHandler implements WebAppStateActionHandler {

    /**
     * Constructs a new singleton {@link MarkChatAsReadHandler}.
     */
    @WhatsAppWebExport(moduleName = "WAWebMarkChatAsReadSync", exports = "default", adaptation = WhatsAppAdaptation.ADAPTED)
    public MarkChatAsReadHandler() {

    }

    /**
     * {@inheritDoc}
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebMarkChatAsReadSync", exports = "getAction", adaptation = WhatsAppAdaptation.DIRECT)
    public String actionName() {
        return MarkChatAsReadAction.ACTION_NAME;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebMarkChatAsReadSync", exports = "collectionName", adaptation = WhatsAppAdaptation.DIRECT)
    public SyncPatchType collectionName() {
        return MarkChatAsReadAction.COLLECTION_NAME;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebMarkChatAsReadSync", exports = "getVersion", adaptation = WhatsAppAdaptation.DIRECT)
    public int version() {
        return MarkChatAsReadAction.ACTION_VERSION;
    }

    /**
     * {@inheritDoc}
     *
     * @implNote
     * This implementation skips WA Web's
     * {@code validateMessageRange} and
     * {@code constructMessageRange + compareMessageRanges} chain
     * because Cobalt does not maintain active message ranges; the
     * read-state change is applied directly to the
     * {@link com.github.auties00.cobalt.model.chat.Chat} so the
     * companion view matches the primary's intent. Any thrown
     * exception is mapped to
     * {@link MutationApplicationResult#failed()} mirroring WA Web's
     * try/catch shape.
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
            if (chatJid == null) {
                return SyncdIndexUtils.malformedActionIndex(collectionName().name(), actionName());
            }

            var chat = client.store().findChatByJid(chatJid);
            if (chat.isEmpty()) {
                return MutationApplicationResult.orphan(chatJidString, "Chat");
            }

            if (action.read()) {
                chat.get().setMarkedAsUnread(false);
                chat.get().setUnreadCount(0);
            } else {
                chat.get().setMarkedAsUnread(true);
                chat.get().setUnreadCount(-1);
            }

            return MutationApplicationResult.success();
        } catch (Exception e) {
            return MutationApplicationResult.failed();
        }
    }

    /**
     * {@inheritDoc}
     *
     * @implNote
     * This implementation decodes both
     * {@link MarkChatAsReadAction} payloads and delegates the four-way
     * enclosure decision to
     * {@link MessageRangeUtils#compareMessageRanges}. When neither
     * range encloses the other a merged action is built with the
     * {@code read} flag drawn from the more-recent mutation and
     * returned via {@link ConflictResolution#merged} for the caller to
     * apply, separating resolution from application; WA Web instead
     * applies the merged mutation immediately under
     * {@code lockForMessageRangeSync}. A {@code null} action or
     * {@code messageRange} on either side defaults to
     * {@link ConflictResolutionState#APPLY_REMOTE_DROP_LOCAL} where WA
     * Web would throw via {@code WANullthrows}.
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

        if (localAction == null || remoteAction == null) {
            return ConflictResolution.of(ConflictResolutionState.APPLY_REMOTE_DROP_LOCAL);
        }

        var localRange = localAction.messageRange().orElse(null);
        var remoteRange = remoteAction.messageRange().orElse(null);

        if (localRange == null || remoteRange == null) {
            return ConflictResolution.of(ConflictResolutionState.APPLY_REMOTE_DROP_LOCAL);
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
                        .timestamp(remoteMutation.timestamp())
                        .markChatAsReadAction(mergedAction)
                        .build();
                var merged = new DecryptedMutation.Trusted(
                        localMutation.index(),
                        mergedValue,
                        localMutation.operation(),
                        localMutation.timestamp(),
                        localMutation.actionVersion()
                );
                yield ConflictResolution.merged(merged);
            }
        };
    }

}
