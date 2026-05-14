package com.github.auties00.cobalt.sync.factory;

import com.alibaba.fastjson2.JSON;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.message.MessageKey;
import com.github.auties00.cobalt.model.sync.SyncActionValueBuilder;
import com.github.auties00.cobalt.model.sync.action.chat.DeleteMessageForMeAction;
import com.github.auties00.cobalt.model.sync.action.chat.DeleteMessageForMeActionBuilder;
import com.github.auties00.cobalt.model.sync.data.SyncdOperation;
import com.github.auties00.cobalt.sync.SyncPendingMutation;
import com.github.auties00.cobalt.sync.crypto.DecryptedMutation;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Builds outgoing delete-message-for-me sync mutations.
 *
 * <p>Mirrors the {@code getDeleteForMeMutations} and
 * {@code buildDeleteForMeMutation} exports of WhatsApp Web's
 * {@code WAWebDeleteMessageForMeSync} module. The factory is the
 * outgoing-mutation counterpart of
 * {@link com.github.auties00.cobalt.sync.handler.DeleteMessageForMeHandler}.
 */
public final class DeleteMessageForMeMutationFactory {
    /**
     * Constructs a delete-message-for-me mutation factory.
     */
    public DeleteMessageForMeMutationFactory() {

    }

    /**
     * Builds a pending mutation for a delete-for-me action on a single message.
     *
     * <p>Per WhatsApp Web {@code WAWebDeleteMessageForMeSync.buildDeleteForMeMutation}:
     * constructs a {@code deleteMessageForMeAction} value with the given
     * {@code deleteMedia} and {@code messageTimestamp} fields, builds the index
     * via {@code WAWebSyncdActionUtils.buildMessageKey} using the remaining
     * parameters, and delegates to {@code WAWebSyncdActionUtils.buildPendingMutation}.
     *
     * @param timestamp        the mutation timestamp (current time)
     * @param deleteMedia      whether to also delete associated media
     * @param messageTimestamp the original message timestamp
     * @param remoteJid        the chat JID (resolved for mutation index)
     * @param id               the message ID
     * @param fromMe           whether the message was sent by the current user
     * @param participant      the participant JID for group messages, or {@code null}
     * @return the pending mutation for the delete-for-me action
     */
    @WhatsAppWebExport(moduleName = "WAWebDeleteMessageForMeSync", exports = "buildDeleteForMeMutation", adaptation = WhatsAppAdaptation.ADAPTED)
    public SyncPendingMutation buildDeleteForMeMutation(
            Instant timestamp,
            boolean deleteMedia,
            Instant messageTimestamp,
            Jid remoteJid,
            String id,
            boolean fromMe,
            Jid participant
    ) {
        var action = new DeleteMessageForMeActionBuilder()
                .deleteMedia(deleteMedia)
                .messageTimestamp(messageTimestamp)
                .build();

        var value = new SyncActionValueBuilder()
                .timestamp(timestamp)
                .deleteMessageForMeAction(action)
                .build();

        var fromMeStr = fromMe ? "1" : "0";
        var participantStr = participant != null && !fromMe
                ? participant.toString()
                : "0";

        var index = JSON.toJSONString(List.of(
                DeleteMessageForMeAction.ACTION_NAME,
                remoteJid.toString(),
                id,
                fromMeStr,
                participantStr
        ));

        var mutation = new DecryptedMutation.Trusted(
                index,
                value,
                SyncdOperation.SET,
                timestamp,
                DeleteMessageForMeAction.ACTION_VERSION
        );
        return new SyncPendingMutation(mutation, 0);
    }

    /**
     * Builds pending mutations for deleting multiple messages for the current user.
     *
     * <p>Per WhatsApp Web {@code WAWebDeleteMessageForMeSync.getDeleteForMeMutations}:
     * iterates over each message, resolves its chat JID for the mutation index,
     * determines the sender, and delegates to
     * {@link #buildDeleteForMeMutation(Instant, boolean, Instant, Jid, String, boolean, Jid)}.
     *
     * <p>In Cobalt, the caller provides pre-resolved {@link MessageKey} instances
     * and the chat JID rather than raw message models, since Cobalt does not have
     * the WA Web message model accessors ({@code getSender}, {@code getT},
     * {@code getIsGroupMsg}).
     *
     * @param keys              the message keys to delete
     * @param deleteMedia       whether to also delete associated media
     * @param messageTimestamps the original message timestamps, parallel to {@code keys}
     * @param isGroupMessages   whether each message is a group message, parallel to {@code keys}
     * @return the list of pending mutations for all messages
     */
    @WhatsAppWebExport(moduleName = "WAWebDeleteMessageForMeSync", exports = "getDeleteForMeMutations", adaptation = WhatsAppAdaptation.ADAPTED)
    public List<SyncPendingMutation> getDeleteForMeMutations(
            List<MessageKey> keys,
            boolean deleteMedia,
            List<Instant> messageTimestamps,
            List<Boolean> isGroupMessages
    ) {
        var now = Instant.now();
        var results = new ArrayList<SyncPendingMutation>(keys.size());
        for (var i = 0; i < keys.size(); i++) {
            var key = keys.get(i);
            var messageTimestamp = messageTimestamps.get(i);
            var isGroup = isGroupMessages.get(i);

            var senderJid = key.senderJid().map(Jid::toUserJid).orElse(null); // ADAPTED: WAWebDeleteMessageForMeSync.getDeleteForMeMutations — getSender + widToUserJid

            var participant = isGroup && !key.fromMe() ? senderJid : null;

            // ADAPTED: Cobalt uses the message's parentJid directly
            var remoteJid = key.parentJid().orElse(null);
            if (remoteJid == null) {
                continue; // ADAPTED: defensive null check — WA Web would throw via getChatJidMutationIndexForChat
            }

            results.add(buildDeleteForMeMutation(
                    now,
                    deleteMedia,
                    messageTimestamp,
                    remoteJid,
                    key.id().orElse(""),
                    key.fromMe(),
                    participant
            ));
        }
        return results;
    }
}
