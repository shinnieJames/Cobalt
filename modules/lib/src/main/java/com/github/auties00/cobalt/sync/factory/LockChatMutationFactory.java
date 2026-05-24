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
 * @apiNote
 * Drives the chat-lock toggle in the chat overflow menu; locking also
 * triggers an unarchive and unpin so the locked chat moves to the
 * dedicated locked-chats folder. Mutations produced here are consumed on
 * receiving devices by
 * {@link com.github.auties00.cobalt.sync.handler.LockChatHandler}.
 *
 * @implNote
 * This implementation mirrors {@code WAWebLockChatSync.getChatLockMutation}
 * and {@code WAWebLockChatSync.sendLockMutation}; the latter is split into
 * a "build the mutation set" step (this factory) and a "commit to the
 * pending queue" step (the caller) so that the outgoing send pipeline can
 * batch multiple side effects before flushing rather than going through
 * WA Web's {@code lockForSync} transaction.
 */
public final class LockChatMutationFactory {
    /**
     * The archive-chat mutation factory consulted by
     * {@link #getMutationsForLock(Instant, boolean, Jid, SyncActionMessageRange)}
     * to build the companion unarchive mutation that ships alongside a
     * lock mutation.
     *
     * @apiNote
     * Used only on the locking path; unlocking emits a single lock-only
     * mutation and never touches the archive collection.
     */
    private final ArchiveChatMutationFactory archiveChatMutationFactory;

    /**
     * The pin-chat mutation factory consulted by
     * {@link #getMutationsForLock(Instant, boolean, Jid, SyncActionMessageRange)}
     * to build the companion unpin mutation that ships alongside a lock
     * mutation.
     *
     * @apiNote
     * Used only on the locking path; unlocking emits a single lock-only
     * mutation and never touches the pin collection.
     */
    private final PinChatMutationFactory pinChatMutationFactory;

    /**
     * Constructs a lock-chat mutation factory bound to the given companion
     * factories.
     *
     * @apiNote
     * Required by the dependency-injection container; the archive and pin
     * factories are co-located in the same package and instantiated once
     * per client.
     *
     * @param archiveChatMutationFactory the archive factory used to build
     *                                   the companion unarchive mutation
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
     * @apiNote
     * Invoked from {@link #getMutationsForLock(Instant, boolean, Jid, SyncActionMessageRange)}
     * and from any caller that already knows the archive and pin status of
     * the chat. The mutation is consumed on receiving devices via
     * {@link com.github.auties00.cobalt.sync.handler.LockChatHandler},
     * which calls {@code setChatAsLocked} or {@code setChatAsUnlocked} on
     * the resolved chat.
     *
     * @implNote
     * This implementation passes the supplied {@code chatJid} verbatim
     * into the index. WA Web's {@code getChatJidMutationIndexForChat}
     * would swap a PN for its paired LID when LID1x1 migration is active;
     * Cobalt does not yet track the outgoing-mutation LID/PN swap at this
     * layer, so callers that need LID-aware indexing must resolve the
     * index JID before invoking this method.
     *
     * @param timestamp the mutation timestamp
     * @param locked    {@code true} to lock the chat, {@code false} to
     *                  unlock
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
        return new SyncPendingMutation(trusted, 0);
    }

    /**
     * Builds the set of pending mutations needed to lock or unlock a chat.
     *
     * @apiNote
     * Drives the chat-lock toggle. When locking, the returned list
     * additionally carries an unarchive mutation (so the chat moves out
     * of the archive folder) and an unpin mutation (so it moves out of
     * the pinned area) so the chat appears in the locked-chats folder
     * cleanly on every linked device. When unlocking, only the lock
     * mutation is emitted; the chat stays wherever it was before locking.
     *
     * @implNote
     * This implementation splits the "build the mutation set" step from
     * the "commit to the pending queue" step. WA Web builds the three
     * mutations concurrently and commits them in a single
     * {@code lockForSync} transaction; Cobalt returns the list so the
     * caller controls the commit step, allowing the outgoing send
     * pipeline to batch multiple side effects before flushing. The
     * archive unset requires a {@code messageRange} that the caller
     * supplies because Cobalt does not maintain active message ranges,
     * which is a browser-specific IndexedDB concern; when
     * {@code messageRange} is {@code null} the archive mutation is built
     * with an absent range, matching how other callers of
     * {@link ArchiveChatMutationFactory#getArchiveChatMutation} behave
     * when the range is unavailable.
     *
     * @param timestamp    the mutation timestamp shared across every
     *                     mutation in the returned list
     * @param locked       {@code true} to lock the chat, {@code false}
     *                     to unlock
     * @param chatJid      the JID of the chat to lock or unlock
     * @param messageRange the message range for the paired archive
     *                     mutation, ignored when {@code locked} is
     *                     {@code false}; may be {@code null} when
     *                     unavailable
     * @return the list of pending mutations for the lock operation, in
     *         the order WA Web would submit them (unarchive, unpin, lock)
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
        return mutations;
    }
}
