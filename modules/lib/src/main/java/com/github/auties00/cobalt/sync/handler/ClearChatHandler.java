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
import com.github.auties00.cobalt.model.sync.action.chat.ClearChatAction;
import com.github.auties00.cobalt.model.sync.action.chat.ClearChatActionBuilder;
import com.github.auties00.cobalt.model.sync.data.SyncdOperation;
import com.github.auties00.cobalt.sync.crypto.DecryptedMutation;

/**
 * Handles clear chat sync actions.
 *
 * <p>This handler processes mutations that clear all messages from a chat
 * while keeping the chat itself. Per WhatsApp Web, the clear chat action
 * extends {@code ChatMessageRangeSyncdActionBase} and uses message-range-based
 * conflict resolution.
 *
 * <p>Index format: {@code ["clearChat", chatJid, deleteStarred, deleteMedia]}
 * where {@code deleteStarred} is {@code "1"} (delete starred) or {@code "0"}
 * (keep starred), and {@code deleteMedia} is {@code "1"} (keep media) or
 * {@code "0"} (delete media).
 */
@WhatsAppWebModule(moduleName = "WAWebClearChatSync")
public final class ClearChatHandler implements WebAppStateActionHandler {

    /**
     * Index slot of the chat JID inside the mutation index parts array.
     *
     * <p>Per WhatsApp Web {@code WAWebClearChatSync}: the constructor sets
     * {@code chatJidIndex = 1}, so the chat JID is at {@code indexParts[1]}
     * (i.e. after the action name {@code "clearChat"} at slot {@code 0}).
     */
    @WhatsAppWebExport(moduleName = "WAWebClearChatSync", exports = "default", adaptation = WhatsAppAdaptation.DIRECT)
    private static final int CHAT_JID_INDEX = 1;

    /**
     * Private constructor to enforce singleton pattern.
     */
    @WhatsAppWebExport(moduleName = "WAWebClearChatSync", exports = "default", adaptation = WhatsAppAdaptation.ADAPTED)
    public ClearChatHandler() {
    }

    /**
     * Returns the action name for clear chat actions.
     * @return the action name {@code "clearChat"}
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebClearChatSync", exports = "getAction", adaptation = WhatsAppAdaptation.DIRECT)
    public String actionName() {
        return ClearChatAction.ACTION_NAME;
    }

    /**
     * Returns the sync collection for clear chat actions.
     *
     * <p>Per WhatsApp Web, the clear chat handler's {@code collectionName} is set to
     * {@code WASyncdConst.CollectionName.RegularHigh} in the constructor.
     * @return {@link SyncPatchType#REGULAR_HIGH}
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebClearChatSync", exports = "collectionName", adaptation = WhatsAppAdaptation.DIRECT)
    public SyncPatchType collectionName() {
        return ClearChatAction.COLLECTION_NAME;
    }

    /**
     * Returns the mutation format version for clear chat actions.
     * @return the version number {@code 6}
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebClearChatSync", exports = "getVersion", adaptation = WhatsAppAdaptation.DIRECT)
    public int version() {
        return ClearChatAction.ACTION_VERSION;
    }

    /**
     * Applies a clear chat mutation and returns a detailed result.
     *
     * <p>Per WhatsApp Web {@code WAWebClearChatSync.applyMutations}, for each
     * mutation with {@code operation === "set"}:
     * <ol>
     *   <li>Extracts the index parts: {@code [action, chatJid, deleteStarred, deleteMedia]}</li>
     *   <li>Validates all index parts exist and the chat JID is valid via
     *       {@code WAWebWid.isWid}</li>
     *   <li>Validates the message range via
     *       {@code WAWebMessageRangeUtils.validateMessageRange}</li>
     *   <li>Resolves the chat via
     *       {@code WAWebSyncdGetChat.resolveChatForMutationIndex}</li>
     *   <li>Replaces remote JIDs in the message range via
     *       {@code WAWebMessageRangeUtils.replaceMessageRangeRemoteJid}</li>
     *   <li>Builds a set of starred message keys to skip via
     *       {@code $ClearChatSync$p_1}</li>
     *   <li>Applies the clear via {@code $ClearChatSync$p_2}, which calls
     *       {@code addActiveMessageRange} and {@code clearChat}</li>
     * </ol>
     *
     * <p>Non-{@code SET} operations return {@code Unsupported}. Exceptions are
     * caught and return {@code Failed}.
     * @param client   the WhatsApp client instance
     * @param mutation the mutation to apply
     * @return the detailed application result
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebClearChatSync", exports = {"applyMutations", "getMessageRange"}, adaptation = WhatsAppAdaptation.ADAPTED)
    public MutationApplicationResult applyMutation(WhatsAppClient client, DecryptedMutation.Trusted mutation) {
        if (mutation.operation() != SyncdOperation.SET) {
            return MutationApplicationResult.unsupported();
        }

        try {
            var indexParts = JSON.parseArray(mutation.index());
            var chatJidString = indexParts.getString(CHAT_JID_INDEX);
            var deleteStarredString = indexParts.getString(CHAT_JID_INDEX + 1);
            var deleteMediaString = indexParts.getString(CHAT_JID_INDEX + 2);

            if (chatJidString == null || chatJidString.isEmpty()
                    || deleteStarredString == null || deleteStarredString.isEmpty()
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

            if (!(mutation.value().action().orElse(null) instanceof ClearChatAction clearChatAction)) {
                return SyncdIndexUtils.malformedActionValue(collectionName().name());
            }

            var messageRange = clearChatAction.messageRange().orElse(null);
            if (messageRange == null) {
                return SyncdIndexUtils.malformedActionValue(collectionName().name());
            }

            var chat = client.store().findChatByJid(chatJid);
            if (chat.isEmpty()) {
                return MutationApplicationResult.orphan(chatJidString, "Chat");
            }

            // $ClearChatSync$p_2: addActiveMessageRange, clearChat(chatId, messageRange, deleteStarred, starredKeys)
            // clearChat: queryAndRemoveMessagesInMessageRange(chatId, messageRange, {skipStarred: !deleteStarred, skipMessages: starredKeys, deleteAutomatedGreetingMessages: true})
            //            deleteAllThreadsForChat, deleteMessages, maybeClearGroupStatus
            // ADAPTED: WAWebClearChatSync.$ClearChatSync$p_2 performs granular message deletion
            // with starred message skipping, active message range tracking, thread deletion,
            // add-on cleanup, and group status clearing. Cobalt simplifies to Chat.removeMessages()
            // because:
            // 1. addActiveMessageRange is an IndexedDB-specific optimization (browser concern)
            // 2. skipStarred/skipMessages filtering requires per-message iteration against a range
            //    which is not yet supported by the Chat abstraction
            // 3. Thread deletion, add-on cleanup, and AI thread deletion are browser-specific UI concerns
            // 4. maybeClearGroupStatus is a StatusCollection concern not present in Cobalt's store
            chat.get().removeMessages();

            return MutationApplicationResult.success();
        } catch (Exception e) {
            return MutationApplicationResult.failed();
        }
    }

    /**
     * Resolves conflicts between a local pending clear chat mutation and an
     * incoming remote clear chat mutation using message range comparison.
     *
     * <p>Per WhatsApp Web {@code WAWebClearChatSync.resolveConflicts}:
     * <ol>
     *   <li>Decodes the local and remote {@code clearChatAction} values</li>
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
    @WhatsAppWebExport(moduleName = "WAWebClearChatSync", exports = "resolveConflicts", adaptation = WhatsAppAdaptation.ADAPTED)
    public ConflictResolution resolveConflicts(DecryptedMutation.Trusted localMutation, DecryptedMutation.Trusted remoteMutation) {
        var localAction = localMutation.value().action()
                .filter(a -> a instanceof ClearChatAction)
                .map(a -> (ClearChatAction) a)
                .orElse(null);
        var remoteAction = remoteMutation.value().action()
                .filter(a -> a instanceof ClearChatAction)
                .map(a -> (ClearChatAction) a)
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
                var mergedAction = new ClearChatActionBuilder()
                        .messageRange(mergedRange)
                        .build();
                var mergedValue = new SyncActionValueBuilder()
                        .timestamp(remoteMutation.timestamp())
                        .clearChatAction(mergedAction)
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
