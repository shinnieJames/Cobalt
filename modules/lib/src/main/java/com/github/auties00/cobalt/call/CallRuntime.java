package com.github.auties00.cobalt.call;

import com.github.auties00.cobalt.call.session.CallMediaSession;
import com.github.auties00.cobalt.call.session.GroupCallSession;
import com.github.auties00.cobalt.call.session.VoiceCallSession;
import com.github.auties00.cobalt.call.transport.ActiveCallTransport;
import com.github.auties00.cobalt.call.stream.AudioFrame;
import com.github.auties00.cobalt.call.stream.AudioInputStream;
import com.github.auties00.cobalt.call.stream.AudioOutputStream;
import com.github.auties00.cobalt.call.stream.VideoFrame;
import com.github.auties00.cobalt.call.stream.VideoInputStream;
import com.github.auties00.cobalt.call.stream.VideoOutputStream;
import com.github.auties00.cobalt.model.call.Call;
import com.github.auties00.cobalt.model.call.CallEndReason;
import com.github.auties00.cobalt.model.call.CallState;
import com.github.auties00.cobalt.model.call.datachannel.E2eRekeyPayload;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.wam.type.CallSide;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Holds the live runtime state of a single call: the engine-internal counterpart of the public
 * {@link Call} data model.
 *
 * <p>Where {@link Call} carries only the descriptive state an application observes, this object owns
 * everything the engine needs to actually run the call and nothing an application should touch: the
 * four media streams that bridge the application and the codecs, the transport stack, the attached
 * media-plane session, the latest end-to-end rekey bundle, and the peer-acceptance flag. The call
 * layer keeps a {@code CallRuntime} per active call in its registry and discards it when the call ends.
 *
 * <p>Outbound frames are pulled from {@link #audioOut()} and {@link #videoOut()} by the codec
 * pipelines through {@link #takeOutboundAudio()} and {@link #takeOutboundVideo()}, which drop frames
 * while the corresponding medium is muted. Inbound frames decoded from the peer are pushed into
 * {@link #audioIn()} and {@link #videoIn()} through {@link #deliverInboundAudio(AudioFrame)} and
 * {@link #deliverInboundVideo(VideoFrame)}. {@link #end(CallEndReason, String)} is the single teardown
 * path: it flips the {@link Call} to {@link CallState#ENDED}, shuts the streams so blocked application
 * reads and writes unblock, closes the session and transport, and unregisters the call from the engine.
 */
public final class CallRuntime {
    /**
     * Holds the owning engine, used to unregister the call and fire the call-ended notification at
     * teardown.
     */
    private final CallService engine;

    /**
     * Holds the public data view this runtime drives.
     */
    private final Call call;

    /**
     * Holds the per-call telemetry accumulator owned by this runtime.
     *
     * <p>Stamped at the connected and ended lifecycle transitions through {@link #notifyActive()} and
     * {@link #end(CallEndReason, String)} and drained into a WAM Call event by the engine when the call
     * is unregistered.
     */
    private final CallStats stats;

    /**
     * Guards the one-time media-plane bring-up through {@link #beginMediaPlane()}.
     *
     * <p>A relay block can reach the engine both inline in the offer and again in a
     * {@code <group_update>} push; this flag ensures only the first observation starts the transport.
     */
    private final AtomicBoolean mediaPlaneStarted = new AtomicBoolean();

    /**
     * Holds the 32-byte per-call shared key, or {@code null} until it is known.
     *
     * <p>For an outbound call it is generated locally and shipped, Signal-encrypted, to every peer
     * device in the offer; for an inbound call it is the key the caller generated, transferred here from
     * the engine's pre-acceptance offer stash when the call is answered. It keys the end-to-end
     * participant media SRTP (Family B).
     */
    private volatile byte[] callKey;

    /**
     * Holds the deferred caller-side media bring-up continuation, or {@code null} when none is parked.
     *
     * <p>For an outbound call the relay is allocated when the offer ACK arrives, but the peer-DTLS
     * handshake and media session are deferred until the peer answers (the remote side only opens its
     * relay DataChannel after {@code <accept>}). The continuation that runs those deferred steps is
     * parked here through {@link #setPendingMediaPlane(Runnable)} and executed exactly once, by whichever
     * of relay-allocation or {@link #onPeerAccept()} first calls {@link #runPendingMediaPlane()}.
     */
    private final AtomicReference<Runnable> pendingMediaPlane = new AtomicReference<>();

    /**
     * Holds the stream the application writes local audio into and the encoder drains.
     */
    private final AudioOutputStream audioOut;

    /**
     * Holds the stream the decoder fills with remote audio and the application reads.
     */
    private final AudioInputStream audioIn;

    /**
     * Holds the stream the application writes local video into and the encoder drains.
     */
    private final VideoOutputStream videoOut;

    /**
     * Holds the stream the decoder fills with remote video and the application reads.
     */
    private final VideoInputStream videoIn;

    /**
     * Holds the transport-layer state for this call: the ICE agent, the DTLS endpoint, and the SCTP
     * and DCEP transport. Created at construction and closed by {@link #end(CallEndReason, String)}.
     */
    private final ActiveCallTransport transport;

    /**
     * Holds the media-plane session driving this call once attached, or {@code null} before bring-up.
     *
     * <p>This is a {@link VoiceCallSession} for a one-to-one call or a {@link GroupCallSession} for a
     * group call; the runtime drives both uniformly through {@link CallMediaSession} and narrows back to
     * the concrete type through {@link #voiceSession()} and {@link #groupSession()} only where the
     * topology-specific surface is needed.
     */
    private volatile CallMediaSession session;

    /**
     * Holds the most recent end-to-end rekey bundle delivered through {@link #applyRekey(E2eRekeyPayload)}.
     */
    private volatile E2eRekeyPayload latestRekey;

    /**
     * Tracks whether the peer has accepted this outbound offer, gating the caller-side peer handshake.
     */
    private volatile boolean peerAccepted;

    /**
     * Holds the finalized relay block delivered in the peer's {@code <accept>} stanza, or {@code null}
     * when the accept carried none.
     *
     * <p>The {@code <relay>} block in the offer ACK is allocated before the callee answers and carries
     * placeholder edgeray credentials the edgeray does not yet honor; the {@code <accept>} delivers the
     * finalized relay block whose {@code <key>} and {@code <auth_token>} the edgeray accepts for the
     * caller's WebRTC ICE leg. The caller's web relay bring-up keys its ICE connectivity checks from
     * this block when present, falling back to the offer-ACK block only for the native raw-UDP path.
     */
    private volatile com.github.auties00.cobalt.call.signaling.CallRelay acceptRelay;

    /**
     * Holds the full device {@link Jid} of the peer participant that answered this outbound one-to-one
     * call, captured from the {@code from} of its {@code preaccept} or {@code accept} stanza, or
     * {@code null} before any answer arrives.
     *
     * <p>This is the device that actually streams media (for example {@code 258252122116273:71@lid}),
     * which is not necessarily the relay roster's {@code peer_pid} entry: the relay block keys
     * {@code peer_pid} to the bare user JID ({@code 258252122116273@lid}), whereas the peer encrypts its
     * end-to-end media payload (Family B) with its own device JID as the HKDF info. Keying the inbound
     * SRTP master from this device JID rather than the bare user JID is what lets the peer's audio
     * authenticate and decrypt.
     */
    private volatile Jid peerDeviceJid;

    /**
     * Constructs a runtime bound to a call and its four media streams.
     *
     * @param engine   the owning engine
     * @param call     the public data view
     * @param audioOut the local-audio stream
     * @param audioIn  the remote-audio stream
     * @param videoOut the local-video stream
     * @param videoIn  the remote-video stream
     * @throws NullPointerException if any argument is {@code null}
     */
    public CallRuntime(CallService engine, Call call,
                       AudioOutputStream audioOut, AudioInputStream audioIn,
                       VideoOutputStream videoOut, VideoInputStream videoIn) {
        this.engine = Objects.requireNonNull(engine, "engine cannot be null");
        this.call = Objects.requireNonNull(call, "call cannot be null");
        this.audioOut = Objects.requireNonNull(audioOut, "audioOut cannot be null");
        this.audioIn = Objects.requireNonNull(audioIn, "audioIn cannot be null");
        this.videoOut = Objects.requireNonNull(videoOut, "videoOut cannot be null");
        this.videoIn = Objects.requireNonNull(videoIn, "videoIn cannot be null");
        this.transport = new ActiveCallTransport();
        this.stats = new CallStats(call.callId(),
                call.isOutgoing() ? CallSide.CALLER : CallSide.CALLEE,
                call.isVideo(), Instant.now());
    }

    /**
     * Returns the public data view this runtime drives.
     *
     * @return the call
     */
    public Call call() {
        return call;
    }

    /**
     * Returns the transport-layer state machine for this call.
     *
     * @return the transport
     */
    public ActiveCallTransport transport() {
        return transport;
    }

    /**
     * Returns the stream the application writes local audio into.
     *
     * @return the local-audio stream
     */
    public AudioOutputStream audioOut() {
        return audioOut;
    }

    /**
     * Returns the stream the application reads remote audio from.
     *
     * @return the remote-audio stream
     */
    public AudioInputStream audioIn() {
        return audioIn;
    }

    /**
     * Returns the stream the application writes local video into.
     *
     * @return the local-video stream
     */
    public VideoOutputStream videoOut() {
        return videoOut;
    }

    /**
     * Returns the stream the application reads remote video from.
     *
     * @return the remote-video stream
     */
    public VideoInputStream videoIn() {
        return videoIn;
    }

    /**
     * Drains the next outbound audio frame for the codec pipeline, skipping frames while audio is
     * muted.
     *
     * @return the next frame to encode, or {@code null} on end-of-stream
     * @throws InterruptedException if the calling thread is interrupted while waiting
     */
    public AudioFrame takeOutboundAudio() throws InterruptedException {
        while (true) {
            var frame = audioOut.take();
            if (frame == null) {
                return null;
            }
            if (call.isAudioMuted()) {
                continue;
            }
            return frame;
        }
    }

    /**
     * Drains the next outbound video frame for the codec pipeline, skipping frames while video is
     * muted.
     *
     * @return the next frame to encode, or {@code null} on end-of-stream
     * @throws InterruptedException if the calling thread is interrupted while waiting
     */
    public VideoFrame takeOutboundVideo() throws InterruptedException {
        while (true) {
            var frame = videoOut.take();
            if (frame == null) {
                return null;
            }
            if (call.isVideoMuted()) {
                continue;
            }
            return frame;
        }
    }

    /**
     * Delivers a decoded peer audio frame for the application to consume.
     *
     * <p>A {@code null} frame or a frame delivered after the call has ended is ignored.
     *
     * @param frame the decoded frame
     */
    public void deliverInboundAudio(AudioFrame frame) {
        if (frame == null || call.state() == CallState.ENDED) {
            return;
        }
        audioIn.offer(frame);
    }

    /**
     * Delivers a decoded peer video frame for the application to consume.
     *
     * <p>A {@code null} frame or a frame delivered after the call has ended is ignored.
     *
     * @param frame the decoded frame
     */
    public void deliverInboundVideo(VideoFrame frame) {
        if (frame == null || call.state() == CallState.ENDED) {
            return;
        }
        videoIn.offer(frame);
    }

    /**
     * Records that the peer accepted the outbound offer, gating the caller-side peer handshake.
     */
    public void onPeerAccept() {
        peerAccepted = true;
    }

    /**
     * Stores the finalized relay block from the peer's {@code <accept>} stanza.
     *
     * @param relay the accept-stanza relay block, or {@code null} when the accept carried none
     */
    public void setAcceptRelay(com.github.auties00.cobalt.call.signaling.CallRelay relay) {
        this.acceptRelay = relay;
    }

    /**
     * Returns the finalized relay block delivered in the peer's {@code <accept>} stanza.
     *
     * @return the accept relay block, or {@link java.util.Optional#empty()} when none was delivered
     */
    public java.util.Optional<com.github.auties00.cobalt.call.signaling.CallRelay> acceptRelay() {
        return java.util.Optional.ofNullable(acceptRelay);
    }

    /**
     * Records the full device {@link Jid} of the peer participant that answered this call, taken from
     * the {@code from} of its {@code preaccept} or {@code accept} stanza.
     *
     * <p>The first non-{@code null} answer wins; later announcements from the same device are ignored so
     * a {@code preaccept} followed by an {@code accept} does not churn the value. This device JID keys
     * the inbound end-to-end media SRTP master (Family B).
     *
     * @param jid the answering peer's device JID, ignored when {@code null}
     */
    public void setPeerDeviceJid(Jid jid) {
        if (jid == null) {
            return;
        }
        // Prefer a device-suffixed JID. The peer keys its end-to-end media (Family B) with the
        // answering device JID, so the inbound SRTP master must be derived from the <lid>:<device>@lid
        // form. Both the device-less aggregate JID and the answering device's JID can arrive on call
        // stanzas in either order, so a device-less value must never shadow the device form: accept the
        // first value when none is set, but always upgrade a device-less value to a device-suffixed one.
        var current = peerDeviceJid;
        if (current == null || (!current.hasDevice() && jid.hasDevice())) {
            peerDeviceJid = jid;
        }
    }

    /**
     * Returns the full device {@link Jid} of the peer participant that answered this call, when known.
     *
     * @return the answering peer's device JID, or {@link Optional#empty()} before any answer arrives
     */
    public Optional<Jid> peerDeviceJid() {
        return Optional.ofNullable(peerDeviceJid);
    }

    /**
     * Returns whether the peer has accepted this outbound offer.
     *
     * @return {@code true} once {@link #onPeerAccept()} has run
     */
    public boolean peerAccepted() {
        return peerAccepted;
    }

    /**
     * Promotes the call from {@link CallState#CONNECTING} to {@link CallState#ACTIVE}.
     *
     * <p>Invoked by the media session once its handshake completes and the RTP pipelines are wired; a
     * no-op when the call is not currently connecting.
     */
    public void notifyActive() {
        if (call.state() == CallState.CONNECTING) {
            call.setState(CallState.ACTIVE);
            stats.markConnected();
        }
    }

    /**
     * Claims the one-time right to bring up this call's media plane.
     *
     * <p>The first invocation returns {@code true}; every later invocation returns {@code false}, so a
     * relay block delivered both inline in the offer and again in a {@code <group_update>} push starts
     * the transport only once.
     *
     * @return {@code true} on the first invocation, {@code false} thereafter
     */
    public boolean beginMediaPlane() {
        return mediaPlaneStarted.compareAndSet(false, true);
    }

    /**
     * Records the 32-byte per-call shared key for this call, replacing any previously-set key.
     *
     * @param key the call key, defensively copied; {@code null} clears it
     */
    public void setCallKey(byte[] key) {
        this.callKey = key == null ? null : key.clone();
    }

    /**
     * Returns the 32-byte per-call shared key, or {@code null} when none has been set.
     *
     * @return the call key, or {@code null}
     */
    public byte[] callKey() {
        return callKey;
    }

    /**
     * Parks the deferred caller-side media bring-up continuation, to be run once by
     * {@link #runPendingMediaPlane()}.
     *
     * @param continuation the continuation to park
     */
    public void setPendingMediaPlane(Runnable continuation) {
        pendingMediaPlane.set(continuation);
    }

    /**
     * Runs the parked media bring-up continuation on a fresh virtual thread, if one is parked and has
     * not already been run.
     *
     * <p>The continuation is removed atomically before it runs, so a relay-allocation/accept race runs
     * the deferred steps exactly once regardless of which side observes both conditions first.
     */
    public void runPendingMediaPlane() {
        var continuation = pendingMediaPlane.getAndSet(null);
        if (continuation != null) {
            Thread.ofVirtual().name("call-bringup-complete-" + call.callId()).start(continuation);
        }
    }

    /**
     * Returns the per-call telemetry accumulator owned by this runtime.
     *
     * @return the telemetry accumulator
     */
    CallStats stats() {
        return stats;
    }

    /**
     * Attaches the media-plane session driving this call, closing any previously-attached session.
     *
     * @param session the media-plane session, a {@link VoiceCallSession} or a {@link GroupCallSession}
     * @throws NullPointerException if {@code session} is {@code null}
     */
    public void attachSession(CallMediaSession session) {
        Objects.requireNonNull(session, "session cannot be null");
        var previous = this.session;
        this.session = session;
        if (previous != null && previous != session) {
            try {
                previous.close();
            } catch (RuntimeException _) {
            }
        }
    }

    /**
     * Returns the attached media-plane session when it is a one-to-one {@link VoiceCallSession}.
     *
     * @return the voice session, or {@link Optional#empty()} when none is attached or the attached
     *         session drives a group call
     */
    public Optional<VoiceCallSession> voiceSession() {
        return session instanceof VoiceCallSession voice ? Optional.of(voice) : Optional.empty();
    }

    /**
     * Returns the attached media-plane session when it is a {@link GroupCallSession}.
     *
     * @return the group session, or {@link Optional#empty()} when none is attached or the attached
     *         session drives a one-to-one call
     */
    public Optional<GroupCallSession> groupSession() {
        return session instanceof GroupCallSession group ? Optional.of(group) : Optional.empty();
    }

    /**
     * Applies a freshly-published end-to-end rekey bundle, routing it to the attached media-plane
     * session.
     *
     * @param rekey the rekey bundle
     * @throws NullPointerException if {@code rekey} is {@code null}
     */
    public void applyRekey(E2eRekeyPayload rekey) {
        Objects.requireNonNull(rekey, "rekey cannot be null");
        this.latestRekey = rekey;
        var current = this.session;
        if (current != null) {
            current.applyRekey(rekey);
        }
    }

    /**
     * Requests a fresh video key frame from the peer, routing the request to the attached media-plane
     * session.
     *
     * <p>Returns without effect when no media session is attached yet; the session itself is a no-op when
     * it has no inbound video stream (see
     * {@link com.github.auties00.cobalt.call.session.CallMediaSession#requestKeyframe()}).
     */
    public void requestKeyframe() {
        var current = this.session;
        if (current != null) {
            current.requestKeyframe();
        }
    }

    /**
     * Returns the most recent rekey bundle, if any.
     *
     * @return the latest bundle, or {@link Optional#empty()} when none has been observed
     */
    public Optional<E2eRekeyPayload> latestRekey() {
        return Optional.ofNullable(latestRekey);
    }

    /**
     * Ends the call in response to the peer, mapping the wire reason to a typed {@link CallEndReason}.
     *
     * @param wireReason the {@code reason} attribute the peer sent, or {@code null}
     */
    public void onPeerEnded(String wireReason) {
        end(CallEndReason.fromWireValue(wireReason), wireReason);
    }

    /**
     * Drives the call to {@link CallState#ENDED} and tears down its runtime.
     *
     * <p>Records the end reason on the {@link Call}, shuts down the four media streams so blocked
     * application reads and writes return, closes the attached session and the transport, then
     * unregisters the call from the engine and fires the call-ended notification. Idempotent: a second
     * invocation after the call has ended returns without effect.
     *
     * @param reason     the canonical end reason to record
     * @param wireReason the wire-level reason for listener notifications
     */
    public synchronized void end(CallEndReason reason, String wireReason) {
        if (call.state() == CallState.ENDED) {
            return;
        }
        call.setState(CallState.ENDED);
        call.setEndReason(reason);
        stats.markEnded();
        audioOut.shutdown();
        audioIn.shutdown();
        videoOut.shutdown();
        videoIn.shutdown();
        var current = this.session;
        if (current != null) {
            try {
                current.close();
            } catch (RuntimeException _) {
            }
            this.session = null;
        }
        try {
            transport.close();
        } catch (RuntimeException _) {
        }
        engine.unregister(call.callId());
        engine.notifyEnded(call.callId(), call.peer(), wireReason);
    }
}
