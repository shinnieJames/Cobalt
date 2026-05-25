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
 * Represents a single in-progress or recently-ended call: the four-port
 * media object the rest of Cobalt's call layer hides behind.
 *
 * <p>An instance exists from the moment an outbound call is placed or an
 * inbound offer is accepted until the call ends. Outbound media is driven
 * by writing into {@link #localAudioSink()} and {@link #localVideoSink()};
 * inbound media is consumed by reading from {@link #remoteAudioSource()}
 * and {@link #remoteVideoSource()}. Cobalt itself never owns the operating
 * system microphone, camera, speaker, or render surface: microphone and
 * camera capture live in a companion media module, and bots and servers
 * supply their own producers and consumers such as file streams, synthetic
 * generators, or bridges between calls.
 *
 * <p>The call is {@link AutoCloseable}; {@link #close()} hangs up if the
 * call is still active, so it composes with try-with-resources:
 *
 * {@snippet :
 *   try (var call = client.startCall(peer, CallOptions.video())) {
 *       call.awaitState(CallState.ACTIVE);
 *
 *       Thread.startVirtualThread(() -> pump(mic,    call.localAudioSink()));
 *       Thread.startVirtualThread(() -> pump(camera, call.localVideoSink()));
 *       Thread.startVirtualThread(() -> pump(call.remoteAudioSource(), speaker));
 *       Thread.startVirtualThread(() -> pump(call.remoteVideoSource(), window));
 *
 *       call.awaitEnded();
 *   }
 * }
 *
 * <p>State is mutated under {@link #lock}, and reads and awaits use a
 * {@code synchronized} block on the same monitor with
 * {@link Object#wait()} and {@link Object#notifyAll()}. The four media
 * ports are themselves thread-safe (the sinks delegate to a
 * {@link LinkedBlockingDeque} and the sources block on it), so
 * application-side producer and consumer threads never coordinate with the
 * engine thread. Writes to the local sinks accept frames into a bounded
 * queue; reads from the remote sources block on {@link AudioSource#next()}
 * and {@link VideoSource#next()} until a frame is pushed or, at hangup,
 * until an end-of-stream sentinel unblocks them and yields {@code null}.
 */
public final class ActiveCall implements AutoCloseable {
    /**
     * Bounds the per-call media queues to ten frames.
     *
     * @implNote This implementation buffers ten 10-ms frames, which is
     * 100 ms of audio at WhatsApp's default 16 kHz mono Opus profile;
     * enough to absorb the engine's tick cadence without growing unbounded
     * when a consumer stalls.
     */
    private static final int MEDIA_QUEUE_CAPACITY = 10;

    /**
     * Sentinel pushed into the audio queues at close time so a blocked
     * {@link AudioSource#next()} returns {@code null}.
     */
    private static final AudioFrame SENTINEL_AUDIO = new AudioFrame(new short[0], -1L);

    /**
     * Sentinel pushed into the video queues at close time so a blocked
     * {@link VideoSource#next()} returns {@code null}.
     */
    private static final VideoFrame SENTINEL_VIDEO = new VideoFrame(new byte[6], 2, 2, -1L);

    /**
     * Holds the owning engine, used to send signaling stanzas, fire the
     * call-ended notification, and unregister the session at teardown.
     */
    private final CallService engine;

    /**
     * Holds the call's unique identifier, assigned by the local user for
     * outbound calls and by the remote peer for inbound calls.
     */
    private final String callId;

    /**
     * Holds the JID of the peer on the other end of the call.
     */
    private final Jid peer;

    /**
     * Holds the chat the call's log entry belongs to: the peer JID for
     * one-to-one calls, the group JID for group calls.
     */
    @SuppressWarnings("unused")
    private final Jid chatJid;

    /**
     * Holds the call-creator JID: the local user for outbound calls,
     * {@link #peer} for inbound calls.
     *
     * <p>This value is placed on the call-creator attribute of every
     * outgoing signaling stanza.
     */
    private final Jid creator;

    /**
     * Indicates whether this is an outbound call: {@code true} when the
     * local user placed it, {@code false} when the local user accepted it.
     */
    private final boolean outgoing;

    /**
     * Holds the local side's call options: audio-only or audio-and-video,
     * picture dimensions, and bitrate.
     */
    private final CallOptions options;

    /**
     * Holds locally-captured audio frames awaiting encoding and transport.
     */
    private final LinkedBlockingDeque<AudioFrame> outboundAudio = new LinkedBlockingDeque<>(MEDIA_QUEUE_CAPACITY);

    /**
     * Holds locally-captured video frames awaiting encoding and transport.
     */
    private final LinkedBlockingDeque<VideoFrame> outboundVideo = new LinkedBlockingDeque<>(MEDIA_QUEUE_CAPACITY);

    /**
     * Holds decoded peer audio frames awaiting consumption through
     * {@link #remoteAudioSource()}.
     */
    private final LinkedBlockingDeque<AudioFrame> inboundAudio = new LinkedBlockingDeque<>(MEDIA_QUEUE_CAPACITY);

    /**
     * Holds decoded peer video frames awaiting consumption through
     * {@link #remoteVideoSource()}.
     */
    private final LinkedBlockingDeque<VideoFrame> inboundVideo = new LinkedBlockingDeque<>(MEDIA_QUEUE_CAPACITY);

    /**
     * Guards {@link #state}, {@link #endReason}, {@link #audioMuted}, and
     * {@link #videoMuted}, and serves as the monitor that
     * {@link #awaitState(CallState)} waiters block on.
     */
    private final Object lock = new Object();

    /**
     * Holds the current call state, mutated only under {@link #lock}.
     */
    private CallState state = CallState.CONNECTING;

    /**
     * Holds the reason the call ended, populated when {@link #state}
     * becomes {@link CallState#ENDED}.
     */
    private CallEndReason endReason;

    /**
     * Indicates whether the local microphone is muted.
     */
    private boolean audioMuted = false;

    /**
     * Indicates whether local video is muted or disabled.
     */
    private boolean videoMuted;

    /**
     * Holds the sink the application writes captured microphone frames
     * into.
     */
    private final AudioSink localAudioSink = new LocalAudioSink();

    /**
     * Holds the sink the application writes captured camera frames into.
     */
    private final VideoSink localVideoSink = new LocalVideoSink();

    /**
     * Holds the source the application reads decoded peer audio from.
     */
    private final AudioSource remoteAudioSource = new RemoteAudioSource();

    /**
     * Holds the source the application reads decoded peer video from.
     */
    private final VideoSource remoteVideoSource = new RemoteVideoSource();

    /**
     * Holds the transport-layer state for this call: the ICE agent, the
     * DTLS-SRTP endpoint, and the SCTP and DCEP transport.
     *
     * <p>It is created at construction in the
     * {@link ActiveCallTransport.State#IDLE} state and
     * {@link ActiveCallTransport#close() closed} on {@link #hangup()}.
     */
    private final ActiveCallTransport transport;

    /**
     * Constructs a live call session.
     *
     * @param engine   the owning engine
     * @param callId   the unique call identifier
     * @param peer     the peer JID
     * @param chatJid  the chat JID: peer for one-to-one, group for group
     *                 calls
     * @param creator  the call-creator JID: self for outbound, peer for
     *                 inbound
     * @param outgoing {@code true} for an outbound call, {@code false} for
     *                 inbound
     * @param options  the local-side options
     * @throws NullPointerException if {@code engine}, {@code callId},
     *                              {@code peer}, {@code chatJid},
     *                              {@code creator}, or {@code options} is
     *                              {@code null}
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
     * Returns the JID of the peer on the other end.
     *
     * <p>The peer is always set, even before the call connects.
     *
     * @return the peer JID
     */
    public Jid peer() {
        return peer;
    }

    /**
     * Returns the options the call was created with.
     *
     * @return the options
     */
    public CallOptions options() {
        return options;
    }

    /**
     * Returns the current call state.
     *
     * <p>The result is a point-in-time snapshot; for a coordinated wait,
     * prefer {@link #awaitState(CallState)} or {@link #awaitEnded()}.
     *
     * @return the current state
     */
    public CallState state() {
        synchronized (lock) {
            return state;
        }
    }

    /**
     * Blocks until the call reaches {@code target} or a later state.
     *
     * <p>Because {@link CallState} constants are ordered chronologically,
     * a wait for {@link CallState#ACTIVE} returns immediately when the call
     * has already moved on to {@link CallState#ENDED}.
     *
     * @param target the state to wait for
     * @throws NullPointerException if {@code target} is {@code null}
     * @throws InterruptedException if the calling thread is interrupted
     *                              while waiting
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
     * @throws InterruptedException if the calling thread is interrupted
     *                              while waiting
     */
    public void awaitEnded() throws InterruptedException {
        awaitState(CallState.ENDED);
    }

    /**
     * Returns the reason the call ended.
     *
     * <p>The reason is present only once {@link #state()} is
     * {@link CallState#ENDED}.
     *
     * @return the end reason, or {@link Optional#empty()} while the call is
     * active
     */
    public Optional<CallEndReason> endReason() {
        synchronized (lock) {
            return Optional.ofNullable(endReason);
        }
    }

    /**
     * Returns the sink the application pushes outbound audio frames into.
     *
     * @return the local-audio sink
     */
    public AudioSink localAudioSink() {
        return localAudioSink;
    }

    /**
     * Returns the sink the application pushes outbound video frames into.
     *
     * @return the local-video sink
     */
    public VideoSink localVideoSink() {
        return localVideoSink;
    }

    /**
     * Returns the source the application pulls inbound audio frames from.
     *
     * @return the remote-audio source
     */
    public AudioSource remoteAudioSource() {
        return remoteAudioSource;
    }

    /**
     * Returns the source the application pulls inbound video frames from.
     *
     * @return the remote-video source
     */
    public VideoSource remoteVideoSource() {
        return remoteVideoSource;
    }

    /**
     * Mutes or unmutes outbound media.
     *
     * <p>The two flags are set independently, so {@code mute(true, false)}
     * silences the microphone while still sending video. A mute change is
     * announced to the peer only when it differs from the current state,
     * and a video-state change is announced only when video is enabled for
     * the call.
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
     * Requests a mid-call upgrade of an audio-only call to audio-and-video.
     *
     * <p>The request asks the peer to switch to a video call; it is a no-op
     * once the call has ended. The peer's reply surfaces through
     * {@link com.github.auties00.cobalt.client.WhatsAppClientListener#onCallVideoStateChanged(com.github.auties00.cobalt.client.WhatsAppClient, String, Jid, boolean)}
     * on acceptance or
     * {@link com.github.auties00.cobalt.client.WhatsAppClientListener#onCallEnded(com.github.auties00.cobalt.client.WhatsAppClient, String, Jid, CallEndReason)}
     * on rejection.
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
     * Accepts a peer-initiated video upgrade.
     *
     * <p>The affirmative video-state stanza is sent and local video is
     * unmuted, after which the call layer starts a video track on its
     * {@link VoiceCallSession}. It is a no-op once the call has ended.
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
     * Declines a peer-initiated video upgrade.
     *
     * <p>The negative video-state stanza is sent so the peer keeps its
     * video track suppressed. It is a no-op once the call has ended.
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
     * Broadcasts an emoji reaction to the call.
     *
     * <p>Other participants observe the reaction through
     * {@link com.github.auties00.cobalt.client.WhatsAppClientListener#onCallInteraction(com.github.auties00.cobalt.client.WhatsAppClient, String, Jid, CallInteraction)}.
     *
     * @param emoji the emoji glyph, typically a single grapheme
     */
    public void sendReaction(String emoji) {
        sendInteraction(new CallInteraction.Reaction(emoji));
    }

    /**
     * Sends a raise-hand gesture as a UI hint to the other participants in
     * a group call.
     */
    public void raiseHand() {
        sendInteraction(new CallInteraction.RaiseHand());
    }

    /**
     * Sends a lower-hand gesture, clearing a previously-raised hand.
     */
    public void lowerHand() {
        sendInteraction(new CallInteraction.LowerHand());
    }

    /**
     * Asks a participant to mute themselves.
     *
     * @param target the participant being asked to mute, in string form of
     *               their JID
     */
    public void requestPeerMute(String target) {
        sendInteraction(new CallInteraction.PeerMuteRequest(target,
                Optional.empty()));
    }

    /**
     * Asks the peer to emit a video keyframe.
     *
     * <p>Useful after a stream restart, a decoder reset, or detected packet
     * loss.
     */
    public void requestVideoKeyFrame() {
        sendInteraction(new CallInteraction.KeyFrameRequest());
    }

    /**
     * Sends one in-call interaction stanza.
     *
     * <p>The call is a no-op once the call has ended.
     *
     * @param interaction the interaction
     * @throws NullPointerException if {@code interaction} is {@code null}
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
     * Hangs up the call locally, sending the call-termination stanza.
     *
     * <p>The call is a no-op if the call is already ended.
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
     * {@inheritDoc}
     *
     * <p>Hangs up the call, or does nothing if it is already ended.
     */
    @Override
    public void close() {
        hangup();
    }

    /**
     * Records that the peer accepted the outbound offer.
     *
     * <p>Invoked by {@link CallService} on acceptance.
     */
    // TODO: drive the CONNECTING-to-ACTIVE transition here once the
    //  transport and codec pipelines deliver flowing media.
    public void onPeerAccept() {
    }

    /**
     * Ends the call in response to the peer.
     *
     * <p>Invoked by {@link CallService} when the peer rejects an outbound
     * offer, hangs up an in-flight call, or times out.
     *
     * @param wireReason the {@code reason} attribute the peer sent, or
     *                   {@code null}
     */
    public void onPeerEnded(String wireReason) {
        end(CallEndReason.fromWireValue(wireReason), wireReason);
    }

    /**
     * Drives the call to {@link CallState#ENDED}.
     *
     * <p>Records the end reason, closes the transport, unregisters from the
     * engine, fires the call-ended notification, and unblocks any in-flight
     * media reads by pushing end-of-stream sentinels into every queue. It
     * is idempotent: a second invocation after the call has ended returns
     * without effect.
     *
     * @param reason     the canonical end reason to record
     * @param wireReason the wire-level reason for listener notifications
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
     * Delivers a decoded peer audio frame into the inbound queue.
     *
     * <p>A {@code null} frame or a frame delivered after the call has ended
     * is ignored. The frame becomes available through
     * {@link #remoteAudioSource()}.
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
     * Delivers a decoded peer video frame into the inbound queue.
     *
     * <p>A {@code null} frame or a frame delivered after the call has ended
     * is ignored. The frame becomes available through
     * {@link #remoteVideoSource()}.
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
     * Drains the next outbound audio frame for the codec pipeline to
     * encode.
     *
     * @return the next frame, or {@code null} on end-of-stream
     * @throws InterruptedException if the calling thread is interrupted
     *                              while waiting
     */
    public AudioFrame takeOutboundAudio() throws InterruptedException {
        var frame = outboundAudio.take();
        return frame == SENTINEL_AUDIO ? null : frame;
    }

    /**
     * Drains the next outbound video frame for the codec pipeline to
     * encode.
     *
     * @return the next frame, or {@code null} on end-of-stream
     * @throws InterruptedException if the calling thread is interrupted
     *                              while waiting
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
     * Returns the JID of the call creator: the side that emitted the
     * original call offer.
     *
     * <p>For an outbound call this equals the local user's JID; for an
     * inbound call it is the peer.
     *
     * @return the call-creator JID
     */
    public Jid creator() {
        return creator;
    }

    /**
     * Returns whether this is an outbound call.
     *
     * @return {@code true} for an outgoing call
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
     * Returns whether outgoing video is muted or disabled.
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
     * Sink for locally-captured audio frames.
     *
     * <p>Frames are dropped silently when the call has ended or the
     * microphone is muted; otherwise the write blocks while the queue is
     * full to apply natural backpressure.
     */
    private final class LocalAudioSink implements AudioSink {
        /**
         * {@inheritDoc}
         *
         * @param frame {@inheritDoc}
         * @throws NullPointerException if {@code frame} is {@code null}
         * @throws InterruptedException if interrupted while the queue is
         *                              full
         */
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
     * Sink for locally-captured video frames.
     *
     * <p>Frames are dropped silently when the call has ended or video is
     * muted or disabled; otherwise the write blocks while the queue is
     * full to apply natural backpressure.
     */
    private final class LocalVideoSink implements VideoSink {
        /**
         * {@inheritDoc}
         *
         * @param frame {@inheritDoc}
         * @throws NullPointerException if {@code frame} is {@code null}
         * @throws InterruptedException if interrupted while the queue is
         *                              full
         */
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
     * Source for decoded peer audio frames.
     *
     * <p>The read blocks until a frame is pushed and returns {@code null}
     * once the end-of-stream sentinel arrives at hangup.
     */
    private final class RemoteAudioSource implements AudioSource {
        /**
         * {@inheritDoc}
         *
         * @return {@inheritDoc}
         * @throws InterruptedException if interrupted while waiting for a
         *                              frame
         */
        @Override
        public AudioFrame next() throws InterruptedException {
            var frame = inboundAudio.take();
            return frame == SENTINEL_AUDIO ? null : frame;
        }
    }

    /**
     * Source for decoded peer video frames.
     *
     * <p>The read blocks until a frame is pushed and returns {@code null}
     * once the end-of-stream sentinel arrives at hangup.
     */
    private final class RemoteVideoSource implements VideoSource {
        /**
         * {@inheritDoc}
         *
         * @return {@inheritDoc}
         * @throws InterruptedException if interrupted while waiting for a
         *                              frame
         */
        @Override
        public VideoFrame next() throws InterruptedException {
            var frame = inboundVideo.take();
            return frame == SENTINEL_VIDEO ? null : frame;
        }
    }
}
