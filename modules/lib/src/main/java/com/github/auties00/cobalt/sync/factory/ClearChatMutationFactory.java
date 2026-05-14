package com.github.auties00.cobalt.sync.factory;

import com.alibaba.fastjson2.JSON;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.sync.SyncActionMessageRange;
import com.github.auties00.cobalt.model.sync.SyncActionValueBuilder;
import com.github.auties00.cobalt.model.sync.action.chat.ClearChatAction;
import com.github.auties00.cobalt.model.sync.action.chat.ClearChatActionBuilder;
import com.github.auties00.cobalt.model.sync.data.SyncdOperation;
import com.github.auties00.cobalt.sync.SyncPendingMutation;
import com.github.auties00.cobalt.sync.crypto.DecryptedMutation;

import java.time.Instant;
import java.util.List;

/**
 * Builds outgoing clear-chat sync mutations.
 *
 * <p>Mirrors the {@code getClearChatMutation} export of WhatsApp Web's
 * {@code WAWebClearChatSync} module. The factory is the outgoing-mutation
 * counterpart of
 * {@link com.github.auties00.cobalt.sync.handler.ClearChatHandler}.
 */
public final class ClearChatMutationFactory {
    /**
     * Constructs a clear-chat mutation factory.
     */
    public ClearChatMutationFactory() {

    }

    /**
     * Builds a pending mutation that clears a chat's messages.
     *
     * <p>Per WhatsApp Web {@code WAWebClearChatSync.getClearChatMutation}:
     * <pre>{@code
     * getClearChatMutation(timestamp, chatWid, deleteStarred, messageRange, skipLidLookup) {
     *   var indexJid = skipLidLookup ? chatWid.toString()
     *                                : yield getChatJidMutationIndexForChat(chatWid, Actions.ClearChat);
     *   var forwardRange = yield constructForwardMovingMessageRange(chatWid, indexJid);
     *   var indexArgs = [indexJid, deleteStarred ? "1" : "0", deleteMedia ? "1" : "0"];
     *   // merges with any existing pending ClearChat mutation for the same index
     *   return buildPendingMutation({...});
     * }
     * }</pre>
     *
     * <p>The index format is {@code ["clearChat", chatJid, deleteStarred, deleteMedia]}.
     *
     * <p>In Cobalt, the caller supplies the message range because Cobalt does
     * not maintain the active-message-range infrastructure (browser-specific
     * IndexedDB concern). A {@code null} range is permitted and will result in
     * a mutation without a range. The WAM telemetry commit
     * ({@code MdSyncdDogfoodingFeatureUsageWamEvent}) is performed at the caller
     * ({@code WhatsAppClient.clearChat}) since this method has no
     * {@link com.github.auties00.cobalt.wam.WamService} handle.
     *
     * @param timestamp     the mutation timestamp
     * @param chatJid       the JID of the chat to clear
     * @param deleteStarred whether starred messages should also be deleted
     * @param deleteMedia   whether media files should be deleted (outgoing
     *                      flag written verbatim per {@code $ClearChatSync$p_3})
     * @param messageRange  the message range covering the messages to clear;
     *                      may be {@code null}
     * @return the pending mutation for the clear-chat action
     */
    @WhatsAppWebExport(moduleName = "WAWebClearChatSync", exports = {"getClearChatMutation", "$ClearChatSync$p_3"}, adaptation = WhatsAppAdaptation.ADAPTED)
    public SyncPendingMutation getClearChatMutation(
            Instant timestamp,
            Jid chatJid,
            boolean deleteStarred,
            boolean deleteMedia,
            SyncActionMessageRange messageRange
    ) {
        var actionBuilder = new ClearChatActionBuilder();
        if (messageRange != null) {
            actionBuilder.messageRange(messageRange);
        }
        var action = actionBuilder.build();
        var value = new SyncActionValueBuilder()
                .timestamp(timestamp)
                .clearChatAction(action)
                .build();
        // deleteStarred: "1" = delete, "0" = keep; deleteMedia: "1" = keep, "0" = delete (per $p_2 -> s==="1", d==="0")
        var index = JSON.toJSONString(List.of(
                ClearChatAction.ACTION_NAME,
                chatJid.toString(),
                deleteStarred ? "1" : "0",
                deleteMedia ? "1" : "0"
        ));
        var mutation = new DecryptedMutation.Trusted(
                index,
                value,
                SyncdOperation.SET,
                timestamp,
                ClearChatAction.ACTION_VERSION
        );
        return new SyncPendingMutation(mutation, 0); // ADAPTED: WA Web returns the raw mutation object; Cobalt wraps it in SyncPendingMutation for the outgoing queue
    }
}
