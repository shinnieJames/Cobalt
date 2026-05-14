package com.github.auties00.cobalt.sync.factory;

import com.alibaba.fastjson2.JSON;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.sync.SyncActionMessageRange;
import com.github.auties00.cobalt.model.sync.SyncActionValueBuilder;
import com.github.auties00.cobalt.model.sync.action.chat.MarkChatAsReadAction;
import com.github.auties00.cobalt.model.sync.action.chat.MarkChatAsReadActionBuilder;
import com.github.auties00.cobalt.model.sync.data.SyncdOperation;
import com.github.auties00.cobalt.sync.SyncPendingMutation;
import com.github.auties00.cobalt.sync.crypto.DecryptedMutation;

import java.time.Instant;
import java.util.List;

/**
 * Builds outgoing mark-chat-as-read sync mutations.
 *
 * <p>Mirrors the {@code getMarkChatAsReadMutation} export of WhatsApp Web's
 * {@code WAWebMarkChatAsReadSync} module. The factory is the outgoing-mutation
 * counterpart of
 * {@link com.github.auties00.cobalt.sync.handler.MarkChatAsReadHandler}.
 */
public final class MarkChatAsReadMutationFactory {
    /**
     * Constructs a mark-chat-as-read mutation factory.
     */
    public MarkChatAsReadMutationFactory() {

    }

    /**
     * Builds a pending mutation for marking a chat as read or unread.
     *
     * <p>Per WhatsApp Web {@code WAWebMarkChatAsReadSync.getMarkChatAsReadMutation}:
     * <ol>
     *   <li>Resolves the chat JID for the mutation index via
     *       {@code WAWebSyncdGetChat.getChatJidMutationIndexForChat}</li>
     *   <li>Constructs the outgoing message range via
     *       {@code WAWebMessageRangeUtils.constructMessageRange}</li>
     *   <li>Builds the pending mutation with the mark-chat-as-read action value
     *       via {@code WAWebSyncdActionUtils.buildPendingMutation}</li>
     * </ol>
     *
     * <p>In Cobalt, the message range is passed as a parameter because
     * {@code constructMessageRange} requires store infrastructure that is built
     * at a higher level.
     *
     * @param timestamp    the mutation timestamp
     * @param read         {@code true} to mark the chat as read, {@code false}
     *                     to mark it as unread
     * @param chatJid      the JID of the chat
     * @param messageRange the outgoing message range for this action
     * @return the pending mutation for the mark-chat-as-read operation
     */
    @WhatsAppWebExport(moduleName = "WAWebMarkChatAsReadSync", exports = "getMarkChatAsReadMutation", adaptation = WhatsAppAdaptation.ADAPTED)
    public SyncPendingMutation getMarkChatAsReadMutation(
            Instant timestamp,
            boolean read,
            Jid chatJid,
            SyncActionMessageRange messageRange
    ) {
        var action = new MarkChatAsReadActionBuilder()
                .read(read)
                .messageRange(messageRange)
                .build();
        var value = new SyncActionValueBuilder()
                .timestamp(timestamp)
                .markChatAsReadAction(action)
                .build();
        var index = JSON.toJSONString(List.of(MarkChatAsReadAction.ACTION_NAME, chatJid.toString()));
        var mutation = new DecryptedMutation.Trusted(
                index,
                value,
                SyncdOperation.SET,
                timestamp,
                MarkChatAsReadAction.ACTION_VERSION
        );
        return new SyncPendingMutation(mutation, 0);
    }
}
