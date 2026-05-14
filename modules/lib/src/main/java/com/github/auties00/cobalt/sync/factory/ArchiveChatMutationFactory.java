package com.github.auties00.cobalt.sync.factory;

import com.alibaba.fastjson2.JSON;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.sync.SyncActionMessageRange;
import com.github.auties00.cobalt.model.sync.SyncActionValueBuilder;
import com.github.auties00.cobalt.model.sync.action.chat.ArchiveChatAction;
import com.github.auties00.cobalt.model.sync.action.chat.ArchiveChatActionBuilder;
import com.github.auties00.cobalt.model.sync.data.SyncdOperation;
import com.github.auties00.cobalt.sync.SyncPendingMutation;
import com.github.auties00.cobalt.sync.crypto.DecryptedMutation;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Builds outgoing archive-chat sync mutations.
 *
 * <p>Mirrors the {@code getArchiveChatMutation} and
 * {@code getMutationsForArchive} exports of WhatsApp Web's
 * {@code WAWebArchiveChatSync} module. The factory is the outgoing-mutation
 * counterpart of
 * {@link com.github.auties00.cobalt.sync.handler.ArchiveChatHandler} — the
 * handler keeps the inbound {@code applyMutation}/{@code resolveConflicts}
 * pipeline while this class produces the {@link SyncPendingMutation} values
 * dispatched by {@link com.github.auties00.cobalt.client.WhatsAppClient}.
 */
public final class ArchiveChatMutationFactory {
    /**
     * The pin-chat mutation factory consulted by
     * {@link #getMutationsForArchive(Instant, boolean, Jid, SyncActionMessageRange)}
     * to build the companion unpin mutation that ships alongside an
     * archive mutation.
     */
    private final PinChatMutationFactory pinChatMutationFactory;

    /**
     * Constructs an archive-chat mutation factory bound to the given pin-chat
     * mutation factory.
     *
     * @param pinChatMutationFactory the pin-chat factory used to build the
     *                               companion unpin mutation when archiving
     */
    public ArchiveChatMutationFactory(PinChatMutationFactory pinChatMutationFactory) {
        this.pinChatMutationFactory = pinChatMutationFactory;
    }

    /**
     * Builds a pending mutation for archiving or unarchiving a chat.
     *
     * <p>Per WhatsApp Web {@code WAWebArchiveChatSync.getArchiveChatMutation}:
     * <ol>
     *   <li>Resolves the chat JID for mutation index via
     *       {@code WAWebSyncdGetChat.getChatJidMutationIndexForChat}</li>
     *   <li>Constructs the message range for the chat via
     *       {@code WAWebMessageRangeUtils.constructMessageRange}</li>
     *   <li>Builds the pending mutation with the archive action value</li>
     * </ol>
     *
     * <p>In Cobalt, the message range is passed as a parameter since
     * {@code constructMessageRange} requires store infrastructure that is
     * built at a higher level.
     *
     * @param timestamp    the mutation timestamp
     * @param archived     whether the chat should be archived
     * @param chatJid      the JID of the chat to archive
     * @param messageRange the message range for the archive action
     * @return the pending mutation for the archive action
     */
    @WhatsAppWebExport(moduleName = "WAWebArchiveChatSync", exports = "getArchiveChatMutation", adaptation = WhatsAppAdaptation.ADAPTED)
    public SyncPendingMutation getArchiveChatMutation(
            Instant timestamp,
            boolean archived,
            Jid chatJid,
            SyncActionMessageRange messageRange
    ) {
        var action = new ArchiveChatActionBuilder()
                .archived(archived)
                .messageRange(messageRange)
                .build();
        var value = new SyncActionValueBuilder()
                .timestamp(timestamp)
                .archiveChatAction(action)
                .build();
        var index = JSON.toJSONString(List.of(ArchiveChatAction.ACTION_NAME, chatJid.toString()));
        var mutation = new DecryptedMutation.Trusted(
                index,
                value,
                SyncdOperation.SET,
                timestamp,
                ArchiveChatAction.ACTION_VERSION
        );
        return new SyncPendingMutation(mutation, 0);
    }

    /**
     * Builds the set of pending mutations needed to archive or unarchive a chat.
     *
     * <p>Per WhatsApp Web {@code WAWebArchiveChatSync.getMutationsForArchive}:
     * <ul>
     *   <li>Always includes an archive mutation</li>
     *   <li>When archiving ({@code archived = true}), also includes a pin mutation
     *       with {@code pinned = false} to unpin the chat (via
     *       {@code WAWebPinChatSync.PinChatSync.getPinMutation(e, false, r)})</li>
     * </ul>
     *
     * @param timestamp    the mutation timestamp
     * @param archived     whether the chat should be archived
     * @param chatJid      the JID of the chat
     * @param messageRange the message range for the archive action
     * @return the list of pending mutations for the archive operation
     */
    @WhatsAppWebExport(moduleName = "WAWebArchiveChatSync", exports = "getMutationsForArchive", adaptation = WhatsAppAdaptation.DIRECT)
    public List<SyncPendingMutation> getMutationsForArchive(
            Instant timestamp,
            boolean archived,
            Jid chatJid,
            SyncActionMessageRange messageRange
    ) {
        var mutations = new ArrayList<SyncPendingMutation>();
        mutations.add(getArchiveChatMutation(timestamp, archived, chatJid, messageRange));
        if (archived) {
            mutations.add(pinChatMutationFactory.getPinMutation(timestamp, false, chatJid));
        }
        return mutations;
    }
}
