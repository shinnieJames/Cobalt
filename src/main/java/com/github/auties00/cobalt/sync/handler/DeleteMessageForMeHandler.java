package com.github.auties00.cobalt.sync.handler;

import com.alibaba.fastjson2.JSON;
import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.sync.ConflictResolution;
import com.github.auties00.cobalt.model.sync.ConflictResolutionState;
import com.github.auties00.cobalt.model.sync.SyncPatchType;
import com.github.auties00.cobalt.model.sync.action.chat.DeleteMessageForMeAction;
import com.github.auties00.cobalt.model.sync.data.SyncdOperation;
import com.github.auties00.cobalt.sync.crypto.DecryptedMutation;

/**
 * Handles delete message for me actions.
 *
 * <p>This handler processes mutations that delete messages locally
 * (not for everyone in the chat).
 *
 * <p>Index format: ["deleteMessageForMe", "chatJid", "messageId", "fromMe", "participant"]
 */
public final class DeleteMessageForMeHandler implements WebAppStateActionHandler {
    public static final DeleteMessageForMeHandler INSTANCE = new DeleteMessageForMeHandler();

    private DeleteMessageForMeHandler() {

    }

    @Override
    public String actionName() {
        return DeleteMessageForMeAction.ACTION_NAME;
    }

    @Override
    public SyncPatchType collectionName() {
        return DeleteMessageForMeAction.COLLECTION_NAME;
    }

    @Override
    public int version() {
        return DeleteMessageForMeAction.ACTION_VERSION;
    }

    @Override
    public boolean applyMutation(WhatsAppClient client, DecryptedMutation.Trusted mutation) {
        if (mutation.operation() != SyncdOperation.SET) {
            return false;
        }

        if (!(mutation.value().action().orElse(null) instanceof DeleteMessageForMeAction _)) {
            return false;
        }

        var indexArray = JSON.parseArray(mutation.index());
        var chatJidString = indexArray.getString(1);
        var messageId = indexArray.getString(2);

        var chatJid = Jid.of(chatJidString);

        var chat = client.store()
                .findChatByJid(chatJid);
        if (chat.isEmpty()) {
            return false;
        }

        client.store()
                .findMessageById(chat.get(), messageId)
                .ifPresent(chatMessageInfo -> chat.get().removeMessage(chatMessageInfo.id()));

        return true;
    }

    /**
     * Resolves conflicts based on the {@code deleteMedia} field.
     *
     * <p>Per WhatsApp Web {@code WAWebDeleteMessageForMeSync.resolveConflicts}:
     * <ul>
     *   <li>If the remote has {@code deleteMedia=false} and the local has
     *       {@code deleteMedia=true}: keep the local mutation (it is more
     *       aggressive), return {@code SKIP_REMOTE}
     *   <li>In all other cases: drop both mutations, return
     *       {@code SKIP_REMOTE_DROP_LOCAL}
     * </ul>
     *
     * <p>Notably, this handler never returns {@code APPLY_REMOTE_DROP_LOCAL}
     * and does not use timestamp comparison at all.
     *
     * @param localMutation  the local pending mutation
     * @param remoteMutation the incoming remote mutation
     * @return the conflict resolution indicating which mutation to keep
     */
    @Override
    public ConflictResolution resolveConflicts(DecryptedMutation.Trusted localMutation, DecryptedMutation.Trusted remoteMutation) {
        var localDeleteMedia = localMutation.value().action()
                .filter(a -> a instanceof DeleteMessageForMeAction)
                .map(a -> ((DeleteMessageForMeAction) a).deleteMedia())
                .orElse(false);
        var remoteDeleteMedia = remoteMutation.value().action()
                .filter(a -> a instanceof DeleteMessageForMeAction)
                .map(a -> ((DeleteMessageForMeAction) a).deleteMedia())
                .orElse(false);

        if (!remoteDeleteMedia && localDeleteMedia) {
            return ConflictResolution.of(ConflictResolutionState.SKIP_REMOTE);
        }

        return ConflictResolution.of(ConflictResolutionState.SKIP_REMOTE_DROP_LOCAL);
    }
}
