package com.github.auties00.cobalt.calls2.core.control;

import com.github.auties00.cobalt.calls2.signaling.RaiseHandStanza;
import com.github.auties00.cobalt.model.jid.Jid;

import java.util.Objects;

/**
 * Drives the in-call raise-hand control: raising or lowering the local user's hand and surfacing peers'.
 *
 * <p>In a group call a participant can raise a hand to ask to speak. {@link #setHandRaised(boolean)} sends
 * a {@code raise_hand} action carrying the new state, broadcast in a group, emits a
 * {@link RaiseHandStateChanged} for the local user, and feeds the local hand state into the grid-ranking
 * comparator so the local user sorts first. Inbound peer reports are delivered through
 * {@link #onPeerHandRaised(Jid, boolean)}, which emits a {@link RaiseHandStateChanged} for the peer and
 * feeds its hand state into the ranking too.
 *
 * <p>Unlike reactions, raise-hand rides the signaling plane rather than the application-data channel, so
 * it dispatches through the {@link CallSignalingSender}. The controller is bound to one call's identity,
 * its signaling and event seams, and the {@link SpeakerRankingService} it updates at construction; it owns
 * no timers and holds only the local hand state in a volatile field.
 *
 * @implNote This implementation reproduces the raise-hand control of the wa-voip WASM module
 * {@code ff-tScznZ8P}: {@code serialize_raise_hand} sends the {@code <raise_hand>} element (carried in the
 * {@code notify} container {@code 0x68}, no taxonomy ordinal) with {@code raise-hand-state} {@code 1} or
 * {@code 0}, and the resulting state emits event {@code 0x94} ({@code RaiseHandStateChanged}) and feeds the
 * grid-ranking comparator (hand-raised participants sort first). The single local-hand-raised flag is held
 * in a volatile field rather than behind the info-mutex, per the threading design.
 */
public final class RaiseHandController {
    /**
     * The call identity this controller stamps onto its raise-hand actions.
     */
    private final CallControlContext context;

    /**
     * The signaling egress raise-hand actions are sent through.
     */
    private final CallSignalingSender sender;

    /**
     * The event sink raise-hand events are emitted into.
     */
    private final CallEventSink events;

    /**
     * The grid-ranking service the hand state is fed into.
     */
    private final SpeakerRankingService ranking;

    /**
     * Whether the local user currently has a hand raised.
     *
     * <p>Volatile so {@link #setHandRaised(boolean)} can store it and {@link #isHandRaised()} can read it
     * without a lock: the field is a lone flag with no compound read-modify-write.
     */
    private volatile boolean handRaised;

    /**
     * Constructs a raise-hand controller bound to a call, its seams, and the ranking service.
     *
     * @param context the call identity to stamp onto raise-hand actions; never {@code null}
     * @param sender  the signaling egress to send raise-hand actions through; never {@code null}
     * @param events  the event sink to emit raise-hand events into; never {@code null}
     * @param ranking the grid-ranking service to feed hand state into; never {@code null}
     * @throws NullPointerException if any argument is {@code null}
     */
    public RaiseHandController(CallControlContext context, CallSignalingSender sender, CallEventSink events,
                               SpeakerRankingService ranking) {
        this.context = Objects.requireNonNull(context, "context cannot be null");
        this.sender = Objects.requireNonNull(sender, "sender cannot be null");
        this.events = Objects.requireNonNull(events, "events cannot be null");
        this.ranking = Objects.requireNonNull(ranking, "ranking cannot be null");
    }

    /**
     * Raises or lowers the local user's hand, broadcasting the change.
     *
     * <p>Records the new hand state, sends a {@code raise_hand} action carrying it, emits a
     * {@link RaiseHandStateChanged} for the local user, and feeds the state into the grid-ranking service.
     *
     * @param raised {@code true} to raise the hand, {@code false} to lower it
     */
    public void setHandRaised(boolean raised) {
        this.handRaised = raised;
        sender.send(new RaiseHandStanza(context.callId(), context.callCreator(), raised, context.group()));
        events.emit(new RaiseHandStateChanged(context.selfJid(), raised, true));
        ranking.setHandRaised(context.selfJid(), raised);
    }

    /**
     * Returns whether the local user currently has a hand raised.
     *
     * @return {@code true} when the local user's hand is raised
     */
    public boolean isHandRaised() {
        return handRaised;
    }

    /**
     * Handles an inbound peer raise-hand report, emitting the peer's change and updating the ranking.
     *
     * <p>Emits a {@link RaiseHandStateChanged} for the reporting peer and feeds the peer's hand state into
     * the grid-ranking service; it does not change the local hand state.
     *
     * @param peer   the device JID of the reporting peer; never {@code null}
     * @param raised {@code true} when the peer raised a hand, {@code false} when lowered
     * @throws NullPointerException if {@code peer} is {@code null}
     */
    public void onPeerHandRaised(Jid peer, boolean raised) {
        Objects.requireNonNull(peer, "peer cannot be null");
        events.emit(new RaiseHandStateChanged(peer, raised, false));
        ranking.setHandRaised(peer, raised);
    }
}
