package com.github.auties00.cobalt.sync.handler;

import com.alibaba.fastjson2.JSON;
import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.sync.ConflictResolution;
import com.github.auties00.cobalt.model.sync.ConflictResolutionState;
import com.github.auties00.cobalt.model.sync.SyncActionValueBuilder;
import com.github.auties00.cobalt.model.sync.SyncPatchType;
import com.github.auties00.cobalt.model.sync.action.chat.ArchiveChatAction;
import com.github.auties00.cobalt.model.sync.action.chat.ArchiveChatActionBuilder;
import com.github.auties00.cobalt.model.sync.data.SyncdOperation;
import com.github.auties00.cobalt.sync.crypto.DecryptedMutation;

/**
 * Handles archive chat actions.
 *
 * <p>This handler processes mutations that archive or unarchive chats.
 * The action is identified by the "archiveChatAction" field in ActionValueSync.
 *
 * <p>Index format: ["archiveChatAction", "chatJid"]
 */
public final class ArchiveChatHandler implements WebAppStateActionHandler {
    public static final ArchiveChatHandler INSTANCE = new ArchiveChatHandler();

    private ArchiveChatHandler() {

    }

    @Override
    public String actionName() {
        return ArchiveChatAction.ACTION_NAME;
    }

    @Override
    public SyncPatchType collectionName() {
        return ArchiveChatAction.COLLECTION_NAME;
    }

    @Override
    public int version() {
        return ArchiveChatAction.ACTION_VERSION;
    }

    @Override
    public boolean applyMutation(WhatsAppClient client, DecryptedMutation.Trusted mutation) {
        if (mutation.operation() != SyncdOperation.SET) {
            return false;
        }

        if (!(mutation.value().action().orElse(null) instanceof ArchiveChatAction action)) {
            return false;
        }

        var chatJidString = JSON.parseArray(mutation.index())
                .getString(1);
        var chatJid = Jid.of(chatJidString);

        var chat = client.store()
                .findChatByJid(chatJid);
        if (chat.isEmpty()) {
            return false;
        }

        chat.get().setArchived(action.archived());

        return true;
    }

    /**
     * Resolves conflicts using message range comparison.
     *
     * <p>Per WhatsApp Web {@code WAWebArchiveChatSync.resolveConflicts}:
     * <ul>
     *   <li>If remote range encloses local: apply remote, drop local
     *   <li>If local range encloses remote: skip remote
     *   <li>If ranges are equal: timestamp tiebreaker (local {@code <=} remote means apply remote)
     *   <li>If ranges don't enclose each other: merge the two ranges, pick
     *       the {@code archived} value from the newer mutation, and return
     *       {@code SKIP_REMOTE_DROP_LOCAL} with the merged mutation
     * </ul>
     *
     * @param localMutation  the local pending mutation
     * @param remoteMutation the incoming remote mutation
     * @return the conflict resolution indicating which mutation to keep
     */
    @Override
    public ConflictResolution resolveConflicts(DecryptedMutation.Trusted localMutation, DecryptedMutation.Trusted remoteMutation) {
        var localAction = localMutation.value().action()
                .filter(a -> a instanceof ArchiveChatAction)
                .map(a -> (ArchiveChatAction) a)
                .orElse(null);
        var remoteAction = remoteMutation.value().action()
                .filter(a -> a instanceof ArchiveChatAction)
                .map(a -> (ArchiveChatAction) a)
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
            case RANGE_A_ENCLOSES_RANGE_B -> ConflictResolution.of(ConflictResolutionState.APPLY_REMOTE_DROP_LOCAL);
            case RANGE_B_ENCLOSES_RANGE_A -> ConflictResolution.of(ConflictResolutionState.SKIP_REMOTE);
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
                yield ConflictResolution.merged(merged);
            }
        };
    }
}
