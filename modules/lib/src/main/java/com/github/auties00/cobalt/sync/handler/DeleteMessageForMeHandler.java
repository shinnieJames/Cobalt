package com.github.auties00.cobalt.sync.handler;

import com.alibaba.fastjson2.JSON;
import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.sync.*;
import com.github.auties00.cobalt.model.sync.action.chat.DeleteMessageForMeAction;
import com.github.auties00.cobalt.sync.ConflictResolution;
import com.github.auties00.cobalt.model.sync.data.SyncdOperation;
import com.github.auties00.cobalt.sync.crypto.DecryptedMutation;

/**
 * Handles delete message for me sync actions.
 *
 * <p>This handler processes mutations that delete messages locally
 * (not for everyone in the chat). It extends the base message sync
 * action pattern with {@code collectionName = RegularHigh},
 * {@code chatJidIndex = 1}, {@code getVersion() = 3}, and
 * {@code getAction() = "deleteMessageForMe"}.
 *
 * <p>Index format: {@code ["deleteMessageForMe", chatJid, messageId, fromMe, participant]}
 */
@WhatsAppWebModule(moduleName = "WAWebDeleteMessageForMeSync")
public final class DeleteMessageForMeHandler implements WebAppStateActionHandler {

    /**
     * Private constructor to enforce singleton pattern.
     */
    @WhatsAppWebExport(moduleName = "WAWebDeleteMessageForMeSync", exports = "default", adaptation = WhatsAppAdaptation.ADAPTED)
    public DeleteMessageForMeHandler() {

    }

    /**
     * Returns the action name for delete message for me actions.
     * @return the action name {@code "deleteMessageForMe"}
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebDeleteMessageForMeSync", exports = "getAction", adaptation = WhatsAppAdaptation.DIRECT)
    public String actionName() {
        return DeleteMessageForMeAction.ACTION_NAME;
    }

    /**
     * Returns the sync collection for delete message for me actions.
     * @return {@link SyncPatchType#REGULAR_HIGH}
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebDeleteMessageForMeSync", exports = "collectionName", adaptation = WhatsAppAdaptation.DIRECT)
    public SyncPatchType collectionName() {
        return DeleteMessageForMeAction.COLLECTION_NAME;
    }

    /**
     * Returns the mutation format version for delete message for me actions.
     * @return {@code 3}
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebDeleteMessageForMeSync", exports = "getVersion", adaptation = WhatsAppAdaptation.DIRECT)
    public int version() {
        return DeleteMessageForMeAction.ACTION_VERSION;
    }

    /**
     * Applies a single delete-message-for-me mutation and returns a detailed result.
     *
     * <p>Per WhatsApp Web {@code WAWebDeleteMessageForMeSync.applyMutations}:
     * <ul>
     *   <li>If the operation is not {@code SET}, returns {@code Unsupported}</li>
     *   <li>Extracts the four index parts: remote JID, message ID, fromMe, participant</li>
     *   <li>If any index part is missing or empty, returns {@code malformedActionIndex()}</li>
     *   <li>Builds a {@link MessageKey} via {@code syncKeyToMsgKey} for the orphan model ID</li>
     *   <li>Looks up the chat by JID; if not found, returns {@code Orphan} with the
     *       serialized MsgKey as model ID and {@code "Msg"} as model type</li>
     *   <li>Looks up the message by ID; if not found, returns {@code Orphan}</li>
     *   <li>If found, removes the message and returns {@code Success}</li>
     * </ul>
     * @param client   the WhatsApp client instance
     * @param mutation the mutation to apply
     * @return the detailed application result
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebDeleteMessageForMeSync", exports = {"applyMutations", "getMessageKey"}, adaptation = WhatsAppAdaptation.ADAPTED)
    public MutationApplicationResult applyMutation(WhatsAppClient client, DecryptedMutation.Trusted mutation) {
        if (mutation.operation() != SyncdOperation.SET) {
            return MutationApplicationResult.unsupported();
        }

        var indexArray = JSON.parseArray(mutation.index());
        if (indexArray.size() < 5) {
            return SyncdIndexUtils.malformedActionIndex(collectionName().name(), actionName());
        }

        var chatJidString = indexArray.getString(1);
        var messageId = indexArray.getString(2);
        var fromMeString = indexArray.getString(3);
        var participantString = indexArray.getString(4);

        if (isNullOrEmpty(chatJidString) || isNullOrEmpty(messageId)
                || isNullOrEmpty(fromMeString) || isNullOrEmpty(participantString)) {
            return SyncdIndexUtils.malformedActionIndex(collectionName().name(), actionName());
        }

        var msgKey = SyncdIndexUtils.syncKeyToMsgKey(
                client.store(), chatJidString, messageId, fromMeString, participantString
        );
        if (msgKey.isEmpty()) {
            return SyncdIndexUtils.malformedActionIndex(collectionName().name(), actionName());
        }

        // ADAPTED: Cobalt resolves chat directly from the index JID without the
        // incomingRemoteToLocalChatId cache used by WAWebSyncdResolveMessages
        var chatJid = Jid.of(chatJidString);
        var chat = client.store().findChatByJid(chatJid);
        if (chat.isEmpty()) {
            return MutationApplicationResult.orphan(
                    SyncdIndexUtils.serializeMessageKey(msgKey.get()),
                    "Msg"
            );
        }

        // ADAPTED: Cobalt uses findMessageById + filter instead of msgKeyToDbIdWithoutFromMeParticipant prefix match
        var fromMe = "1".equals(fromMeString);
        var participant = !"0".equals(participantString) ? Jid.of(participantString) : null;
        var removed = client.store()
                .findMessageById(chat.get(), messageId)
                .filter(msg -> msg.key().fromMe() == fromMe)
                .filter(msg -> participant == null || participant.toUserJid().equals(msg.key().senderJid().map(Jid::toUserJid).orElse(null)))
                .flatMap(info -> info.key().id())
                .map(id -> {
                    chat.get().removeMessage(id);
                    return id;
                })
                .isPresent();

        return removed
                ? MutationApplicationResult.success()
                : MutationApplicationResult.orphan(
                        SyncdIndexUtils.serializeMessageKey(msgKey.get()),
                        "Msg"
                );
    }

    /**
     * Resolves conflicts based on the {@code deleteMedia} field.
     *
     * <p>Per WhatsApp Web {@code WAWebDeleteMessageForMeSync.resolveConflicts}:
     * <ul>
     *   <li>If the remote has {@code deleteMedia=false} and the local has
     *       {@code deleteMedia=true}: keep the local mutation (it is more
     *       aggressive), return {@code SKIP_REMOTE}</li>
     *   <li>In all other cases: drop both mutations, return
     *       {@code SKIP_REMOTE_DROP_LOCAL}</li>
     * </ul>
     *
     * <p>Notably, this handler never returns {@code APPLY_REMOTE_DROP_LOCAL}
     * and does not use timestamp comparison at all.
     * @param localMutation  the local pending mutation
     * @param remoteMutation the incoming remote mutation
     * @return the conflict resolution indicating which mutation to keep
     */
    @Override
    @WhatsAppWebExport(moduleName = "WAWebDeleteMessageForMeSync", exports = "resolveConflicts", adaptation = WhatsAppAdaptation.DIRECT)
    public ConflictResolution resolveConflicts(DecryptedMutation.Trusted localMutation, DecryptedMutation.Trusted remoteMutation) {
        var localDeleteMedia = localMutation.value().action()
                .filter(a -> a instanceof DeleteMessageForMeAction)
                .map(a -> ((DeleteMessageForMeAction) a).deleteMedia())
                .orElse(false); // ADAPTED: WANullthrows would throw on null; Cobalt coalesces to false
        var remoteDeleteMedia = remoteMutation.value().action()
                .filter(a -> a instanceof DeleteMessageForMeAction)
                .map(a -> ((DeleteMessageForMeAction) a).deleteMedia())
                .orElse(false); // ADAPTED: WANullthrows would throw on null; Cobalt coalesces to false

        if (!remoteDeleteMedia && localDeleteMedia) {
            return ConflictResolution.of(ConflictResolutionState.SKIP_REMOTE);
        }

        return ConflictResolution.of(ConflictResolutionState.SKIP_REMOTE_DROP_LOCAL);
    }

    /**
     * Checks whether the given string is {@code null} or empty.
     *
     * <p>Used to replicate WA Web's JavaScript falsy check ({@code !value})
     * on index part strings.
     * @param value the string to check
     * @return {@code true} if the string is {@code null} or empty
     */
    private static boolean isNullOrEmpty(String value) {
        return value == null || value.isEmpty(); // ADAPTED: JS !value is falsy for null/undefined/""
    }
}
