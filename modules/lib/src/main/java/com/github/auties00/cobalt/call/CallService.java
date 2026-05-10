package com.github.auties00.cobalt.call;

import com.github.auties00.cobalt.call.signaling.*;
import com.github.auties00.cobalt.client.WhatsAppClient;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.wam.WamService;
import com.github.auties00.cobalt.wam.event.CallEventBuilder;
import com.github.auties00.cobalt.wam.type.CallResultType;
import com.github.auties00.cobalt.wam.type.CallSide;

import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Central coordinator for one client's call activity. Owns the
 * registry of in-flight {@link ActiveCall}s and exposes the
 * outbound entry points ({@link #placeCall}) plus the inbound-offer
 * accept/reject paths that {@link WhatsAppClient#acceptCall} and
 * {@link WhatsAppClient#rejectCall} delegate to. One instance per
 * {@link WhatsAppClient}.
 *
 * <p>The engine sits between two layers:
 *
 * <ul>
 *   <li><b>Above:</b> the public API ({@link WhatsAppClient#startCall},
 *       {@link WhatsAppClient#acceptCall}) and the listener event
 *       surface ({@code WhatsAppClientListener.onCall}).</li>
 *   <li><b>Below:</b> the signaling layer
 *       ({@link CallReceiver} + {@link CallStanza}) for stanza I/O,
 *       and the transport/media layers (#76 ICE, #77 DTLS-SRTP, #78
 *       RTP, #61/#62 media pipelines) once they land.</li>
 * </ul>
 *
 * <p>This engine implements the signaling and state-machine portion
 * — it produces real {@link ActiveCall} instances that own their
 * lifecycle and respond correctly to peer-side state changes. The
 * media plane (encoders, transport, RTP) wires into
 * {@link ActiveCall} as it lands; until then a placed/accepted
 * call sits in {@link CallState#CONNECTING} and is terminated either
 * by local {@link ActiveCall#hangup()} or peer
 * {@code <terminate>}.
 */
public class CallService {
    /**
     * The owning client — used to send signaling stanzas, look up
     * the local self JID, and surface end-of-call notifications to
     * listeners.
     */
    private final WhatsAppClient whatsapp;

    /**
     * Live calls keyed by their unique callId — entries are added on
     * {@link #placeCall} / {@link #accept} and removed when the call
     * reaches {@link CallState#ENDED}.
     */
    private final ConcurrentHashMap<String, ActiveCall> activeCalls = new ConcurrentHashMap<>();

    /**
     * Per-call stats accumulators keyed by callId — populated as the
     * call's lifecycle events fire and drained into the WAM Call
     * event ({@link com.github.auties00.cobalt.wam.event.CallEvent},
     * id 462) when the call ends.
     */
    private final ConcurrentHashMap<String, CallStatsAccumulator> stats = new ConcurrentHashMap<>();

    /**
     * The WAM service used to commit the per-call telemetry event.
     */
    private final WamService wamService;

    /**
     * Constructs a new engine bound to the given client.
     *
     * @param whatsapp   the owning client
     * @param wamService the WAM telemetry service for end-of-call
     *                   field-stats events
     */
    public CallService(WhatsAppClient whatsapp, WamService wamService) {
        this.whatsapp = whatsapp;
        this.wamService = wamService;
    }

    /**
     * Places an outbound call to {@code peer} with the given options
     * and returns a live {@link ActiveCall}. Sends the
     * {@code <call><offer/></call>} stanza, registers the call in
     * the in-flight registry, and parks the session in
     * {@link CallState#CONNECTING} until the peer's {@code <accept>}
     * arrives.
     *
     * @param peer    the JID of the callee
     * @param options the local side's preferred settings
     * @return a live session
     * @throws NullPointerException  if any argument is {@code null}
     * @throws IllegalStateException if the client is not logged in
     */
    public ActiveCall placeCall(Jid peer, CallOptions options) {
        Objects.requireNonNull(peer, "peer cannot be null");
        Objects.requireNonNull(options, "options cannot be null");
        var selfJid = whatsapp.store().jid()
                .orElseThrow(() -> new IllegalStateException("Not logged in"));
        var callId = CallIdGenerator.generate();
        var session = new ActiveCall(this, callId, peer, peer, selfJid, true, options);
        activeCalls.put(callId, session);
        stats.put(callId, new CallStatsAccumulator(callId, CallSide.CALLER, options.videoEnabled(), Instant.now()));
        whatsapp.sendNodeWithNoResponse(
                CallStanza.offer(peer, selfJid, callId, options.videoEnabled(), null, null));
        return session;
    }

    /**
     * Accepts an inbound call offer. Sends the
     * {@code <call><accept/></call>} stanza, registers the live
     * session in the in-flight registry, and parks it in
     * {@link CallState#CONNECTING}.
     *
     * <p>Invoked by
     * {@link WhatsAppClient#acceptCall(IncomingCall, CallOptions)}
     * after the one-shot guard on the offer has been claimed.
     *
     * @param offer   the offer being accepted
     * @param options the local side's preferred settings
     * @return the live session
     */
    public ActiveCall accept(IncomingCall offer, CallOptions options) {
        Objects.requireNonNull(offer, "offer cannot be null");
        Objects.requireNonNull(options, "options cannot be null");
        var session = new ActiveCall(
                this,
                offer.callId(),
                offer.peer(),
                offer.chatJid(),
                offer.peer(),
                false,
                options);
        activeCalls.put(offer.callId(), session);
        stats.put(offer.callId(),
                new CallStatsAccumulator(offer.callId(), CallSide.CALLEE, options.videoEnabled(), Instant.now()));
        whatsapp.sendNodeWithNoResponse(CallStanza.accept(offer.peer(), offer.callId()));
        return session;
    }

    /**
     * Rejects an inbound call offer with the given reason. Sends the
     * {@code <call><reject/></call>} stanza and fires
     * {@code onCallEnded} on every listener.
     *
     * <p>Invoked by
     * {@link WhatsAppClient#rejectCall(IncomingCall, CallEndReason)}
     * after the one-shot guard on the offer has been claimed.
     *
     * @param offer  the offer being rejected
     * @param reason the reason to communicate to the peer
     */
    public void reject(IncomingCall offer, CallEndReason reason) {
        Objects.requireNonNull(offer, "offer cannot be null");
        Objects.requireNonNull(reason, "reason cannot be null");
        whatsapp.sendNodeWithNoResponse(CallStanza.reject(offer.peer(), offer.callId()));
        whatsapp.store().removeCall(offer.callId());
        for (var listener : whatsapp.store().listeners()) {
            Thread.startVirtualThread(() ->
                    listener.onCallEnded(whatsapp, offer.callId(), offer.peer(), reason));
        }
    }

    /**
     * Looks up the {@link ActiveCall} corresponding to a given
     * {@code callId}, returning {@code null} if no such call is
     * tracked. Visible to {@link CallReceiver} so it can route
     * peer-side state transitions to the right session.
     *
     * @param callId the call's unique identifier
     * @return the matching session, or {@code null}
     */
    ActiveCall find(String callId) {
        return callId == null ? null : activeCalls.get(callId);
    }

    /**
     * Reports that the peer has accepted a previously-placed
     * outbound call. Forwards to the live session.
     *
     * @param callId the call identifier
     */
    public void onPeerAccept(String callId) {
        var session = find(callId);
        if (session != null) {
            session.onPeerAccept();
        }
    }

    /**
     * Reports that the peer has rejected a previously-placed
     * outbound call.
     *
     * @param callId the call identifier
     * @param reason the wire-level rejection reason, or {@code null}
     */
    public void onPeerReject(String callId, String reason) {
        var session = find(callId);
        if (session != null) {
            session.onPeerEnded(reason);
        }
    }

    /**
     * Reports that the peer has terminated an in-flight call.
     *
     * @param callId the call identifier
     * @param reason the wire-level reason, or {@code null}
     */
    public void onPeerTerminate(String callId, String reason) {
        var session = find(callId);
        if (session != null) {
            session.onPeerEnded(reason);
        }
    }

    /**
     * Removes a session from the registry — invoked by
     * {@link ActiveCall} when it transitions to ENDED. Drains the
     * per-call stats accumulator into a
     * {@link com.github.auties00.cobalt.wam.event.CallEvent} (WAM
     * id 462) and commits it via {@link WamService}.
     *
     * @param callId the call identifier
     */
    void unregister(String callId) {
        var session = activeCalls.remove(callId);
        var accumulator = stats.remove(callId);
        if (whatsapp != null) {
            whatsapp.store().removeCall(callId);
        }
        if (wamService == null || session == null || accumulator == null) {
            return;
        }
        emitFieldstatsEvent(session, accumulator);
    }

    /**
     * Builds the WAM Call event from one accumulator + the matching
     * {@link ActiveCall} terminal state and commits it via
     * {@link WamService#commit}. Failures are swallowed — telemetry
     * never crashes the engine.
     *
     * @param session     the call that just ended
     * @param accumulator the per-call stats
     */
    private void emitFieldstatsEvent(ActiveCall session, CallStatsAccumulator accumulator) {
        try {
            var endReason = session.endReason().orElse(CallEndReason.UNKNOWN);
            var event = new CallEventBuilder()
                    .callRandomId(accumulator.callId())
                    .callSide(accumulator.side())
                    .callResult(mapToResultType(endReason))
                    .videoEnabled(accumulator.videoEnabled())
                    .videoEnabledAtCallStart(accumulator.videoEnabled())
                    .callOfferElapsedT(accumulator.startedAt())
                    .build();
            wamService.commit(event);
        } catch (RuntimeException _) {
        }
    }

    /**
     * Maps a Cobalt {@link CallEndReason} to the WAM
     * {@link CallResultType} for the {@code callResult} field.
     *
     * @param reason the canonical end reason
     * @return the matching WAM result type
     */
    private static CallResultType mapToResultType(CallEndReason reason) {
        return switch (reason) {
            case HANGUP -> CallResultType.CONNECTED;
            case TIMEOUT -> CallResultType.MISSED;
            case REJECT_DO_NOT_DISTURB, REJECT_BLOCKED -> CallResultType.REJECTED_BY_USER;
            case MIC_PERMISSION_DENIED, CAMERA_PERMISSION_DENIED -> CallResultType.SETUP_ERROR;
            case UNKNOWN -> CallResultType.INVALID;
        };
    }

    /**
     * Per-call telemetry accumulator. Holds the deterministic
     * dimensions known at call start (callId, side, video flag,
     * start timestamp); media and transport tasks (#76–#78,
     * #61/#62) populate further fields by mutating accumulators
     * via {@link #stats}.
     *
     * @param callId       the call identifier
     * @param side         which side initiated the call
     * @param videoEnabled whether video was enabled
     * @param startedAt    when {@link #placeCall} or
     *                     {@link #accept} fired
     */
    record CallStatsAccumulator(String callId, CallSide side, boolean videoEnabled, Instant startedAt) {
    }

    /**
     * Sends a {@code <call><terminate/></call>} stanza to the peer.
     * Invoked by {@link ActiveCall#hangup()}.
     *
     * @param peer    the peer JID
     * @param creator the call creator JID (us for outbound, peer for
     *                inbound)
     * @param callId  the call identifier
     * @param reason  the reason to communicate
     */
    void sendTerminate(Jid peer, Jid creator, String callId, CallEndReason reason) {
        whatsapp.sendNodeWithNoResponse(CallStanza.terminate(peer, creator, callId, reason));
    }

    /**
     * Sends a {@code <call><mute/></call>} stanza notifying the peer
     * that the local mic was muted or unmuted.
     *
     * @param peer    the peer JID
     * @param creator the call creator JID
     * @param callId  the call identifier
     * @param muted   {@code true} if muted
     */
    void sendMute(Jid peer, Jid creator, String callId, boolean muted) {
        whatsapp.sendNodeWithNoResponse(CallStanza.mute(peer, creator, callId, muted));
    }

    /**
     * Sends a {@code <call><video_state/></call>} stanza notifying
     * the peer that local video was enabled or disabled.
     *
     * @param peer    the peer JID
     * @param creator the call creator JID
     * @param callId  the call identifier
     * @param enabled {@code true} if video is on
     */
    void sendVideoState(Jid peer, Jid creator, String callId, boolean enabled) {
        whatsapp.sendNodeWithNoResponse(CallStanza.videoState(peer, creator, callId, enabled));
    }

    /**
     * Sends a video-upgrade request stanza — the M4 mid-call
     * upgrade flow's request leg. Delegates to
     * {@link #sendVideoState} so subclasses can override one
     * method and intercept the whole video-state family.
     *
     * @param peer    the peer JID
     * @param creator the call creator JID
     * @param callId  the call identifier
     */
    void sendVideoUpgradeRequest(Jid peer, Jid creator, String callId) {
        sendVideoState(peer, creator, callId, true);
    }

    /**
     * Sends a video-upgrade rejection stanza — peer-side response
     * declining the upgrade. The caller stays audio-only.
     *
     * @param peer    the peer JID
     * @param creator the call creator JID
     * @param callId  the call identifier
     */
    void sendVideoUpgradeReject(Jid peer, Jid creator, String callId) {
        sendVideoState(peer, creator, callId, false);
    }

    /**
     * Sends an M8 {@code <call><interaction/></call>}-family
     * stanza with the given {@link CallInteraction} payload.
     * Currently a stub that no-ops if the underlying
     * {@code WhatsAppClient} is null (test fixtures); the live
     * stanza shape is finalised once the signaling-side capture is
     * available.
     *
     * @param peer        the peer JID
     * @param creator     the call creator JID
     * @param callId      the call identifier
     * @param interaction the interaction payload
     */
    void sendInteraction(Jid peer, Jid creator, String callId, CallInteraction interaction) {
        if (whatsapp == null) {
            return;
        }
        // Live wire format will be finalised when the signaling
        // capture for in-call interactions is available; for now
        // use a video-state-on stanza as a placeholder so the
        // stub is observable in tests.
        whatsapp.sendNodeWithNoResponse(CallStanza.videoState(peer, creator, callId, true));
    }

    /**
     * Notifies all registered listeners that a call ended. The wire
     * reason is parsed into a typed {@link CallEndReason}; unknown
     * literals surface as {@link CallEndReason#UNKNOWN}.
     *
     * @param callId     the call identifier
     * @param fromJid    the JID of the party that ended the call
     * @param wireReason the wire-level reason literal, or {@code null}
     */
    void notifyEnded(String callId, Jid fromJid, String wireReason) {
        var parsed = CallEndReason.fromWireValue(wireReason);
        for (var listener : whatsapp.store().listeners()) {
            Thread.startVirtualThread(() -> listener.onCallEnded(whatsapp, callId, fromJid, parsed));
        }
    }

    /**
     * Returns the current monotonic wall-clock — exposed so
     * {@link ActiveCall} can stamp lifecycle events
     * consistently. Switched out for a fake clock in tests.
     *
     * @return the current instant
     */
    Instant now() {
        return Instant.now();
    }

    /**
     * Returns the owning client.
     *
     * @return the client
     */
    WhatsAppClient client() {
        return whatsapp;
    }
}
