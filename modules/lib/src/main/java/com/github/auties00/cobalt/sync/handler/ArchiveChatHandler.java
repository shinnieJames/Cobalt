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
import com.github.auties00.cobalt.model.sync.action.chat.ArchiveChatAction;
import com.github.auties00.cobalt.model.sync.action.chat.ArchiveChatActionBuilder;
import com.github.auties00.cobalt.model.sync.data.SyncdOperation;
import com.github.auties00.cobalt.sync.crypto.DecryptedMutation;

/**
 * Handles archive chat sync actions.
 *
 * <p>This handler processes incoming mutations that archive or unarchive chats,
 * resolves conflicts between local and remote archive mutations using message
 * range comparison, and builds outgoing mutations for user-initiated archive
 * actions.
 *
 * <p>The action is identified by the {@code "archive"} action name in
 * {@code SyncActionValue.archiveChatAction}. The mutation index format is
 * {@code ["archive", chatJid]}.
 *
 * <p>Per WhatsApp Web, this handler extends {@code ChatMessageRangeSyncdActionBase},
 * which provides shared message-range-based conflict resolution. In Cobalt, this
 * logic is inlined since Java does not use the same inheritance hierarchy.
 */
@WhatsAppWebModule(moduleName = "WAWebArchiveChatSync")
public final class ArchiveChatHandler implements WebAppStateActionHandler {

    /**
     * Private constructor to enforce singleton pattern.
     */
    @WhatsAppWebExport(moduleName = "WAWebArchiveChatSync", exports = "default", adaptation = WhatsAppAdaptation.ADAPTED)
    public ArchiveChatHandler() {

    }

    /**
     * Returns the action name for archive chat actions.
     * @return the action name {@code "archive"}
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebArchiveChatSync", exports = "getAction", adaptation = WhatsAppAdaptation.DIRECT)
    public String actionName() {
        return ArchiveChatAction.ACTION_NAME;
    }

    /**
     * Returns the sync collection for archive chat actions.
     *
     * <p>Per WhatsApp Web, the archive handler's {@code collectionName} is set to
     * {@code WASyncdConst.CollectionName.RegularLow} in the constructor.
     * @return {@link SyncPatchType#REGULAR_LOW}
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebArchiveChatSync", exports = "collectionName", adaptation = WhatsAppAdaptation.DIRECT)
    public SyncPatchType collectionName() {
        return ArchiveChatAction.COLLECTION_NAME;
    }

    /**
     * Returns the mutation format version for archive chat actions.
     * @return the version number {@code 3}
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebArchiveChatSync", exports = "getVersion", adaptation = WhatsAppAdaptation.DIRECT)
    public int version() {
        return ArchiveChatAction.ACTION_VERSION;
    }

    /**
     * Applies an archive chat mutation and returns a detailed result.
     *
     * <p>Per WhatsApp Web {@code WAWebArchiveChatSync.applyMutations}, for each
     * mutation with {@code operation === "set"}:
     * <ol>
     *   <li>Extracts the chat JID from {@code indexParts[1]}</li>
     *   <li>Validates the JID is a valid WID via {@code WAWebWid.isWid}</li>
     *   <li>Resolves the chat via {@code WAWebSyncdGetChat.resolveChatForMutationIndex}</li>
     *   <li>Validates the action value via {@code validateSyncActionValue} (checks
     *       {@code archived} is not null, {@code messageRange} is present and valid)</li>
     *   <li>Applies the archive state change via {@code $ArchiveChatSync$p_2}</li>
     * </ol>
     *
     * <p>Non-{@code SET} operations return {@code Unsupported}. Exceptions are
     * caught and return {@code Failed}.
     * @param client   the WhatsApp client instance
     * @param mutation the mutation to apply
     * @return the detailed application result
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebArchiveChatSync", exports = {"applyMutations", "validateSyncActionValue", "getMessageRange"}, adaptation = WhatsAppAdaptation.ADAPTED)
    public MutationApplicationResult applyMutation(WhatsAppClient client, DecryptedMutation.Trusted mutation) {
        if (mutation.operation() != SyncdOperation.SET) {
            return MutationApplicationResult.unsupported();
        }

        try {
            if (!(mutation.value().action().orElse(null) instanceof ArchiveChatAction action)) {
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

            // In Cobalt, archived() null-coalesces to false via ArchiveChatAction.archived(),
            // so null archived effectively means "unarchive" which is still valid behavior.
            // The messageRange validation is skipped because Cobalt does not maintain active
            // message ranges (browser-specific IndexedDB concern).
            // See $ArchiveChatSync$p_2 — the core archive state change is always applied.
            chat.get().setArchived(action.archived()); // ADAPTED: WAWebArchiveChatSync.$ArchiveChatSync$p_2 — Cobalt applies archive directly without message range gating
            return MutationApplicationResult.success();
        } catch (Exception e) {
            return MutationApplicationResult.failed();
        }
    }

    /**
     * Resolves conflicts between a local pending archive mutation and an incoming
     * remote archive mutation using message range comparison.
     *
     * <p>Per WhatsApp Web {@code WAWebArchiveChatSync.resolveConflicts}:
     * <ol>
     *   <li>Decodes the local and remote {@code archiveChatAction} values</li>
     *   <li>Compares their message ranges via
     *       {@code WAWebMessageRangeUtils.compareMessageRanges(remote, local)}</li>
     *   <li>Resolves based on the enclosure type:
     *     <ul>
     *       <li>{@code RangeAEnclosesRangeB} (remote encloses local): apply remote, drop local</li>
     *       <li>{@code RangeBEnclosesRangeA} (local encloses remote): skip remote</li>
     *       <li>{@code RangesAreEqual}: timestamp tiebreaker ({@code local <= remote}
     *           means apply remote)</li>
     *       <li>{@code RangesNotEnclosing}: merge the two ranges, pick the {@code archived}
     *           value from the newer mutation, and return
     *           {@code SKIP_REMOTE_DROP_LOCAL} with the merged mutation</li>
     *     </ul>
     *   </li>
     * </ol>
     * @param localMutation  the local pending mutation
     * @param remoteMutation the incoming remote mutation
     * @return the conflict resolution indicating which mutation to keep
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebArchiveChatSync", exports = "resolveConflicts", adaptation = WhatsAppAdaptation.ADAPTED)
    public ConflictResolution resolveConflicts(DecryptedMutation.Trusted localMutation, DecryptedMutation.Trusted remoteMutation) {
        var localAction = localMutation.value().action()
                .filter(a -> a instanceof ArchiveChatAction)
                .map(a -> (ArchiveChatAction) a)
                .orElse(null);
        var remoteAction = remoteMutation.value().action()
                .filter(a -> a instanceof ArchiveChatAction)
                .map(a -> (ArchiveChatAction) a)
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
                var localWins = localMutation.timestamp().compareTo(remoteMutation.timestamp()) > 0;
                var archived = localWins ? localAction.archived() : remoteAction.archived();
                var mergedRange = MessageRangeUtils.mergeMessageRanges(remoteRange, localRange);
                var mergedAction = new ArchiveChatActionBuilder()
                        .archived(archived)
                        .messageRange(mergedRange)
                        .build();
                var mergedValue = new SyncActionValueBuilder()
                        .timestamp(remoteMutation.timestamp())
                        .archiveChatAction(mergedAction)
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
