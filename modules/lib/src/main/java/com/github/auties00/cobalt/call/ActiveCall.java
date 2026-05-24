package com.github.auties00.cobalt.call;

import com.github.auties00.cobalt.call.frame.audio.*;
import com.github.auties00.cobalt.call.frame.video.*;
import com.github.auties00.cobalt.call.internal.session.VoiceCallSession;
import com.github.auties00.cobalt.call.CallEndReason;
import com.github.auties00.cobalt.call.CallInteraction;
import com.github.auties00.cobalt.call.internal.transport.ActiveCallTransport;
import com.github.auties00.cobalt.model.jid.Jid;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.LinkedBlockingDeque;
import com.github.auties00.cobalt.call.internal.CallService;

/**
 * A single in-progress or recently-ended call (post-accept on the
 * inbound path or post-place on the outbound path) — the four-port
 * object the rest of Cobalt's call layer hides behind. Outbound media
 * is driven by writing into {@link #localAudioSink()} /
 * {@link #localVideoSink()}; inbound media is consumed by reading
 * from {@link #remoteAudioSource()} / {@link #remoteVideoSource()}.
 *
 * <p>Cobalt itself never owns the OS microphone, camera, speaker, or
 * render surface. Mic/camera capture lives in the
 * {@code cobalt-media-local} companion module; bots and servers
 * supply their own producers/consumers (file streams, synthetic
 * generators, bridges between calls).
 *
 * <p>Lifecycle:
 *
 * <pre>{@code
 *   try (var call = client.startCall(peer, true)) {
 *       call.awaitState(CallState.ACTIVE);
 *
 *       Thread.startVirtualThread(() -> pump(mic,    call.localAudioSink()));
 *       Thread.startVirtualThread(() -> pump(camera, call.localVideoSink()));
 *       Thread.startVirtualThread(() -> pump(call.remoteAudioSource(), speaker));
 *       Thread.startVirtualThread(() -> pump(call.remoteVideoSource(), window));
 *
 *       call.awaitEnded();
 *   }
 * }</pre>
 *
 * <p>The call is {@link AutoCloseable}; {@link #close} hangs up if
 * the call is still active.
 *
 * <p>Threading model: state is mutated under {@link #lock}; reads /
 * awaits use {@code synchronized(lock)} + {@link Object#wait()} /
 * {@link Object#notifyAll()}. The four media ports are themselves
 * thread-safe (sinks delegate to a {@link LinkedBlockingDeque} and
 * sources block on it), so application-side producer/consumer
 * threads never need to coordinate with the engine thread.
 *
 * <p>Media-port semantics today: writes to the local sinks accept
 * frames into a bounded queue, and the remote sources block on
 * {@link AudioSource#next()} / {@link VideoSource#next()} until a
 * frame is pushed. The transport (#76 + #77) and pipelines (#61 +
 * #62) wire into these queues to drain locally-captured frames into
 * the encoder and to feed decoded peer frames back out — until then
 * the queues are simply storage that nobody else touches, which
 * means writes succeed silently and reads block until the call
 * ends. Hangup unblocks pending readers via end-of-stream sentinels.
 */
public final class ActiveCall implements AutoCloseable {
    /**
     * Bounded capacity for the per-call media queues. Ten 10-ms
     * frames is 100 ms of buffering at WhatsApp's default 16 kHz
     * mono Opus profile — more than enough for the engine's tick
     * cadence without growing unbounded if a consumer stalls.
     */
    private static final int MEDIA_QUEUE_CAPACITY = 10;

    /**
     * Sentinel pushed into the audio queue at close time so a
     * blocked {@link AudioSource#next()} returns {@code null}.
     */
    private static final AudioFrame SENTINEL_AUDIO = new AudioFrame(new short[0], -1L);

    /**
     * Sentinel pushed into the video queue at close time.
     */
    private static final VideoFrame SENTINEL_VIDEO = new VideoFrame(new byte[6], 2, 2, -1L);

    /**
     * The owning engine. Used to send signaling stanzas, fire
     * {@code onCallEnded}, and unregister the session at teardown.
     */
    private final CallService engine;

    /**
     * The call's unique identifier — assigned by the local user for
     * outbound calls, by the remote peer for inbound.
     */
    private final String callId;

    /**
     * The peer JID — who we're talking to.
     */
    private final Jid peer;

    /**
     * The chat the call's log entry belongs to: peer JID for 1:1,
     * group JID for group calls.
     */
    @SuppressWarnings("unused")
    private final Jid chatJid;

    /**
     * The call creator JID. {@code self} for outbound calls,
     * {@link #peer} for inbound. Used as the {@code call-creator}
     * attribute on all outgoing signaling stanzas.
     */
    private final Jid creator;

    /**
     * {@code true} for outbound calls (we placed it),
     * {@code false} for inbound (we accepted it).
     */
    private final boolean outgoing;

    /**
     * The local side's call options (audio-only / audio+video,
     * dimensions, bitrate).
     */
    private final CallOptions options;

    /**
     * Bounded queue receiving locally-captured audio frames; drained
     * by the audio pipeline (#61) when wired.
     */
    private final LinkedBlockingDeque<AudioFrame> outboundAudio = new LinkedBlockingDeque<>(MEDIA_QUEUE_CAPACITY);

    /**
     * Bounded queue receiving locally-captured video frames.
     */
    private final LinkedBlockingDeque<VideoFrame> outboundVideo = new LinkedBlockingDeque<>(MEDIA_QUEUE_CAPACITY);

    /**
     * Bounded queue holding decoded peer audio frames; the audio
     * pipeline pushes here, the application drains via
     * {@link #remoteAudioSource}.
     */
    private final LinkedBlockingDeque<AudioFrame> inboundAudio = new LinkedBlockingDeque<>(MEDIA_QUEUE_CAPACITY);

    /**
     * Bounded queue holding decoded peer video frames.
     */
    private final LinkedBlockingDeque<VideoFrame> inboundVideo = new LinkedBlockingDeque<>(MEDIA_QUEUE_CAPACITY);

    /**
     * Lock guarding {@link #state}, {@link #endReason},
     * {@link #audioMuted} and {@link #videoMuted}; also serves as
     * the monitor for state-change waiters in {@link #awaitState}.
     */
    private final Object lock = new Object();

    /**
     * Current call state. Mutated under {@link #lock} only.
     */
    private CallState state = CallState.CONNECTING;

    /**
     * Reason the call ended; populated when {@link #state} becomes
     * {@link CallState#ENDED}.
     */
    private CallEndReason endReason;

    /**
     * {@code true} if the local mic is muted.
     */
    private boolean audioMuted = false;

    /**
     * {@code true} if local video is muted/disabled.
     */
    private boolean videoMuted;

    /**
     * Sink the application writes captured mic frames into.
     */
    private final AudioSink localAudioSink = new LocalAudioSink();

    /**
     * Sink the application writes captured camera frames into.
     */
    private final VideoSink localVideoSink = new LocalVideoSink();

    /**
     * Source the application reads decoded peer audio from.
     */
    private final AudioSource remoteAudioSource = new RemoteAudioSource();

    /**
     * Source the application reads decoded peer video from.
     */
    private final VideoSource remoteVideoSource = new RemoteVideoSource();

    /**
     * Transport-layer state for this call: ICE agent + DTLS-SRTP
     * endpoint + SCTP/DCEP transport. Created at construction in the
     * {@link ActiveCallTransport.State#IDLE}
     * state and {@link ActiveCallTransport#close()
     * closed} on {@link #hangup()}.
     */
    private final ActiveCallTransport transport;

    /**
     * Constructs a new live session.
     *
     * @param engine   owning engine
     * @param callId   unique call identifier
     * @param peer     peer JID
     * @param chatJid  chat JID (peer for 1:1, group for group)
     * @param creator  call-creator JID (self for outbound, peer for inbound)
     * @param outgoing {@code true} for outbound, {@code false} for inbound
     * @param options  the local-side options
     */
    public ActiveCall(CallService engine, String callId, Jid peer, Jid chatJid,
                      Jid creator, boolean outgoing, CallOptions options) {
        this.engine = Objects.requireNonNull(engine, "engine cannot be null");
        this.callId = Objects.requireNonNull(callId, "callId cannot be null");
        this.peer = Objects.requireNonNull(peer, "peer cannot be null");
        this.chatJid = Objects.requireNonNull(chatJid, "chatJid cannot be null");
        this.creator = Objects.requireNonNull(creator, "creator cannot be null");
        this.outgoing = outgoing;
        this.options = Objects.requireNonNull(options, "options cannot be null");
        this.videoMuted = !options.videoEnabled();
        this.transport = new ActiveCallTransport();
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
     * Returns the JID of the peer on the other end. Always set, even
     * before the call connects.
     *
     * @return the peer JID
     */
    public Jid peer() {
        return peer;
    }

    /**
     * Returns the call options the call was created with.
     *
     * @return the options
     */
    public CallOptions options() {
        return options;
    }

    /**
     * Returns the current call state. Snapshot semantics — for
     * coordinated waits, prefer {@link #awaitState} or
     * {@link #awaitEnded}.
     *
     * @return the current state
     */
    public CallState state() {
        synchronized (lock) {
            return state;
        }
    }

    /**
     * Blocks until the call reaches {@code target} (or any later
     * state — e.g. waiting for {@link CallState#ACTIVE} returns
     * immediately if the call has already moved to
     * {@link CallState#ENDED}).
     *
     * @param target the state to wait for
     * @throws InterruptedException if the calling thread is
     *                              interrupted while waiting
     */
    public void awaitState(CallState target) throws InterruptedException {
        Objects.requireNonNull(target, "target cannot be null");
        synchronized (lock) {
            while (state.ordinal() < target.ordinal()) {
                lock.wait();
            }
        }
    }

    /**
     * Blocks until the call reaches {@link CallState#ENDED}.
     *
     * @throws InterruptedException if the calling thread is
     *                              interrupted while waiting
     */
    public void awaitEnded() throws InterruptedException {
        awaitState(CallState.ENDED);
    }

    /**
     * Returns the reason the call ended, present only once
     * {@link #state()} is {@link CallState#ENDED}.
     *
     * @return the end reason, or empty while the call is active
     */
    public Optional<CallEndReason> endReason() {
        synchronized (lock) {
            return Optional.ofNullable(endReason);
        }
    }

    /**
     * Returns the sink to push outbound audio frames into.
     *
     * @return the local-audio sink
     */
    public AudioSink localAudioSink() {
        return localAudioSink;
    }

    /**
     * Returns the sink to push outbound video frames into.
     *
     * @return the local-video sink
     */
    public VideoSink localVideoSink() {
        return localVideoSink;
    }

    /**
     * Returns the source to pull inbound audio frames from.
     *
     * @return the remote-audio source
     */
    public AudioSource remoteAudioSource() {
        return remoteAudioSource;
    }

    /**
     * Returns the source to pull inbound video frames from.
     *
     * @return the remote-video source
     */
    public VideoSource remoteVideoSource() {
        return remoteVideoSource;
    }

    /**
     * Mutes or unmutes outbound media. Both flags can be set
     * independently — {@code mute(true, false)} silences the mic
     * while still sending video.
     *
     * @param audio {@code true} to silence the audio sink
     * @param video {@code true} to silence the video sink
     */
    public void mute(boolean audio, boolean video) {
        boolean audioChanged;
        boolean videoChanged;
        synchronized (lock) {
            audioChanged = audio != audioMuted;
            videoChanged = video != videoMuted;
            audioMuted = audio;
            videoMuted = video;
        }
        if (audioChanged) {
            engine.sendMute(peer, creator, callId, audio);
        }
        if (videoChanged && options.videoEnabled()) {
            engine.sendVideoState(peer, creator, callId, !video);
        }
    }

    /**
     * Initiates the M4 mid-call video upgrade — sends a
     * video-state-on stanza asking the peer to switch to a video
     * call. The peer's reply flows through
     * {@link com.github.auties00.cobalt.client.WhatsAppClientListener#onCallVideoStateChanged
     * onCallVideoStateChanged} (acceptance) or
     * {@link com.github.auties00.cobalt.client.WhatsAppClientListener#onCallEnded
     * onCallEnded} (rejection).
     */
    public void requestVideoUpgrade() {
        synchronized (lock) {
            if (state == CallState.ENDED) {
                return;
            }
        }
        engine.sendVideoUpgradeRequest(peer, creator, callId);
    }

    /**
     * Acknowledges a peer-initiated video upgrade — sends the
     * affirmative video-state-on stanza, after which the call
     * layer is expected to start a video track on its
     * {@link VoiceCallSession}.
     */
    public void acceptVideoUpgrade() {
        synchronized (lock) {
            if (state == CallState.ENDED) {
                return;
            }
            videoMuted = false;
        }
        engine.sendVideoState(peer, creator, callId, true);
    }

    /**
     * Declines a peer-initiated video upgrade — sends the
     * negative video-state stanza so the peer knows to keep its
     * video track suppressed.
     */
    public void rejectVideoUpgrade() {
        synchronized (lock) {
            if (state == CallState.ENDED) {
                return;
            }
        }
        engine.sendVideoUpgradeReject(peer, creator, callId);
    }

    /**
     * Broadcasts an emoji reaction to the call. Other participants
     * see it via
     * {@link com.github.auties00.cobalt.client.WhatsAppClientListener#onCallInteraction
     * onCallInteraction}.
     *
     * @param emoji the emoji glyph (typically a single grapheme)
     */
    public void sendReaction(String emoji) {
        sendInteraction(new CallInteraction.Reaction(emoji));
    }

    /**
     * Sends a "raise hand" gesture — UI hint shown to other
     * participants in a group call.
     */
    public void raiseHand() {
        sendInteraction(new CallInteraction.RaiseHand());
    }

    /**
     * Sends a "lower hand" gesture, clearing a previously-raised
     * hand.
     */
    public void lowerHand() {
        sendInteraction(new CallInteraction.LowerHand());
    }

    /**
     * Asks the peer to mute themselves — admin-style request.
     *
     * @param target the participant being asked to mute (their JID
     *               in string form)
     */
    public void requestPeerMute(String target) {
        sendInteraction(new CallInteraction.PeerMuteRequest(target,
                Optional.empty()));
    }

    /**
     * Asks the peer to emit a video keyframe — used after a
     * stream restart, decoder reset, or detected packet loss.
     */
    public void requestVideoKeyFrame() {
        sendInteraction(new CallInteraction.KeyFrameRequest());
    }

    /**
     * Sends one in-call interaction stanza. No-op once the call
     * has ended.
     *
     * @param interaction the interaction
     */
    public void sendInteraction(CallInteraction interaction) {
        Objects.requireNonNull(interaction, "interaction cannot be null");
        synchronized (lock) {
            if (state == CallState.ENDED) {
                return;
            }
        }
        engine.sendInteraction(peer, creator, callId, interaction);
    }

    /**
     * Hangs up the call locally, sending the
     * {@code <call><terminate/></call>} stanza. No-op if the call is
     * already ended.
     */
    public void hangup() {
        boolean alreadyEnded;
        synchronized (lock) {
            alreadyEnded = state == CallState.ENDED;
        }
        if (alreadyEnded) {
            return;
        }
        engine.sendTerminate(peer, creator, callId, CallEndReason.HANGUP);
        end(CallEndReason.HANGUP, "hangup");
    }

    /**
     * Hangs up the call (no-op if already ended). Lets
     * {@link ActiveCall} compose with try-with-resources.
     */
    @Override
    public void close() {
        hangup();
    }

    /**
     * Called by {@link CallService} when the peer accepts our
     * outbound offer. The {@link CallState#CONNECTING}-to-
     * {@link CallState#ACTIVE} transition is added by tasks #76–#78
     * + #61–#62 once media is flowing.
     */
    public void onPeerAccept() {
    }

    /**
     * Called by {@link CallService} when the peer ends the call —
     * either rejected our outbound offer, hung up an in-flight
     * call, or timed out.
     *
     * @param wireReason the {@code reason} attribute the peer sent,
     *                   or {@code null}
     */
    public void onPeerEnded(String wireReason) {
        end(CallEndReason.fromWireValue(wireReason), wireReason);
    }

    /**
     * Drives the call to {@link CallState#ENDED}, sets the end
     * reason, unregisters from the engine, fires onCallEnded, and
     * unblocks any in-flight media reads via sentinels.
     *
     * @param reason     the canonical end reason to record
     * @param wireReason the wire-level reason for listener
     *                   notifications
     */
    private void end(CallEndReason reason, String wireReason) {
        synchronized (lock) {
            if (state == CallState.ENDED) {
                return;
            }
            state = CallState.ENDED;
            endReason = reason;
            lock.notifyAll();
        }
        outboundAudio.offer(SENTINEL_AUDIO);
        outboundVideo.offer(SENTINEL_VIDEO);
        inboundAudio.offer(SENTINEL_AUDIO);
        inboundVideo.offer(SENTINEL_VIDEO);
        try { transport.close(); } catch (RuntimeException _) { /* swallow */ }
        engine.unregister(callId);
        engine.notifyEnded(callId, peer, wireReason);
    }

    /**
     * Pushes a decoded peer audio frame into the inbound queue.
     * Used by the codec pipeline (#61) and the RTP transport (#78)
     * to deliver remote PCM into {@link #remoteAudioSource()}.
     *
     * @param frame the decoded frame
     */
    public void deliverInboundAudio(AudioFrame frame) {
        if (frame == null || state() == CallState.ENDED) {
            return;
        }
        inboundAudio.offer(frame);
    }

    /**
     * Pushes a decoded peer video frame. Used by the video codec
     * pipeline (#62) and the RTP transport (#78).
     *
     * @param frame the decoded frame
     */
    public void deliverInboundVideo(VideoFrame frame) {
        if (frame == null || state() == CallState.ENDED) {
            return;
        }
        inboundVideo.offer(frame);
    }

    /**
     * Drains the next outbound audio frame for the codec pipeline
     * (#61) to encode. Returns {@code null} on end-of-stream.
     *
     * @return the next frame, or {@code null}
     * @throws InterruptedException if interrupted
     */
    public AudioFrame takeOutboundAudio() throws InterruptedException {
        var frame = outboundAudio.take();
        return frame == SENTINEL_AUDIO ? null : frame;
    }

    /**
     * Drains the next outbound video frame for the codec pipeline
     * (#62) to encode. Returns {@code null} on end-of-stream.
     *
     * @return the next frame, or {@code null}
     * @throws InterruptedException if interrupted
     */
    public VideoFrame takeOutboundVideo() throws InterruptedException {
        var frame = outboundVideo.take();
        return frame == SENTINEL_VIDEO ? null : frame;
    }

    /**
     * Returns the call identifier.
     *
     * @return the call id
     */
    public String callId() {
        return callId;
    }

    /**
     * Returns the JID of the call creator — the side that emitted
     * the original {@code <call><offer/></call>} stanza. For an
     * outbound call this equals the local user's JID; for inbound
     * it's the peer.
     *
     * @return the call creator JID
     */
    public Jid creator() {
        return creator;
    }

    /**
     * Returns whether this is an outbound call.
     *
     * @return {@code true} for outgoing
     */
    @SuppressWarnings("unused")
    boolean outgoing() {
        return outgoing;
    }

    /**
     * Returns whether outgoing audio is muted.
     *
     * @return {@code true} if audio is muted
     */
    @SuppressWarnings("unused")
    boolean isAudioMuted() {
        synchronized (lock) {
            return audioMuted;
        }
    }

    /**
     * Returns whether outgoing video is muted/disabled.
     *
     * @return {@code true} if video is muted
     */
    @SuppressWarnings("unused")
    boolean isVideoMuted() {
        synchronized (lock) {
            return videoMuted;
        }
    }

    /**
     * Local-audio sink: drops frames silently when muted, otherwise
     * blocks-on-full to apply natural backpressure.
     */
    private final class LocalAudioSink implements AudioSink {
        @Override
        public void write(AudioFrame frame) throws InterruptedException {
            Objects.requireNonNull(frame, "frame cannot be null");
            if (state() == CallState.ENDED || isAudioMuted()) {
                return;
            }
            outboundAudio.put(frame);
        }
    }

    /**
     * Local-video sink: drops frames silently when muted/disabled,
     * otherwise blocks-on-full.
     */
    private final class LocalVideoSink implements VideoSink {
        @Override
        public void write(VideoFrame frame) throws InterruptedException {
            Objects.requireNonNull(frame, "frame cannot be null");
            if (state() == CallState.ENDED || isVideoMuted()) {
                return;
            }
            outboundVideo.put(frame);
        }
    }

    /**
     * Remote-audio source: blocks until a decoded frame is pushed,
     * returns {@code null} on end-of-stream sentinel.
     */
    private final class RemoteAudioSource implements AudioSource {
        @Override
        public AudioFrame next() throws InterruptedException {
            var frame = inboundAudio.take();
            return frame == SENTINEL_AUDIO ? null : frame;
        }
    }

    /**
     * Remote-video source: same shape as
     * {@link RemoteAudioSource}.
     */
    private final class RemoteVideoSource implements VideoSource {
        @Override
        public VideoFrame next() throws InterruptedException {
            var frame = inboundVideo.take();
            return frame == SENTINEL_VIDEO ? null : frame;
        }
    }
}
