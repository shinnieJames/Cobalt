package com.github.auties00.cobalt.calls2.core.control;

import com.github.auties00.cobalt.calls2.net.transport.AppDataController;
import com.github.auties00.cobalt.model.call.datachannel.ReactionInfo;
import com.github.auties00.cobalt.model.call.datachannel.ReactionInfoBuilder;
import com.github.auties00.cobalt.model.jid.Jid;

import java.util.Objects;
import java.util.Optional;

/**
 * Drives the in-call reaction control: sending emoji reactions and surfacing inbound ones as events.
 *
 * <p>Reactions are transient UI overlays that travel over the call's application-data side-channel rather
 * than over signaling. {@link #sendReaction(String)} broadcasts the local user's reaction through the
 * attached {@link AppDataController#sendReaction(String)}, which retains it for
 * retransmission and clears it after a short window, and emits a {@link ReactionStateChanged} for the local
 * user so its own overlay shows. Inbound reactions, and their expiry, arrive on the
 * {@link #onReaction(Jid, Optional)} callback this controller registers with the {@link AppDataController}:
 * an arrival emits a {@link ReactionStateChanged} carrying the reaction and an expiry emits one carrying an
 * empty reaction so the host takes the overlay down.
 *
 * <p>This controller is the inbound reaction observer the {@link AppDataController} is constructed with, so
 * it must exist before that controller; the outbound send seam is therefore attached after the
 * application-data controller is built, through {@link #attach(AppDataController)}, to break the construction
 * cycle. The controller owns no timers of its own (the reaction lifetime and retransmission timers belong
 * to the {@link AppDataController}) and holds no mutable state beyond the one-time send-seam binding.
 *
 * @implNote This implementation reproduces the reaction control of the wa-voip WASM module
 * {@code ff-tScznZ8P}: an outbound reaction is sent through {@code wa_app_data_controller_send_reaction}
 * (the {@code wa.voip.reactionInfo} payload on the AppData stream) and a local event {@code 0x91}
 * ({@code ReactionStateChanged}) is emitted, with the reaction-clear timer owned by the app-data
 * controller; an inbound reaction updates the per-participant record and emits the same event, and the
 * rx-reaction-clear sweep emits it again with an empty reaction. Cobalt sources both arrival and expiry
 * from the {@link #onReaction(Jid, Optional)} callback it registers with the {@link AppDataController}, so it
 * adds only the event mapping. Reactions deliberately do NOT serialize a {@code CallInteraction}; the wire
 * layer never carries that facade.
 */
public final class ReactionController {
    /**
     * The local device JID stamped as the participant on the local user's own reaction events.
     */
    private final Jid selfJid;

    /**
     * The event sink reaction events are emitted into.
     */
    private final CallEventSink events;

    /**
     * The application-data controller carrying the outbound reaction send, attached after it is built.
     *
     * <p>Volatile because {@link #attach(AppDataController)} runs on the wiring thread while
     * {@link #sendReaction(String)} may run on a control thread; the field is written once.
     */
    private volatile AppDataController appData;

    /**
     * Constructs a reaction controller bound to the local identity and the event sink.
     *
     * <p>The outbound send seam is attached separately through {@link #attach(AppDataController)} once the
     * application-data controller this controller observes has been built.
     *
     * @param selfJid the local device JID for the local user's own reaction events; never {@code null}
     * @param events  the event sink to emit reaction events into; never {@code null}
     * @throws NullPointerException if either argument is {@code null}
     */
    public ReactionController(Jid selfJid, CallEventSink events) {
        this.selfJid = Objects.requireNonNull(selfJid, "selfJid cannot be null");
        this.events = Objects.requireNonNull(events, "events cannot be null");
    }

    /**
     * Attaches the outbound reaction send seam.
     *
     * <p>Called once, after the application-data controller this controller observes is built, to wire the
     * outbound path (the application-data controller's reaction send). Breaking the binding into
     * a separate step resolves the construction cycle between this observer and the application-data
     * controller it is passed to.
     *
     * @param appData the application-data controller carrying the reaction send; never {@code null}
     * @throws NullPointerException if {@code appData} is {@code null}
     */
    public void attach(AppDataController appData) {
        this.appData = Objects.requireNonNull(appData, "appData cannot be null");
    }

    /**
     * Broadcasts the local user's emoji reaction and emits its local state change.
     *
     * <p>Sends the reaction through the attached {@link AppDataController#sendReaction(String)}, which assigns
     * it a transaction id and retains it for retransmission, and emits a {@link ReactionStateChanged} for the
     * local user carrying the sent reaction so its own overlay appears. Returns the assigned transaction id, or
     * {@code -1} when no send seam is attached or the send was dropped.
     *
     * @param emoji the reaction emoji string to broadcast; never {@code null}
     * @return the transaction id assigned to the reaction, or {@code -1} when unsent
     * @throws NullPointerException if {@code emoji} is {@code null}
     */
    public long sendReaction(String emoji) {
        Objects.requireNonNull(emoji, "emoji cannot be null");
        var current = appData;
        if (current == null) {
            return -1;
        }
        var transactionId = current.sendReaction(emoji);
        // FIXME: when current.sendReaction(emoji) returns -1 the app-data controller dropped the reaction, yet this still
        //  emits ReactionStateChanged(self=true) so the local overlay shows a reaction that was never sent;
        //  the intended fix is to return -1 without emitting on the dropped-send path, but whether WA emits
        //  the local 0x91 event regardless of send-drop is unconfirmed (the drop path differs from the
        //  seam-absent path), so the emit is left in place until confirmed against WA.
        var reaction = new ReactionInfoBuilder()
                .transactionId(transactionId)
                .reaction(emoji)
                .build();
        events.emit(new ReactionStateChanged(selfJid, Optional.of(reaction), true));
        return transactionId;
    }

    /**
     * Surfaces an inbound reaction arrival or expiry from the application-data layer as an event.
     *
     * <p>Invoked by the {@link AppDataController} with a present reaction when one arrives from a
     * participant and an empty reaction when that participant's reaction is expired by the sweep. Emits a
     * {@link ReactionStateChanged} carrying the participant and the reaction; the {@code self} flag is set
     * only when the participant is the local device.
     *
     * @param participant the device JID whose reaction changed; never {@code null}
     * @param reaction    the arrived reaction, or empty when the reaction expired
     */
    public void onReaction(Jid participant, Optional<ReactionInfo> reaction) {
        events.emit(new ReactionStateChanged(participant, reaction, participant.equals(selfJid)));
    }
}
