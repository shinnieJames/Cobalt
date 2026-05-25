package com.github.auties00.cobalt.call.internal;

import com.github.auties00.cobalt.call.*;
import com.github.auties00.cobalt.call.internal.interaction.CallInteractionEncoder;
import com.github.auties00.cobalt.call.internal.signaling.CallIdGenerator;
import com.github.auties00.cobalt.call.internal.signaling.CallReceiver;
import com.github.auties00.cobalt.call.internal.signaling.CallStanza;
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
 * Coordinates one client's call activity.
 *
 * <p>This service owns the registry of in-flight {@link ActiveCall} sessions
 * and exposes the call-control entry points used by {@link WhatsAppClient}:
 * {@link #placeCall(Jid, CallOptions)} for outbound calls, and
 * {@link #accept(IncomingCall, CallOptions)} and
 * {@link #reject(IncomingCall, CallEndReason)} for inbound offers. It sits
 * between the public client API and listener surface above and the signaling
 * classes ({@link CallReceiver}, {@link CallStanza}) and transport and media
 * layers below. There is one instance per {@link WhatsAppClient}.
 *
 * <p>The service implements the signaling and state-machine portion of a
 * call: it produces {@link ActiveCall} instances that own their lifecycle and
 * react to peer-side state changes routed back through
 * {@link #onPeerAccept(String)}, {@link #onPeerReject(String, String)}, and
 * {@link #onPeerTerminate(String, String)}. A placed or accepted call sits in
 * {@link CallState#CONNECTING} until the media plane wires into its
 * {@link ActiveCall} and is terminated by either a local
 * {@link ActiveCall#hangup()} or a peer termination.
 */
public class CallService {
    /**
     * Holds the owning client.
     *
     * <p>The client is used to send signaling stanzas, resolve the local self
     * {@link Jid}, and surface end-of-call notifications to listeners.
     */
    private final WhatsAppClient whatsapp;

    /**
     * Tracks live calls keyed by their unique call identifier.
     *
     * <p>An entry is added on {@link #placeCall(Jid, CallOptions)} or
     * {@link #accept(IncomingCall, CallOptions)} and removed by
     * {@link #unregister(String)} when the call reaches
     * {@link CallState#ENDED}.
     */
    private final ConcurrentHashMap<String, ActiveCall> activeCalls = new ConcurrentHashMap<>();

    /**
     * Tracks per-call telemetry accumulators keyed by call identifier.
     *
     * <p>An accumulator is created alongside its {@link ActiveCall} and is
     * drained into a WAM Call event when the call ends.
     */
    private final ConcurrentHashMap<String, CallStatsAccumulator> stats = new ConcurrentHashMap<>();

    /**
     * Holds the WAM service used to commit the per-call telemetry event.
     */
    private final WamService wamService;

    /**
     * Constructs a service bound to the given client.
     *
     * @param whatsapp   the owning client
     * @param wamService the WAM telemetry service used for end-of-call
     *                   field-stats events
     */
    public CallService(WhatsAppClient whatsapp, WamService wamService) {
        this.whatsapp = whatsapp;
        this.wamService = wamService;
    }

    /**
     * Places an outbound call to {@code peer} with the given options and
     * returns its live session.
     *
     * <p>A fresh call identifier is generated, an {@link ActiveCall} is
     * registered in the in-flight registry along with a caller-side telemetry
     * accumulator, and the offer stanza is sent. The session is parked in
     * {@link CallState#CONNECTING} until the peer's acceptance arrives through
     * {@link #onPeerAccept(String)}.
     *
     * @param peer    the {@link Jid} of the callee
     * @param options the local side's preferred settings
     * @return the live session
     * @throws NullPointerException  if {@code peer} or {@code options} is
     *                               {@code null}
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
     * Accepts an inbound call offer and returns its live session.
     *
     * <p>An {@link ActiveCall} is registered in the in-flight registry along
     * with a callee-side telemetry accumulator, the accept stanza is sent, and
     * the session is parked in {@link CallState#CONNECTING}. This method is
     * invoked after the one-shot guard on the offer has been claimed.
     *
     * @param offer   the offer being accepted
     * @param options the local side's preferred settings
     * @return the live session
     * @throws NullPointerException if {@code offer} or {@code options} is
     *                              {@code null}
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
     * Rejects an inbound call offer with the given reason.
     *
     * <p>The reject stanza is sent to the peer, the offer is removed from the
     * store, and {@code onCallEnded} is fired on every listener from a virtual
     * thread. This method is invoked after the one-shot guard on the offer has
     * been claimed.
     *
     * @param offer  the offer being rejected
     * @param reason the {@link CallEndReason} to communicate to the peer
     * @throws NullPointerException if {@code offer} or {@code reason} is
     *                              {@code null}
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
     * Returns the {@link ActiveCall} tracked under the given identifier, or
     * {@code null} if none is tracked.
     *
     * <p>A {@code null} argument yields {@code null}. This lookup lets
     * {@link CallReceiver} route peer-side state transitions to the matching
     * session.
     *
     * @param callId the call's unique identifier
     * @return the matching session, or {@code null}
     */
    public ActiveCall find(String callId) {
        return callId == null ? null : activeCalls.get(callId);
    }

    /**
     * Reports that the peer accepted a previously-placed outbound call.
     *
     * <p>The acceptance is forwarded to the matching session, if one is still
     * tracked; otherwise the report is dropped.
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
     * Reports that the peer rejected a previously-placed outbound call.
     *
     * <p>The matching session, if still tracked, is ended with the given wire
     * reason; otherwise the report is dropped.
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
     * Reports that the peer terminated an in-flight call.
     *
     * <p>The matching session, if still tracked, is ended with the given wire
     * reason; otherwise the report is dropped.
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
     * Removes a session from the registry and emits its end-of-call
     * telemetry.
     *
     * <p>This method is invoked by {@link ActiveCall} when it transitions to
     * {@link CallState#ENDED}. The session and its telemetry accumulator are
     * removed, the call is removed from the store, and the accumulator is
     * drained into a WAM Call event committed through {@link WamService}.
     * Telemetry is skipped when no client, session, accumulator, or WAM
     * service is available.
     *
     * @param callId the call identifier
     */
    public void unregister(String callId) {
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
     * Builds and commits the WAM Call event for one ended call.
     *
     * <p>The event combines the accumulator's start-time dimensions with the
     * session's terminal {@link CallEndReason}, mapped to a
     * {@link CallResultType}, and is committed through {@link WamService}. Any
     * {@link RuntimeException} raised while building or committing is swallowed
     * so that telemetry never propagates a failure into the call path.
     *
     * @param session     the call that just ended
     * @param accumulator the per-call telemetry
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
     * Maps a {@link CallEndReason} to the {@link CallResultType} reported in
     * the WAM Call event.
     *
     * @implNote This implementation reports
     * {@link CallEndReason#ACCEPTED_ELSEWHERE} as
     * {@link CallResultType#CONNECTED}: it arrives as a peer-side terminate,
     * but from the local user's perspective the call did connect, just not on
     * this device.
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
            case ACCEPTED_ELSEWHERE -> CallResultType.CONNECTED;
            case UNKNOWN -> CallResultType.INVALID;
        };
    }

    /**
     * Holds the per-call telemetry dimensions known at call start.
     *
     * <p>The accumulator captures the deterministic fields available when a
     * call begins; further fields are populated by media and transport tasks
     * mutating the accumulators held in {@link #stats}.
     *
     * @param callId       the call identifier
     * @param side         which side initiated the call
     * @param videoEnabled whether video was enabled
     * @param startedAt    when {@link #placeCall(Jid, CallOptions)} or
     *                     {@link #accept(IncomingCall, CallOptions)} fired
     */
    record CallStatsAccumulator(String callId, CallSide side, boolean videoEnabled, Instant startedAt) {
    }

    /**
     * Sends a call-termination stanza to the peer.
     *
     * <p>This method is invoked by {@link ActiveCall#hangup()}.
     *
     * @param peer    the peer {@link Jid}
     * @param creator the call-creator {@link Jid}: the local user for outbound
     *                calls, the peer for inbound calls
     * @param callId  the call identifier
     * @param reason  the {@link CallEndReason} to communicate
     */
    public void sendTerminate(Jid peer, Jid creator, String callId, CallEndReason reason) {
        whatsapp.sendNodeWithNoResponse(CallStanza.terminate(peer, creator, callId, reason));
    }

    /**
     * Sends a mute-state stanza notifying the peer that the local microphone
     * was muted or unmuted.
     *
     * @param peer    the peer {@link Jid}
     * @param creator the call-creator {@link Jid}
     * @param callId  the call identifier
     * @param muted   {@code true} if the microphone is now muted
     */
    public void sendMute(Jid peer, Jid creator, String callId, boolean muted) {
        whatsapp.sendNodeWithNoResponse(CallStanza.mute(peer, creator, callId, muted));
    }

    /**
     * Sends a video-state stanza notifying the peer that local video was
     * enabled or disabled.
     *
     * @param peer    the peer {@link Jid}
     * @param creator the call-creator {@link Jid}
     * @param callId  the call identifier
     * @param enabled {@code true} if local video is now on
     */
    public void sendVideoState(Jid peer, Jid creator, String callId, boolean enabled) {
        whatsapp.sendNodeWithNoResponse(CallStanza.videoState(peer, creator, callId, enabled));
    }

    /**
     * Sends the request leg of a mid-call video upgrade.
     *
     * <p>The request is expressed as an enabling video-state stanza and
     * delegates to {@link #sendVideoState(Jid, Jid, String, boolean)} so that
     * a subclass overriding that one method intercepts the whole video-state
     * family.
     *
     * @param peer    the peer {@link Jid}
     * @param creator the call-creator {@link Jid}
     * @param callId  the call identifier
     */
    public void sendVideoUpgradeRequest(Jid peer, Jid creator, String callId) {
        sendVideoState(peer, creator, callId, true);
    }

    /**
     * Sends the peer-side rejection of a mid-call video upgrade.
     *
     * <p>The rejection is expressed as a disabling video-state stanza and
     * keeps the caller audio-only.
     *
     * @param peer    the peer {@link Jid}
     * @param creator the call-creator {@link Jid}
     * @param callId  the call identifier
     */
    public void sendVideoUpgradeReject(Jid peer, Jid creator, String callId) {
        sendVideoState(peer, creator, callId, false);
    }

    /**
     * Sends an in-call interaction over the call's pre-negotiated
     * DataChannel.
     *
     * <p>The interaction is encoded into an RTP-shaped packet by
     * {@link CallInteractionEncoder}, encrypted with the call's negotiated
     * SRTP keys, and handed to the call's default DataChannel. The call is a
     * no-op when no call is registered for {@code callId}, when the
     * DataChannel is not open, or when the SRTP endpoint is not yet available.
     * The {@code peer} and {@code creator} parameters are unused on this path.
     *
     * @implNote This implementation always protects the envelope with
     * {@code protectRtp}. The interaction stream uses RTP framing even when
     * byte 1 of the packet is {@code 0xc8}; that value is a payload-type
     * literal rather than an RTCP packet type, so the SRTCP path is never
     * taken. The plaintext body of each interaction is empirically derived,
     * because the live WhatsApp wasm encrypts before publishing, so the exact
     * plaintext is not recoverable from captures alone.
     *
     * @param peer        the peer {@link Jid} (unused on this path)
     * @param creator     the call-creator {@link Jid} (unused on this path)
     * @param callId      the call identifier
     * @param interaction the interaction payload
     * @throws NullPointerException if {@code callId} or {@code interaction} is
     *                              {@code null}
     */
    public void sendInteraction(Jid peer, Jid creator, String callId, CallInteraction interaction) {
        Objects.requireNonNull(callId, "callId cannot be null");
        Objects.requireNonNull(interaction, "interaction cannot be null");
        var call = activeCalls.get(callId);
        if (call == null) {
            return;
        }
        var transport = call.transport();
        var channelOpt = transport.dataChannel();
        var srtpOpt = transport.srtp();
        if (channelOpt.isEmpty() || srtpOpt.isEmpty()) {
            return;
        }
        var packet = CallInteractionEncoder.encode(interaction, transport.interactionStreamState());
        var ciphertext = srtpOpt.get().protectRtp(packet);
        channelOpt.get().send(ciphertext);
    }

    /**
     * Notifies all registered listeners that a call ended.
     *
     * <p>The wire reason is parsed into a typed {@link CallEndReason} via
     * {@link CallEndReason#fromWireValue(String)}, so an unrecognized or
     * absent literal surfaces as {@link CallEndReason#UNKNOWN}. Each listener
     * is notified from its own virtual thread.
     *
     * @param callId     the call identifier
     * @param fromJid    the {@link Jid} of the party that ended the call
     * @param wireReason the wire-level reason literal, or {@code null}
     */
    public void notifyEnded(String callId, Jid fromJid, String wireReason) {
        var parsed = CallEndReason.fromWireValue(wireReason);
        for (var listener : whatsapp.store().listeners()) {
            Thread.startVirtualThread(() -> listener.onCallEnded(whatsapp, callId, fromJid, parsed));
        }
    }
}
