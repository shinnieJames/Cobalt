package com.github.auties00.cobalt.calls2.core.control;

import com.github.auties00.cobalt.calls2.VideoStreamState;
import com.github.auties00.cobalt.calls2.core.CallEvent;
import com.github.auties00.cobalt.calls2.core.CallEventType;
import com.github.auties00.cobalt.calls2.core.LiveCallEventBus;
import com.github.auties00.cobalt.model.call.datachannel.ReactionInfo;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * Translates a per-call {@link ControlCallEvent} into the host-facing {@link CallEvent} and publishes it
 * onto the shared {@link LiveCallEventBus}.
 *
 * <p>The in-call controllers emit their changes as {@link ControlCallEvent} records, a finer-grained
 * control-surface payload that carries only the participant and state a control operation changed and not
 * the owning call identifier. This adapter is the seam between that control vocabulary and the host event
 * bus: it closes over the call identifier the control records omit and, on each {@link #emit(ControlCallEvent)},
 * maps the control event onto the matching typed {@link CallEvent} family before handing it to the bus,
 * which gates it through its should-emit allow-set and fans the survivors out to the registered listeners.
 * One bridge is constructed per call when the call becomes answerable and is discarded when the call is torn
 * down; it reads the call's identifier on each emit rather than capturing it, so a bridge that spans a
 * call-link join (whose id changes from the all-zeros placeholder to the relay-assigned id once the join ack
 * arrives) stamps the current id rather than a stale one.
 *
 * <p>The mapping is exhaustive over the sealed {@link ControlCallEvent} hierarchy. A control event with a
 * dedicated typed {@link CallEvent} record (mute, video, peer-video-permission, screen-share, raise-hand,
 * a present reaction, and a transcript fragment) is translated to that record; every remaining control
 * event whose typed host payload is open, including the waiting-room and call-link control events whose
 * {@link CallEvent} counterparts need a {@code JoinableCallLink} a control event does not carry, is carried
 * verbatim by {@link CallEvent.Generic} so it survives the gate and is observable through the raw-event
 * egress rather than being dropped. An empty reaction (the expiry sweep) likewise maps to
 * {@link CallEvent.Generic} because {@link CallEvent.Reaction} requires a non-empty emoji.
 *
 * @implNote This implementation is the Cobalt adapter between the two event seams the native engine fuses:
 * the in-call control units of module {@code ff-tScznZ8P} raise their events through the same generic
 * dispatcher {@code wa_call_dispatch_event} (fn11072, SPEC 17.1) that the lifecycle path uses, so the native
 * code has no separate adapter; Cobalt splits the control payload ({@link ControlCallEvent}) from the
 * host-bus payload ({@link CallEvent}) and reunites them here. The {@code Generic} fallback is faithful to
 * the unrecovered payload byte layouts (CAPTURE-FINDINGS Q12): an event whose typed shape is not modeled is
 * forwarded by id rather than reconstructed, so no gated-in control event is lost.
 * @see ControlCallEvent
 * @see CallEvent
 * @see LiveCallEventBus
 */
public final class ControlEventBridge implements CallEventSink {
    /**
     * Supplies the identifier of the call this bridge stamps onto every translated {@link CallEvent}, read
     * on each {@link #emit(ControlCallEvent)} rather than captured at construction.
     *
     * <p>The control records carry no call identifier, so the bridge supplies the call's own. Reading it at
     * emit time lets a single bridge follow a call whose identifier changes over its lifetime, such as a
     * call-link join that adopts the relay-assigned id over the all-zeros placeholder once the join ack
     * arrives.
     */
    private final Supplier<String> callIdSource;

    /**
     * The shared event bus the translated host events are published onto.
     */
    private final LiveCallEventBus bus;

    /**
     * Constructs a bridge that stamps a call identifier onto the events it forwards to a bus.
     *
     * @param callId the identifier of the call whose control events this bridge translates; never
     *               {@code null}
     * @param bus    the shared event bus the translated host events are published onto; never {@code null}
     * @throws NullPointerException if {@code callId} or {@code bus} is {@code null}
     */
    public ControlEventBridge(String callId, LiveCallEventBus bus) {
        Objects.requireNonNull(callId, "callId cannot be null");
        this.callIdSource = () -> callId;
        this.bus = Objects.requireNonNull(bus, "bus cannot be null");
    }

    /**
     * Constructs a bridge that reads its call identifier from a supplier on each forwarded event.
     *
     * <p>Used where the call's identifier can change over the bridge's lifetime, such as a call-link join
     * that adopts the relay-assigned id from the join ack: the supplier reads the call's current id at emit
     * time so events stamped before and after the adoption each carry the id then in force.
     *
     * @param callIdSource supplies the current call identifier on each forwarded event; never {@code null}
     * @param bus          the shared event bus the translated host events are published onto; never
     *                     {@code null}
     * @throws NullPointerException if {@code callIdSource} or {@code bus} is {@code null}
     */
    public ControlEventBridge(Supplier<String> callIdSource, LiveCallEventBus bus) {
        this.callIdSource = Objects.requireNonNull(callIdSource, "callIdSource cannot be null");
        this.bus = Objects.requireNonNull(bus, "bus cannot be null");
    }

    /**
     * {@inheritDoc}
     *
     * @implNote This implementation switches exhaustively over the sealed {@link ControlCallEvent}
     * hierarchy, builds the matching {@link CallEvent} (stamping the call id {@link #callIdSource} supplies),
     * and publishes it through
     * {@link LiveCallEventBus#emit(CallEvent)}, which applies the should-emit gate; a control event without a
     * dedicated typed record maps to {@link CallEvent.Generic} carrying its {@link ControlCallEvent#type()}.
     * The mapping never blocks and never throws for a well-formed event, matching the {@link CallEventSink}
     * contract: the bus dispatches each listener on its own virtual thread, so the controller's emitting
     * thread returns at once.
     */
    @Override
    public void emit(ControlCallEvent event) {
        Objects.requireNonNull(event, "event cannot be null");
        var callId = callIdSource.get();
        var translated = switch (event) {
            case MuteStateChanged e ->
                    new CallEvent.MuteChanged(callId, e.participant(), e.muted(), false);
            case MuteByAnotherParticipant e ->
                    new CallEvent.MuteChanged(callId, e.requester(), true, true);
            case MuteRequestFailed ignored ->
                    new CallEvent.Generic(CallEventType.MUTE_REQUEST_FAILED, callId);
            case VideoStateChanged e -> videoEvent(callId, e);
            case PeerVideoPermissionChanged e ->
                    new CallEvent.PeerVideoPermissionChanged(callId, e.participant(), e.allowed());
            case ScreenShareEvent e ->
                    new CallEvent.ScreenShareChanged(callId, e.sharer(), mapScreenShareState(e.state()));
            case ReactionStateChanged e -> reactionEvent(callId, e);
            case RaiseHandStateChanged e ->
                    new CallEvent.RaiseHand(callId, e.participant(), e.raised());
            case CallLinkStateChanged ignored ->
                    new CallEvent.Generic(CallEventType.CALL_LINK_STATE_CHANGED, callId);
            case LinkQueryAcked ignored ->
                    new CallEvent.Generic(CallEventType.LINK_QUERY_ACKED, callId);
            case TranscriptReceived e ->
                    new CallEvent.Transcript(callId, e.participant(), e.text(),
                            e.language().orElse(null), e.sequence());
            case WaitingRoomStateChanged ignored ->
                    new CallEvent.Generic(CallEventType.WAITING_ROOM_STATE_CHANGED, callId);
            case WaitingRoomDenied ignored ->
                    new CallEvent.Generic(CallEventType.WAITING_ROOM_DENIED, callId);
            case WaitingRoomAdmitAcked ignored ->
                    new CallEvent.Generic(CallEventType.WAITING_ROOM_ADMIT_ACKED, callId);
            case WaitingRoomDenyAcked ignored ->
                    new CallEvent.Generic(CallEventType.WAITING_ROOM_DENY_ACKED, callId);
            case WaitingRoomToggleAcked ignored ->
                    new CallEvent.Generic(CallEventType.WAITING_ROOM_TOGGLE_ACKED, callId);
            case CallGridRankingChanged ignored ->
                    new CallEvent.Generic(CallEventType.CALL_GRID_RANKING_CHANGED, callId);
            case CallLinkLobbySelfStateChanged ignored ->
                    new CallEvent.Generic(CallEventType.CALL_LINK_LOBBY_SELF_STATE_CHANGED, callId);
            case PlayCallTone ignored ->
                    new CallEvent.Generic(CallEventType.PLAY_CALL_TONE, callId);
        };
        bus.emit(translated);
    }

    /**
     * Maps an inbound video-state change onto the host event family it belongs to.
     *
     * <p>A peer's {@link VideoStreamState#UPGRADE_REQUEST} or {@link VideoStreamState#UPGRADE_REQUEST_V2}
     * is the request leg of a mid-call audio-to-video upgrade and becomes a
     * {@link CallEvent.VideoUpgradeRequest}, the distinct host notification the application answers with
     * an accept or reject. Every other state (the camera on/off lifecycle, and the upgrade
     * accept/reject/cancel responses) becomes a {@link CallEvent.VideoStateChanged} reporting whether the
     * camera is on, with a self-originated change carrying no peer attribution.
     *
     * @param callId the call identifier to stamp onto the event
     * @param event  the inbound video-state change
     * @return the typed video-upgrade-request event for an inbound upgrade request, otherwise the
     *         video-state-changed event
     */
    private CallEvent videoEvent(String callId, VideoStateChanged event) {
        if (!event.self() && (event.state() == VideoStreamState.UPGRADE_REQUEST
                || event.state() == VideoStreamState.UPGRADE_REQUEST_V2)) {
            return new CallEvent.VideoUpgradeRequest(callId, event.participant());
        }
        return new CallEvent.VideoStateChanged(callId, event.self() ? null : event.participant(),
                event.state() == VideoStreamState.ENABLED);
    }

    /**
     * Maps a present reaction to a typed reaction event and an empty one to a generic event.
     *
     * <p>A control reaction arrives with a present {@link ReactionInfo} carrying the emoji and clears with an
     * empty reaction on the expiry sweep. A present reaction with a non-empty emoji becomes a typed
     * {@link CallEvent.Reaction}; an empty reaction, or one whose emoji is absent or blank, becomes a
     * {@link CallEvent.Generic} carrying {@link CallEventType#REACTION_STATE_CHANGED}, because
     * {@link CallEvent.Reaction} rejects an empty emoji and the expiry carries none.
     *
     * @param callId the call identifier to stamp onto the reaction event
     * @param event  the inbound reaction state change
     * @return the typed reaction event when a non-empty emoji is present, otherwise the generic event
     */
    private CallEvent reactionEvent(String callId, ReactionStateChanged event) {
        return event.reaction()
                .flatMap(ReactionInfo::reaction)
                .filter(emoji -> !emoji.isEmpty())
                .<CallEvent>map(emoji -> new CallEvent.Reaction(callId, event.participant(), emoji))
                .orElseGet(() -> new CallEvent.Generic(CallEventType.REACTION_STATE_CHANGED, callId));
    }

    /**
     * Maps a control-surface {@link ScreenShareState} onto the host {@link CallEvent.ScreenShareChanged.State}.
     *
     * @param state the control-surface screen-share state
     * @return the matching host screen-share state
     */
    private static CallEvent.ScreenShareChanged.State mapScreenShareState(ScreenShareState state) {
        return switch (state) {
            case STARTED -> CallEvent.ScreenShareChanged.State.STARTED;
            case STOPPED -> CallEvent.ScreenShareChanged.State.STOPPED;
            case FAILED -> CallEvent.ScreenShareChanged.State.FAILED;
        };
    }
}
