package com.github.auties00.cobalt.calls2.core;

import com.github.auties00.cobalt.client.linked.LinkedWhatsAppClient;
import com.github.auties00.cobalt.listener.WhatsAppListener;
import com.github.auties00.cobalt.model.call.CallInteraction;
import com.github.auties00.cobalt.listener.linked.LinkedCallEndedListener;
import com.github.auties00.cobalt.listener.linked.LinkedCallInteractionListener;
import com.github.auties00.cobalt.listener.linked.LinkedCallLinkAdmittedListener;
import com.github.auties00.cobalt.listener.linked.LinkedCallLinkDeniedListener;
import com.github.auties00.cobalt.listener.linked.LinkedCallLinkLobbyJoinRequestListener;
import com.github.auties00.cobalt.listener.linked.LinkedCallListener;
import com.github.auties00.cobalt.listener.linked.LinkedCallMuteChangedListener;
import com.github.auties00.cobalt.listener.linked.LinkedCallParticipantsChangedListener;
import com.github.auties00.cobalt.listener.linked.LinkedCallPeerStateChangedListener;
import com.github.auties00.cobalt.listener.linked.LinkedCallPreacceptListener;
import com.github.auties00.cobalt.listener.linked.LinkedCallVideoStateChangedListener;
import com.github.auties00.cobalt.listener.linked.LinkedCallVideoUpgradeRequestListener;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

/**
 * The production {@link CallEventBus}: gates host-facing events and fans them out to listeners.
 *
 * <p>This implementation collapses the native engine's generic event dispatcher and its 127-slot
 * ring into two pure-Java steps. {@link #shouldEmit(CallEventType)} is the should-emit gate: it admits
 * only the recovered host-facing event ids and drops every internal lifecycle and diagnostic id.
 * {@link #emit(CallEvent)} runs that gate and then, for each interested registered listener, maps the
 * typed {@link CallEvent} onto the matching {@code LinkedCall*Listener} callback and dispatches it on
 * its own virtual thread, so a slow or throwing listener can neither stall the call engine nor block
 * the fan-out to the other listeners.
 *
 * <p>The bus reads the registered listeners from the client's store on every emit, so listeners added
 * or removed mid-call take effect immediately, and it iterates the unmodifiable snapshot the store
 * returns rather than holding any registry of its own. It maps each typed event to zero or more public
 * callbacks: a state change is internal and surfaces no callback (the lifecycle controller decides the
 * user-facing consequence and emits the dedicated end or peer-state event), an admin-forced mute
 * surfaces both as a mute change and as a peer-mute interaction, and a {@link CallEvent.Generic} event
 * passes the gate yet maps to no public callback because its typed payload is open. Every fan-out is
 * fire-and-forget; the bus never blocks on a listener and never propagates a listener exception back
 * to the engine.
 *
 * @implNote This implementation reproduces the host egress of {@code wa_call_dispatch_event} (fn11072)
 * and its should-emit gate in module {@code ff-tScznZ8P}, but as an allow-set rather than the native
 * computed deny-mask. The native gate is the helper {@code fn11074} (in {@code events.cc}, called from
 * fn11072 and the resend variant fn11077), which returns emit by default and suppresses only inside one
 * engine phase (a guard on the call object's phase field): it is a phase-conditional bitmask, not a
 * compiled-in id table, so it yields no static host-facing set to transcribe. {@link #HOST_FACING}
 * therefore enumerates the ids SPEC 17.1 lists as host-facing plus the signaling-driven lifecycle ids
 * the typed {@link CallEvent} records carry, each one corroborated by a dedicated {@code send_*_event}
 * emitter in {@code events.cc}; everything else is suppressed by default. An allow-set is the
 * conservative choice: a wrongly suppressed event is a missing notification, never a leak of an
 * engine-internal id to an application listener. The per-listener virtual-thread fan-out mirrors the
 * established call-listener dispatch in the legacy call service, where each {@code onCall*} callback is
 * run through {@link Thread#startVirtualThread(Runnable)}.
 */
public final class LiveCallEventBus implements CallEventBus {
    /**
     * Logs a listener-dispatch failure without letting it reach the call engine.
     */
    private static final System.Logger LOGGER = System.getLogger(LiveCallEventBus.class.getName());

    /**
     * The event types the gate admits to listeners.
     *
     * <p>This is the union of the host-facing ids SPEC 17.1 enumerates and the signaling-driven
     * lifecycle ids the typed {@link CallEvent} records surface (the incoming offer, the preaccept, and
     * the terminate end). Every {@link CallEventType} not in this set is treated as an internal or
     * diagnostic id and suppressed by {@link #shouldEmit(CallEventType)}.
     *
     * @implNote This implementation lists the ids SPEC 17.1 calls out as host-facing
     * ({@code 0x10}/{@code 0x46}/{@code 0x48}/{@code 0x49}/{@code 0x5c}/{@code 0x5d}/{@code 0x5f}/{@code 0x66}/{@code 0x6a}/{@code 0x6c}/{@code 0x6f}/{@code 0x72}/{@code 0x73}/{@code 0x74}/{@code 0x78}/{@code 0x8a}/{@code 0x8b}/{@code 0x91}/{@code 0x92}/{@code 0x93}/{@code 0x94}/{@code 0x9b}/{@code 0x9d}/{@code 0x9e}/{@code 0xa0}/{@code 0xa2}/{@code 0xa5}/{@code 0xa6}/{@code 0xa8}/{@code 0xa9}/{@code 0xaa})
     * together with the offer-received ({@code 0x02}), preaccept-received ({@code 0x09}), and
     * terminate-received ({@code 0x0a}) lifecycle ids the typed records carry. Each host-facing id is
     * corroborated by a dedicated {@code send_*_event} emitter in {@code events.cc} of module
     * {@code ff-tScznZ8P} (for example {@code send_mute_state_changed_event} dispatches {@code 0x49},
     * {@code send_video_state_changed_event} {@code 0x92}, {@code send_call_waiting_state_change_event}
     * {@code 0x48}), and the WASM-to-JS host bridge {@code fn621} ({@code VoipEvent.cpp}) forwards every
     * id it is handed without a per-id filter, so the gate alone determines the host set. The set is a
     * superset of every {@link CallEventType} a {@link CallEvent} record reports through
     * {@link CallEvent#type()}, so {@link #shouldEmit(CallEventType)} admits every event this bus can be
     * handed; it is the consulted-with type set, not a transcription of the native id space.
     */
    private static final Set<CallEventType> HOST_FACING = Set.of(
            CallEventType.CALL_OFFER_RECEIVED,
            CallEventType.CALL_PREACCEPT_RECEIVED,
            CallEventType.CALL_TERMINATE_RECEIVED,
            CallEventType.CALL_STATE_CHANGED,
            CallEventType.GROUP_INFO_CHANGED,
            CallEventType.CALL_WAITING_STATE_CHANGED,
            CallEventType.MUTE_STATE_CHANGED,
            CallEventType.CALL_FATAL,
            CallEventType.UPDATE_JOINABLE_CALL_LOG,
            CallEventType.PLAY_CALL_TONE,
            CallEventType.MUTE_BY_ANOTHER_PARTICIPANT,
            CallEventType.CALL_LINK_STATE_CHANGED,
            CallEventType.MUTE_REQUEST_FAILED,
            CallEventType.CALL_GRID_RANKING_CHANGED,
            CallEventType.VIDEO_RENDERING_STATE_CHANGED,
            CallEventType.USER_REMOVED,
            CallEventType.SCREEN_SHARE,
            CallEventType.LID_CALLER_DISPLAY_INFO,
            CallEventType.UPDATE_1ON1_CALL_LOG,
            CallEventType.CALL_LINK_LOBBY_SELF_STATE_CHANGED,
            CallEventType.REACTION_STATE_CHANGED,
            CallEventType.VIDEO_STATE_CHANGED,
            CallEventType.PEER_VIDEO_PERMISSION_CHANGED,
            CallEventType.RAISE_HAND_STATE_CHANGED,
            CallEventType.TRANSCRIPT_RECEIVED,
            CallEventType.WAITING_ROOM_DENIED,
            CallEventType.WAITING_ROOM_STATE_CHANGED,
            CallEventType.BOT_PRESENCE_CHANGED,
            CallEventType.LINK_QUERY_ACKED,
            CallEventType.WAITING_ROOM_ADMIT_ACKED,
            CallEventType.WAITING_ROOM_DENY_ACKED,
            CallEventType.CALL_ADD_EXTENSION_RECEIVED,
            CallEventType.CALL_ADD_EXTENSION_SUCCESS,
            CallEventType.CALL_ADD_EXTENSION_FAILURE);

    /**
     * The client whose listener registry receives the fanned-out events and that the callbacks receive.
     */
    private final LinkedWhatsAppClient whatsapp;

    /**
     * Constructs a bus bound to the client it dispatches through.
     *
     * @param whatsapp the client whose store supplies the listeners and whose handle the public
     *                 callbacks receive
     * @throws NullPointerException if {@code whatsapp} is {@code null}
     */
    public LiveCallEventBus(LinkedWhatsAppClient whatsapp) {
        this.whatsapp = Objects.requireNonNull(whatsapp, "whatsapp cannot be null");
    }

    /**
     * {@inheritDoc}
     *
     * @implNote This implementation tests the type against {@link #HOST_FACING}; {@link CallEvent.Generic}
     * events therefore pass when their carried type is host-facing even though no typed callback exists
     * for them.
     */
    @Override
    public boolean shouldEmit(CallEventType type) {
        Objects.requireNonNull(type, "type cannot be null");
        return HOST_FACING.contains(type);
    }

    /**
     * {@inheritDoc}
     *
     * @implNote This implementation gates on {@link CallEvent#type()}, then switches over the sealed
     * {@link CallEvent} hierarchy to route each family to its public callbacks. The
     * {@link CallEvent.StateChanged}, {@link CallEvent.CallLinkStateChanged},
     * {@link CallEvent.ScreenShareChanged}, {@link CallEvent.PeerVideoPermissionChanged},
     * {@link CallEvent.Transcript}, and {@link CallEvent.Generic} families have no public callback and
     * so reach the gate but dispatch nothing. The exhaustive {@code switch} needs no default branch.
     */
    @Override
    public void emit(CallEvent event) {
        Objects.requireNonNull(event, "event cannot be null");
        if (!shouldEmit(event.type())) {
            return;
        }
        switch (event) {
            case CallEvent.IncomingOffer e -> dispatch(LinkedCallListener.class,
                    listener -> listener.onCall(whatsapp, e.incoming()));
            case CallEvent.Preaccept e -> dispatch(LinkedCallPreacceptListener.class,
                    listener -> listener.onCallPreaccept(whatsapp, e.callId(), e.peerJid()));
            case CallEvent.PeerStateChanged e -> dispatch(LinkedCallPeerStateChangedListener.class,
                    listener -> listener.onCallPeerStateChanged(whatsapp, e.callId(), e.peerJid(), e.state()));
            case CallEvent.MuteChanged e -> emitMuteChanged(e);
            case CallEvent.VideoStateChanged e -> dispatch(LinkedCallVideoStateChangedListener.class,
                    listener -> listener.onCallVideoStateChanged(
                            whatsapp, e.callId(), e.participantJid(), e.enabled()));
            case CallEvent.VideoUpgradeRequest e -> dispatch(LinkedCallVideoUpgradeRequestListener.class,
                    listener -> listener.onCallVideoUpgradeRequest(whatsapp, e.callId(), e.peerJid()));
            case CallEvent.Reaction e -> dispatch(LinkedCallInteractionListener.class,
                    listener -> listener.onCallInteraction(
                            whatsapp, e.callId(), e.participantJid(), e.toInteraction()));
            case CallEvent.RaiseHand e -> dispatch(LinkedCallInteractionListener.class,
                    listener -> listener.onCallInteraction(
                            whatsapp, e.callId(), e.participantJid(), e.toInteraction()));
            case CallEvent.WaitingRoomJoinRequest e -> dispatch(LinkedCallLinkLobbyJoinRequestListener.class,
                    listener -> listener.onCallLinkLobbyJoinRequest(whatsapp, e.link(), e.peer()));
            case CallEvent.WaitingRoomAdmitted e -> dispatch(LinkedCallLinkAdmittedListener.class,
                    listener -> listener.onCallLinkAdmitted(whatsapp, e.link()));
            case CallEvent.WaitingRoomDenied e -> dispatch(LinkedCallLinkDeniedListener.class,
                    listener -> listener.onCallLinkDenied(whatsapp, e.link()));
            case CallEvent.ParticipantsChanged e -> dispatch(LinkedCallParticipantsChangedListener.class,
                    listener -> listener.onCallParticipantsChanged(
                            whatsapp, e.callId(), e.groupJid(), e.participants(), e.added()));
            case CallEvent.Ended e -> dispatch(LinkedCallEndedListener.class,
                    listener -> listener.onCallEnded(
                            whatsapp, e.callId(), e.from().orElse(null), e.reason()));
            case CallEvent.StateChanged ignored -> {
                // A lifecycle-internal transition; the lifecycle controller emits the dedicated
                // user-facing event the transition implies, so no public callback fires here.
            }
            case CallEvent.CallLinkStateChanged ignored -> {
                // A bare link sub-state advance carries no JoinableCallLink, so the admitted/denied
                // callbacks are emitted from the WaitingRoomAdmitted/WaitingRoomDenied events that do
                // carry it; the lifecycle controller consumes the sub-state advance itself.
            }
            case CallEvent.ScreenShareChanged ignored -> {
                // TODO: dispatch the screen-share change to a host listener. The native dispatcher
                //  (send_screen_share_event, event 0x74) delivers screen-share start/stop/fail to the host;
                //  onCallVideoStateChanged means a camera toggle, not screen share, so this needs a new
                //  LinkedCallScreenShareChangedListener (in listener/linked) carrying the participant JID and
                //  the ScreenShareChanged.State. No such listener type exists, so the event is gated in for
                //  parity with the native host-facing set but currently reaches no callback.
            }
            case CallEvent.PeerVideoPermissionChanged ignored -> {
                // TODO: dispatch the peer video-permission change to a host listener. The native dispatcher
                //  delivers event 0x93 (peer video-permission grant/revoke) to the host; this needs a new
                //  LinkedCallPeerVideoPermissionChangedListener (in listener/linked) carrying the peer JID and
                //  the permitted flag. No such listener type exists, so the event is gated in for parity but
                //  currently reaches no callback.
            }
            case CallEvent.Transcript ignored -> {
                // TODO: dispatch the live-transcription fragment to a host listener. The native dispatcher
                //  delivers event 0x9b (transcript received) to the host; this needs a new
                //  LinkedCallTranscriptListener (in listener/linked) carrying the speaker JID, text, language,
                //  and sequence. No such listener type exists, so the event is gated in for parity but
                //  currently reaches no callback.
            }
            case CallEvent.Generic ignored -> {
                // TODO: provide a raw-event egress for the host-facing ids that carry no typed CallEvent
                //  record (call-waiting state change, add-extension received/success/failure, lobby-self-state
                //  change, play-call-tone, mute-request-failed, grid-ranking change, video-rendering-state,
                //  lid-caller-display-info, bot-presence, link-query-acked, waiting-room-deny-acked, and the
                //  1on1/joinable call-log updates). The native host bridge (fn621) forwards every host-facing
                //  id without a per-id filter, so each is delivered; here they can only be built as
                //  CallEvent.Generic and reach no callback. A faithful fix is a new
                //  onCallRawEvent(CallEventType, callId) host listener (in listener/linked) plus typed
                //  CallEvent records for the ids the application needs; neither exists yet, so a gated-in
                //  Generic event is currently dropped.
            }
        }
    }

    /**
     * Fans a mute change out to the mute-changed callback and, when admin-forced, to the interaction
     * callback as a peer-mute request.
     *
     * <p>A self-initiated change reaches only {@link LinkedCallMuteChangedListener}. An admin-forced
     * mute reaches that callback too, because the participant's mic state did change, and additionally
     * surfaces as a {@link com.github.auties00.cobalt.model.call.CallInteraction.PeerMuteRequest} on
     * {@link LinkedCallInteractionListener}, the facade through which a forced mute is exposed.
     *
     * @param event the mute change to fan out
     */
    private void emitMuteChanged(CallEvent.MuteChanged event) {
        dispatch(LinkedCallMuteChangedListener.class,
                listener -> listener.onCallMuteChanged(
                        whatsapp, event.callId(), event.participantJid(), event.muted()));
        if (event.byAnotherParticipant()) {
            var interaction = new CallInteraction.PeerMuteRequest(
                    event.participantJid().toString(), Optional.empty());
            dispatch(LinkedCallInteractionListener.class,
                    listener -> listener.onCallInteraction(
                            whatsapp, event.callId(), event.participantJid(), interaction));
        }
    }

    /**
     * Dispatches an action to every registered listener of a given event-interface type, each on its
     * own virtual thread.
     *
     * <p>The current listener snapshot is read from the client's store, so a listener added or removed
     * mid-call is reflected on the next emit. Each matching listener's callback is run through
     * {@link Thread#startVirtualThread(Runnable)} and wrapped so a listener exception is logged and
     * swallowed rather than propagated, isolating one faulty listener from the engine and from the
     * other listeners.
     *
     * @param listenerType the event-interface type whose registered listeners receive the action
     * @param action       the callback invocation to run for each matching listener
     * @param <T>          the event-interface type
     */
    private <T extends WhatsAppListener> void dispatch(Class<T> listenerType, Consumer<T> action) {
        for (var listener : whatsapp.store().listeners()) {
            if (listenerType.isInstance(listener)) {
                var typed = listenerType.cast(listener);
                Thread.startVirtualThread(() -> {
                    try {
                        action.accept(typed);
                    } catch (RuntimeException exception) {
                        LOGGER.log(System.Logger.Level.WARNING,
                                "Call event listener threw " + exception.getClass().getSimpleName()
                                        + ": " + exception.getMessage());
                    }
                });
            }
        }
    }
}
