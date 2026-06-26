package com.github.auties00.cobalt.sync.factory;

import com.alibaba.fastjson2.JSON;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.sync.action.SyncActionValueBuilder;
import com.github.auties00.cobalt.model.sync.action.SyncActionMessageRange;
import com.github.auties00.cobalt.model.sync.action.contact.PinAction;
import com.github.auties00.cobalt.model.sync.action.contact.PinActionBuilder;
import com.github.auties00.cobalt.model.sync.data.SyncdOperation;
import com.github.auties00.cobalt.sync.SyncPendingMutation;
import com.github.auties00.cobalt.sync.crypto.DecryptedMutation;

import java.time.Instant;
import java.util.List;

/**
 * Builds outgoing pin-chat sync mutations.
 *
 * <p>Backs the chat-list pin gesture and the newsletter pin toggle in the Newsletters surface.
 * Mutations produced here are consumed on receiving devices by
 * {@link com.github.auties00.cobalt.sync.handler.PinChatHandler}, which applies the pin update to
 * the chat table and, when locked, runs the four-chat-limit eviction. This factory is also used
 * internally by {@link LockChatMutationFactory} and {@link ArchiveChatMutationFactory} to attach a
 * companion unpin when an archive or lock takes effect.
 *
 * @implNote
 * This implementation mirrors {@code WAWebPinChatSync.getPinMutation}. Receiver-side conflict
 * resolution and the {@code WAWebMdSyncdDogfoodingFeatureUsageWamEvent} telemetry emitted on
 * unpinning the fourth chat are handler-side concerns and do not surface here.
 */
public final class PinChatMutationFactory {
    /**
     * Constructs a pin-chat mutation factory.
     *
     * <p>The factory keeps no state, so a single instance is sufficient per client.
     */
    public PinChatMutationFactory() {

    }

    /**
     * Builds a pending mutation that pins or unpins a chat or newsletter.
     *
     * <p>Called from the public pin setter and from
     * {@link LockChatMutationFactory#getMutationsForLock(Instant, boolean, Jid, SyncActionMessageRange)}.
     * Newsletters are supported on the same builder because WA Web routes them through the same
     * apply path after detecting a newsletter JID.
     *
     * @implNote
     * This implementation passes the supplied {@code chatJid} verbatim into the index. WA Web's
     * {@code getChatJidMutationIndexForChat} would swap a PN for its paired LID under LID1x1
     * migration, which Cobalt does not maintain at this layer. The mutation is routed through the
     * {@code RegularLow} collection alongside the other chat-scoped sync actions and uses
     * {@code SET} as the operation.
     *
     * @param timestamp the mutation timestamp recorded on both the outer mutation and the inner
     *                  {@code SyncActionValue}
     * @param pinned    {@code true} to pin the chat, {@code false} to unpin
     * @param chatJid   the JID of the chat or newsletter
     * @return the pending mutation for the pin action
     */
    public SyncPendingMutation getPinMutation(Instant timestamp, boolean pinned, Jid chatJid) {
        var pinAction = new PinActionBuilder()
                .pinned(pinned)
                .build();
        var value = new SyncActionValueBuilder()
                .timestamp(timestamp)
                .pinAction(pinAction)
                .build();
        var index = JSON.toJSONString(List.of(PinAction.ACTION_NAME, chatJid.toString()));
        var mutation = new DecryptedMutation.Trusted(
                index,
                value,
                SyncdOperation.SET,
                timestamp,
                PinAction.ACTION_VERSION
        );
        return new SyncPendingMutation(mutation, 0);
    }
}
