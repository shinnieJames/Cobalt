package com.github.auties00.cobalt.sync.factory;

import com.alibaba.fastjson2.JSON;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.sync.SyncActionMessageRange;
import com.github.auties00.cobalt.model.sync.SyncActionValueBuilder;
import com.github.auties00.cobalt.model.sync.action.chat.LockChatAction;
import com.github.auties00.cobalt.model.sync.action.chat.LockChatActionBuilder;
import com.github.auties00.cobalt.model.sync.data.SyncdOperation;
import com.github.auties00.cobalt.sync.SyncPendingMutation;
import com.github.auties00.cobalt.sync.crypto.DecryptedMutation;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Builds outgoing lock-chat sync mutations.
 *
 * <p>Mirrors the {@code getChatLockMutation} and {@code sendLockMutation}
 * exports of WhatsApp Web's {@code WAWebLockChatSync} module. The factory is
 * the outgoing-mutation counterpart of
 * {@link com.github.auties00.cobalt.sync.handler.LockChatHandler}.
 */
public final class LockChatMutationFactory {
    /**
     * The archive-chat mutation factory consulted by
     * {@link #getMutationsForLock(Instant, boolean, Jid, SyncActionMessageRange)}
     * to build the companion unarchive mutation that ships alongside a lock
     * mutation.
     */
    private final ArchiveChatMutationFactory archiveChatMutationFactory;

    /**
     * The pin-chat mutation factory consulted by
     * {@link #getMutationsForLock(Instant, boolean, Jid, SyncActionMessageRange)}
     * to build the companion unpin mutation that ships alongside a lock
     * mutation.
     */
    private final PinChatMutationFactory pinChatMutationFactory;

    /**
     * Constructs a lock-chat mutation factory bound to the given companion
     * factories.
     *
     * @param archiveChatMutationFactory the archive factory used to build the
     *                                   companion unarchive mutation
     * @param pinChatMutationFactory     the pin factory used to build the
     *                                   companion unpin mutation
     */
    public LockChatMutationFactory(ArchiveChatMutationFactory archiveChatMutationFactory, PinChatMutationFactory pinChatMutationFactory) {
        this.archiveChatMutationFactory = archiveChatMutationFactory;
        this.pinChatMutationFactory = pinChatMutationFactory;
    }

    /**
     * Builds a pending mutation that locks or unlocks a chat.
     *
     * <p>Per WhatsApp Web {@code WAWebLockChatSync.getChatLockMutation}:
     * <pre>{@code
     * getChatLockMutation(timestamp, locked, chatJid) {
     *   return buildPendingMutation({
     *     collection: this.collectionName,
     *     indexArgs: [yield getChatJidMutationIndexForChat(chatJid, Actions.LockChat)],
     *     value: {lockChatAction: {locked}},
     *     version: this.getVersion(),
     *     operation: SyncdMutation$SyncdOperation.SET,
     *     timestamp,
     *     action: this.getAction()
     *   });
     * }
     * }</pre>
     *
     * <p>The {@code indexArgs} list resolves the chat JID through
     * {@code getChatJidMutationIndexForChat}, which in WA Web swaps a PN for
     * its paired LID when LID1x1 migration is active. Cobalt does not yet
     * track the outgoing-mutation LID/PN swap at this layer, so the supplied
     * {@code chatJid} is used verbatim. Callers that need LID-aware indexing
     * should resolve the index JID before invoking this method.
     *
     * @param timestamp the mutation timestamp
     * @param locked    whether the chat should be locked
     * @param chatJid   the JID of the chat to lock or unlock
     * @return the pending mutation for the lock action
     */
    @WhatsAppWebExport(moduleName = "WAWebLockChatSync", exports = "getChatLockMutation", adaptation = WhatsAppAdaptation.ADAPTED)
    public SyncPendingMutation getChatLockMutation(Instant timestamp, boolean locked, Jid chatJid) {
        var action = new LockChatActionBuilder()
                .locked(locked)
                .build();
        var value = new SyncActionValueBuilder()
                .timestamp(timestamp)
                .lockChatAction(action)
                .build();
        var index = JSON.toJSONString(List.of(LockChatAction.ACTION_NAME, chatJid.toString()));
        var trusted = new DecryptedMutation.Trusted(
                index,
                value,
                SyncdOperation.SET,
                timestamp,
                LockChatAction.ACTION_VERSION
        );
        return new SyncPendingMutation(trusted, 0); // ADAPTED: WA Web returns the raw mutation object; Cobalt wraps it in SyncPendingMutation for the outgoing queue
    }

    /**
     * Builds the set of pending mutations needed to lock or unlock a chat.
     *
     * <p>Per WhatsApp Web {@code WAWebLockChatSync.sendLockMutation}:
     * <pre>{@code
     * sendLockMutation(chatJid, {isLocked}) {
     *   var i = unixTimeMs();
     *   var l = [];
     *   if (isLocked) {
     *     l.push(WAWebArchiveChatSync.getArchiveChatMutation(i, false, chatJid),
     *            PinChatSync.getPinMutation(i, false, chatJid));
     *   }
     *   l.push(this.getChatLockMutation(i, isLocked, chatJid));
     *   yield WAWebSyncdCoreApi.lockForSync([], yield Promise.all(l), () => Promise.resolve());
     * }
     * }</pre>
     *
     * <p>In WA Web the three mutations are built concurrently and committed in
     * a single {@code lockForSync} transaction. Cobalt splits the "build the
     * mutation set" step from the "commit to the pending queue" step so that
     * callers (for example the outgoing send pipeline) can batch multiple
     * side effects before flushing.
     *
     * <p>The archive and pin mutations are only appended when locking. The
     * archive unset requires a {@code messageRange} which must be supplied by
     * the caller because Cobalt does not yet maintain active message ranges
     * (a browser-specific IndexedDB concern). When {@code messageRange} is
     * {@code null} the archive mutation is built with an absent range, which
     * matches how other callers of
     * {@link ArchiveChatMutationFactory#getArchiveChatMutation} behave when the
     * range is unavailable.
     *
     * @param timestamp    the mutation timestamp
     * @param locked       whether the chat should be locked
     * @param chatJid      the JID of the chat to lock or unlock
     * @param messageRange the message range for the paired archive mutation,
     *                     ignored when {@code locked} is {@code false}; may be
     *                     {@code null} when unavailable
     * @return the list of pending mutations for the lock operation, in the
     *         order WA Web would submit them
     */
    @WhatsAppWebExport(moduleName = "WAWebLockChatSync", exports = "sendLockMutation", adaptation = WhatsAppAdaptation.ADAPTED)
    public List<SyncPendingMutation> getMutationsForLock(
            Instant timestamp,
            boolean locked,
            Jid chatJid,
            SyncActionMessageRange messageRange
    ) {
        var mutations = new ArrayList<SyncPendingMutation>();
        if (locked) {
            mutations.add(archiveChatMutationFactory.getArchiveChatMutation(timestamp, false, chatJid, messageRange));
            mutations.add(pinChatMutationFactory.getPinMutation(timestamp, false, chatJid));
        }
        mutations.add(getChatLockMutation(timestamp, locked, chatJid));
        return mutations; // ADAPTED: WA Web yields Promise.all(l) and hands the result to lockForSync; Cobalt returns the list so the caller controls the commit step
    }
}
