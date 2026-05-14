package com.github.auties00.cobalt.sync.factory;

import com.alibaba.fastjson2.JSON;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.sync.SyncActionMessageRange;
import com.github.auties00.cobalt.model.sync.SyncActionValueBuilder;
import com.github.auties00.cobalt.model.sync.action.chat.DeleteChatAction;
import com.github.auties00.cobalt.model.sync.action.chat.DeleteChatActionBuilder;
import com.github.auties00.cobalt.model.sync.data.SyncdOperation;
import com.github.auties00.cobalt.sync.SyncPendingMutation;
import com.github.auties00.cobalt.sync.crypto.DecryptedMutation;

import java.time.Instant;
import java.util.List;

/**
 * Builds outgoing delete-chat sync mutations.
 *
 * <p>Mirrors the {@code getDeleteChatMutation} export of WhatsApp Web's
 * {@code WAWebDeleteChatSync} module. The factory is the outgoing-mutation
 * counterpart of
 * {@link com.github.auties00.cobalt.sync.handler.DeleteChatHandler}.
 */
public final class DeleteChatMutationFactory {
    /**
     * Constructs a delete-chat mutation factory.
     */
    public DeleteChatMutationFactory() {

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
     * ({@code MdSyncdDogfoodingFeatureUsageWamEvent}) is performed at the caller
     * ({@code WhatsAppClient.deleteChat}) since this method has no
     * {@link com.github.auties00.cobalt.wam.WamService} handle.
     *
     * @param timestamp        the mutation timestamp
     * @param chatJid          the JID of the chat to delete
     * @param deleteMediaFiles whether media files should be deleted
     * @param messageRange     the message range covering the messages to
     *                         delete; may be {@code null} when the chat has
     *                         no messages and the caller wants a full delete
     * @return the pending mutation for the delete-chat action
     */
    @WhatsAppWebExport(moduleName = "WAWebDeleteChatSync", exports = {"getDeleteChatMutation", "buildDeleteChatMutation", "buildDeleteChatIndexArgs"}, adaptation = WhatsAppAdaptation.ADAPTED)
    public SyncPendingMutation getDeleteChatMutation(
            Instant timestamp,
            Jid chatJid,
            boolean deleteMediaFiles,
            SyncActionMessageRange messageRange
    ) {
        var actionBuilder = new DeleteChatActionBuilder();
        if (messageRange != null) {
            actionBuilder.messageRange(messageRange);
        }
        var action = actionBuilder.build();
        var value = new SyncActionValueBuilder()
                .timestamp(timestamp)
                .deleteChatAction(action)
                .build();
        var index = JSON.toJSONString(List.of(
                DeleteChatAction.ACTION_NAME,
                chatJid.toString(),
                deleteMediaFiles ? "1" : "0"
        ));
        var mutation = new DecryptedMutation.Trusted(
                index,
                value,
                SyncdOperation.SET,
                timestamp,
                DeleteChatAction.ACTION_VERSION
        );
        return new SyncPendingMutation(mutation, 0); // ADAPTED: WA Web returns the raw mutation object; Cobalt wraps it in SyncPendingMutation for the outgoing queue
    }
}
