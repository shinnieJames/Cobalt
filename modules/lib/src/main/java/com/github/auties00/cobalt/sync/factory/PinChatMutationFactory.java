package com.github.auties00.cobalt.sync.factory;

import com.alibaba.fastjson2.JSON;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.sync.SyncActionValueBuilder;
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
 * <p>Mirrors the {@code getPinMutation} export of WhatsApp Web's
 * {@code WAWebPinChatSync} module. The factory is the outgoing-mutation
 * counterpart of
 * {@link com.github.auties00.cobalt.sync.handler.PinChatHandler} — the handler
 * keeps the inbound {@code applyMutation} pipeline while this class produces
 * the {@link SyncPendingMutation} values dispatched by
 * {@link com.github.auties00.cobalt.client.WhatsAppClient}.
 */
public final class PinChatMutationFactory {
    /**
     * Constructs a pin-chat mutation factory.
     */
    public PinChatMutationFactory() {

    }

    /**
     * Builds a pending mutation that pins or unpins a chat or newsletter.
     *
     * <p>Per WhatsApp Web {@code WAWebPinChatSync.getPinMutation}: constructs a
     * {@code pinAction} value, computes the chat-jid mutation index via
     * {@code WAWebSyncdGetChat.getChatJidMutationIndexForChat}, and delegates to
     * {@code WAWebSyncdActionUtils.buildPendingMutation} with the
     * {@code RegularLow} collection, the action version, the {@code SET}
     * operation, the timestamp, and the {@code Pin} action.
     *
     * <p>In Cobalt, the chat-jid mutation index is computed as
     * {@code chatJid.toString()} directly because Cobalt does not maintain the
     * LID-migration accountLid lookup that {@code getChatJidMutationIndexForChat}
     * performs in WA Web.
     *
     * @param timestamp the mutation timestamp
     * @param pinned    whether the chat should be pinned ({@code true}) or unpinned ({@code false})
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
        // ADAPTED: Cobalt does not implement the LID 1:1 migration accountLid path,
        // so the chat-jid mutation index resolves to the JID's canonical string form.
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
