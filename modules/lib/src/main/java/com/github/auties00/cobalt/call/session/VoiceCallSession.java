package com.github.auties00.cobalt.call.session;

import com.github.auties00.cobalt.call.ActiveCall;
import com.github.auties00.cobalt.call.CallService;
import com.github.auties00.cobalt.call.audio.AudioPipeline;
import com.github.auties00.cobalt.call.audio.OpusPacket;
import com.github.auties00.cobalt.call.transport.dtls.DtlsCertificate;
import com.github.auties00.cobalt.call.transport.dtls.DtlsSrtpDriver;
import com.github.auties00.cobalt.call.transport.ice.DatagramTransport;
import com.github.auties00.cobalt.call.rtp.RtpReceiver;
import com.github.auties00.cobalt.call.rtp.RtpSender;
import com.github.auties00.cobalt.call.rtp.srtp.SrtpRole;
import com.github.auties00.cobalt.call.video.VideoCodec;
import com.github.auties00.cobalt.call.video.VideoPacket;
import com.github.auties00.cobalt.call.video.VideoPipeline;

import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * The M1 1:1 voice-call media session — owns and wires the
 * DTLS-SRTP handshake driver, the RTP sender + receiver, and the
 * audio pipeline against an {@link ActiveCall}'s media ports.
 *
 * <p>Construction takes the connected {@link DatagramTransport} from
 * the ICE agent (#76) and the {@link DtlsCertificate} pair the call
 * signaling exchanged. {@link #start()} drives the DTLS handshake to
 * completion, derives the SRTP keys, wires the
 * {@link RtpSender}/{@link RtpReceiver} to the resulting
 * {@link com.github.auties00.cobalt.call.rtp.srtp.SrtpEndpoint
 * SrtpEndpoint}, and starts the audio pipeline so PCM frames written
 * to {@link ActiveCall#localAudioSink} flow through Opus encoding,
 * RTP packetisation, and SRTP protection to the peer — and inbound
 * frames flow back the other way to
 * {@link ActiveCall#remoteAudioSource}.
 *
 * <h2>Lifecycle</h2>
 *
 * <ol>
 *   <li>Construct with the active call, the connected transport, our
 *       DTLS role + cert, and the peer's fingerprint from
 *       signaling.</li>
 *   <li>{@link #start()} drives the DTLS handshake on a virtual
 *       thread, then wires the RTP loop and starts the audio
 *       pipeline.</li>
 *   <li>{@link #awaitConnected} blocks until the handshake completes
 *       — the call is now actively exchanging media.</li>
 *   <li>{@link #close()} (or {@link ActiveCall#close()}) tears
 *       everything down in reverse order.</li>
 * </ol>
 *
 * <h2>What this doesn't do</h2>
 *
 * <p>The session does NOT speak WhatsApp call signaling. It assumes
 * the offer/answer exchange, ICE candidate gathering, and DTLS
 * fingerprint trade have already happened — those flows live in the
 * call signaling layer and feed this session a connected transport
 * plus the peer's fingerprint. Group-call media (M5+) reuses these
 * same primitives but adds an SFU-style multipoint layer on top.
 */
public final class VoiceCallSession implements AutoCloseable {
    /**
     * The active call whose media ports drive the session.
     */
    private final ActiveCall call;

    /**
     * Configuration — never mutated after construction.
     */
    private final VoiceCallOptions options;

    /**
     * The DTLS role we play across this and any future
     * {@link #reconnect reconnection}.
     */
    private final SrtpRole role;

    /**
     * The local DTLS certificate. Reused across reconnects so the
     * peer's signaling-advertised fingerprint stays valid.
     */
    private final DtlsCertificate localCert;

    /**
     * The peer's expected fingerprint, also stable across reconnects.
     */
    private final byte[] expectedPeerFingerprint;

    /**
     * The active DTLS-SRTP handshake driver. Owns the underlying
     * {@link DatagramTransport}; closing the session closes the
     * driver, which closes the transport.
     *
     * <p>Replaced by {@link #reconnect(DatagramTransport)} when the
     * call layer detects a network change.
     */
    private DtlsSrtpDriver dtls;

    /**
     * Audio pipeline; instantiated post-handshake.
     */
    private AudioPipeline audio;

    /**
     * RTP sender; instantiated post-handshake.
     */
    private RtpSender rtpSender;

    /**
     * RTP receiver; instantiated post-handshake.
     */
    private RtpReceiver rtpReceiver;

    /**
     * Per-kind video track state. Keyed by
     * {@link VideoTrackOptions.Kind} so a camera track and a
     * screen-share track can coexist on the same session
     * simultaneously, each with its own codec, pipeline, RTP sender
     * + receiver, and distinct SSRC.
     */
    private final java.util.concurrent.ConcurrentHashMap<VideoTrackOptions.Kind, VideoTrack> videoTracks =
            new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * Bundle of state for one active video track — the codec,
     * pipeline, and RTP plumbing.
     *
     * @param options  the configuration this track was started with
     * @param codec    the codec adapter (closed by the pipeline)
     * @param pipeline the video pipeline driving the loop
     * @param sender   the RTP sender for outbound frames
     * @param receiver the RTP receiver for inbound frames
     */
    private record VideoTrack(VideoTrackOptions options, VideoCodec codec,
                              VideoPipeline pipeline, RtpSender sender, RtpReceiver receiver) {
    }

    /**
     * Monotonic counter used to drive the AudioPipeline's outbound
     * pts when wrapping {@link OpusPacket} from the encoder. Each
     * Opus frame is 10 ms in the WhatsApp profile, so we step by 10
     * per outbound packet.
     */
    private final AtomicLong outboundPtsMs = new AtomicLong();

    /**
     * Whether {@link #start()} has been called.
     */
    private volatile boolean started;

    /**
     * Whether {@link #close()} has been called.
     */
    private volatile boolean closed;

    /**
     * Constructs a new session.
     *
     * @param call                    the active call
     * @param transport               the connected datagram transport
     *                                from the ICE agent
     * @param role                    {@link SrtpRole#CLIENT} or
     *                                {@link SrtpRole#SERVER} per the
     *                                DTLS-SRTP role exchanged via
     *                                signaling
     * @param localCert               our local DTLS certificate
     * @param expectedPeerFingerprint the peer's SHA-256 fingerprint
     *                                from signaling
     * @param options                 the per-call configuration
     * @throws NullPointerException     if any argument is
     *                                  {@code null}
     * @throws IllegalArgumentException if
     *                                  {@code expectedPeerFingerprint}
     *                                  is not exactly 32 bytes
     */
    public VoiceCallSession(ActiveCall call, DatagramTransport transport,
                            SrtpRole role, DtlsCertificate localCert,
                            byte[] expectedPeerFingerprint, VoiceCallOptions options) {
        this.call = Objects.requireNonNull(call, "call cannot be null");
        this.options = Objects.requireNonNull(options, "options cannot be null");
        this.role = Objects.requireNonNull(role, "role cannot be null");
        this.localCert = Objects.requireNonNull(localCert, "localCert cannot be null");
        Objects.requireNonNull(expectedPeerFingerprint, "expectedPeerFingerprint cannot be null");
        this.expectedPeerFingerprint = expectedPeerFingerprint.clone();
        this.dtls = new DtlsSrtpDriver(transport, role, localCert, expectedPeerFingerprint);
    }

    /**
     * Returns the active call this session drives.
     *
     * @return the active call
     */
    public ActiveCall call() {
        return call;
    }

    /**
     * Returns the DTLS-SRTP driver. Exposed so the call layer can
     * wire its STUN handler back to the ICE agent for keepalives.
     *
     * @return the DTLS driver
     */
    public DtlsSrtpDriver dtlsDriver() {
        return dtls;
    }

    /**
     * Returns the audio pipeline, or {@code null} until the DTLS
     * handshake completes.
     *
     * @return the audio pipeline, or {@code null}
     */
    public AudioPipeline audio() {
        return audio;
    }

    /**
     * Returns whether the DTLS handshake has completed and the RTP
     * plumbing is currently wired (i.e. media is flowing). Returns
     * {@code false} during a {@link #reconnect} window between the
     * old session being torn down and the new handshake completing.
     *
     * @return {@code true} if connected
     */
    public boolean connected() {
        return audio != null && rtpSender != null;
    }

    /**
     * Spawns the handshake thread. Idempotent — subsequent calls are
     * no-ops.
     */
    public synchronized void start() {
        if (started || closed) {
            return;
        }
        started = true;
        dtls.start();
        Thread.ofVirtual()
                .name("voice-call-completer")
                .start(this::completeAfterHandshake);
    }

    /**
     * Blocks until the DTLS handshake completes and the media
     * pipeline starts. Throws if the handshake fails or the wait
     * times out.
     *
     * @param timeout maximum time to wait
     * @param unit    timeout unit
     * @throws IOException          if the handshake failed or the
     *                              wait timed out
     * @throws InterruptedException if the calling thread is
     *                              interrupted
     */
    public void awaitConnected(long timeout, TimeUnit unit) throws IOException, InterruptedException {
        dtls.awaitHandshake(timeout, unit);
        long deadline = System.nanoTime() + unit.toNanos(timeout);
        while (audio == null) {
            if (System.nanoTime() > deadline) {
                throw new IOException("VoiceCallSession start did not complete within " + timeout + " " + unit);
            }
            if (closed) {
                throw new IOException("session closed before media wiring completed");
            }
            Thread.sleep(5);
        }
    }

    /**
     * Starts a video track on this call — the call must already be
     * {@link #connected()}. Constructs a {@link VideoCodec} of the
     * configured kind, a {@link VideoPipeline} that drives
     * {@link ActiveCall#localVideoSink} ↔
     * {@link ActiveCall#remoteVideoSource}, and an RTP sender +
     * receiver sharing the audio track's SRTP keys but with a
     * separate SSRC + payload type.
     *
     * <p>The call layer drives this from
     * {@link CallService#startCall} when the user requests video at
     * call setup (#65), or from a video-upgrade signaling exchange
     * (#66), or from a screen-share request (#69).
     *
     * @param trackOptions the video track configuration
     * @throws IllegalStateException if the session is not connected
     *                               or already has a video track
     */
    public synchronized void startVideoTrack(VideoTrackOptions trackOptions) {
        Objects.requireNonNull(trackOptions, "trackOptions cannot be null");
        if (closed) {
            throw new IllegalStateException("session is closed");
        }
        if (videoTracks.containsKey(trackOptions.kind())) {
            throw new IllegalStateException(
                    "video track for kind " + trackOptions.kind()
                            + " already running; call stopVideoTrack(kind) first");
        }
        var srtp = dtls.srtpEndpoint();
        if (srtp == null) {
            throw new IllegalStateException(
                    "DTLS handshake has not completed; cannot start a video track yet");
        }
        var codec = trackOptions.buildCodec();
        VideoPipeline pipeline = null;
        try {
            var sender = new RtpSender(trackOptions.videoPayloadType(),
                    trackOptions.localVideoSsrc(), 90_000, srtp, dtls::sendSrtp);
            var receiver = new RtpReceiver(srtp, trackOptions.remoteVideoSsrc(),
                    trackOptions.videoPayloadType(),
                    inbound -> onInboundVideoRtp(trackOptions, inbound));
            pipeline = new VideoPipeline(call,
                    packet -> onEncodedVideoOutbound(sender, packet),
                    codec, trackOptions.pipeline());
            pipeline.start();
            videoTracks.put(trackOptions.kind(),
                    new VideoTrack(trackOptions, codec, pipeline, sender, receiver));
        } catch (RuntimeException e) {
            try {
                if (pipeline != null) pipeline.close();
            } catch (Throwable _) {
            }
            try {
                codec.close();
            } catch (Throwable _) {
            }
            throw e;
        }
    }

    /**
     * Stops the named video track. Idempotent for the kind.
     *
     * @param kind which track to stop (camera or screen-share)
     */
    public synchronized void stopVideoTrack(VideoTrackOptions.Kind kind) {
        Objects.requireNonNull(kind, "kind cannot be null");
        var track = videoTracks.remove(kind);
        if (track == null) {
            return;
        }
        try {
            track.pipeline().close();
        } catch (Throwable _) {
        }
    }

    /**
     * Returns whether a video track of the given kind is currently
     * active.
     *
     * @param kind the kind to check
     * @return {@code true} if a track of that kind is running
     */
    public boolean videoActive(VideoTrackOptions.Kind kind) {
        return videoTracks.containsKey(kind);
    }

    /**
     * Returns whether any video track (camera or screen-share) is
     * currently active.
     *
     * @return {@code true} if at least one video track is running
     */
    public boolean videoActive() {
        return !videoTracks.isEmpty();
    }

    /**
     * Convenience for the M7 screen-share API — equivalent to
     * {@link #startVideoTrack(VideoTrackOptions)} with options
     * configured for {@link VideoTrackOptions.Kind#SCREEN_SHARE}.
     *
     * @param options the screen-share track options; must have
     *                {@code kind == SCREEN_SHARE}
     * @throws IllegalArgumentException if {@code options.kind() !=
     *                                  SCREEN_SHARE}
     */
    public void startScreenShare(VideoTrackOptions options) {
        Objects.requireNonNull(options, "options cannot be null");
        if (options.kind() != VideoTrackOptions.Kind.SCREEN_SHARE) {
            throw new IllegalArgumentException(
                    "startScreenShare requires Kind.SCREEN_SHARE, got " + options.kind());
        }
        startVideoTrack(options);
    }

    /**
     * Stops the active screen-share track, if any. Equivalent to
     * {@code stopVideoTrack(SCREEN_SHARE)}.
     */
    public void stopScreenShare() {
        stopVideoTrack(VideoTrackOptions.Kind.SCREEN_SHARE);
    }

    /**
     * Re-establishes the DTLS + SRTP layer against a freshly-supplied
     * {@link DatagramTransport} (typically a new ICE-nominated path
     * after a Wi-Fi ↔ cellular IP swap or a relay change). The audio
     * pipeline keeps running across the swap; the encoded outbound
     * frames produced during the brief gap are dropped (the
     * {@link #rtpSender} field is nulled while the new handshake
     * runs), and inbound playback simply skips the missing samples.
     *
     * <p>Idempotent in the sense that calling reconnect on a closed
     * session is a no-op. Calling reconnect twice in quick succession
     * cancels the in-flight handshake and starts a new one.
     *
     * @param newTransport the new connected datagram path
     * @throws NullPointerException if {@code newTransport} is
     *                              {@code null}
     */
    public synchronized void reconnect(DatagramTransport newTransport) {
        Objects.requireNonNull(newTransport, "newTransport cannot be null");
        if (closed) {
            return;
        }
        // Drop the RTP plumbing first so any outbound frame produced
        // during the swap is dropped instead of trying to use a
        // half-built endpoint.
        this.rtpSender = null;
        this.rtpReceiver = null;
        try {
            dtls.close();
        } catch (Throwable _) {
        }
        this.dtls = new DtlsSrtpDriver(newTransport, role, localCert, expectedPeerFingerprint);
        if (started) {
            dtls.start();
            Thread.ofVirtual()
                    .name("voice-call-reconnect")
                    .start(this::completeAfterHandshake);
        }
    }

    /**
     * Tears down the session — stops the audio pipeline, closes the
     * DTLS driver (which closes the transport), and propagates the
     * underlying {@link ActiveCall}'s {@code ENDED} state. Idempotent.
     */
    @Override
    public synchronized void close() {
        if (closed) {
            return;
        }
        closed = true;
        for (var track : videoTracks.values()) {
            try {
                track.pipeline().close();
            } catch (Throwable _) {
            }
        }
        videoTracks.clear();
        if (audio != null) {
            try {
                audio.close();
            } catch (Throwable _) {
            }
            audio = null;
        }
        try {
            dtls.close();
        } catch (Throwable _) {
        }
    }

    /**
     * Body of the completer virtual thread — waits for the DTLS
     * handshake, then wires up the RTP loop and starts the audio
     * pipeline. If the handshake fails, the session is closed and
     * the call is hung up.
     */
    private void completeAfterHandshake() {
        var srtp = dtls.srtpEndpoint();
        if (srtp == null) {
            try {
                srtp = dtls.awaitHandshake(30, TimeUnit.SECONDS);
            } catch (Throwable _) {
                handshakeFailed();
                return;
            }
        }
        wireMedia(srtp);
    }

    /**
     * Wires the RTP loop + audio pipeline once the SRTP keys are
     * known. On the first handshake this also instantiates and
     * starts the {@link AudioPipeline}; on a {@link #reconnect}
     * the pipeline is preserved and only the RTP/SRTP plumbing is
     * swapped.
     *
     * @param srtp the negotiated SRTP endpoint
     */
    private synchronized void wireMedia(com.github.auties00.cobalt.call.rtp.srtp.SrtpEndpoint srtp) {
        if (closed) {
            return;
        }
        this.rtpSender = new RtpSender(options.opusPayloadType(), options.localAudioSsrc(),
                options.audio().sampleRate(), srtp, dtls::sendSrtp);
        this.rtpReceiver = new RtpReceiver(srtp, options.remoteAudioSsrc(),
                options.opusPayloadType(), this::onInboundRtp);
        dtls.setSrtpHandler(this::onProtectedSrtp);
        if (audio == null) {
            var pipeline = new AudioPipeline(call, this::onEncodedOutbound, options.audio());
            try {
                pipeline.start();
            } catch (RuntimeException e) {
                try {
                    pipeline.close();
                } catch (Throwable _) {
                }
                handshakeFailed();
                return;
            }
            this.audio = pipeline;
        }
    }

    /**
     * Inbound side: parses the SSRC from the (clear) RTP header at
     * offset 8, routes the packet to the audio or video receiver,
     * and drains the resulting jitter buffer.
     *
     * <p>The SRTP header is in clear; only the payload + auth tag
     * are encrypted/MAC'd. So we can read the SSRC without first
     * decrypting — that lets us pick the right receiver before
     * paying the AES-CM cost.
     *
     * @param protectedPacket the SRTP-protected bytes
     */
    private void onProtectedSrtp(byte[] protectedPacket) {
        if (protectedPacket.length < 12) {
            return;
        }
        int ssrc = ((protectedPacket[8] & 0xFF) << 24)
                | ((protectedPacket[9] & 0xFF) << 16)
                | ((protectedPacket[10] & 0xFF) << 8)
                | (protectedPacket[11] & 0xFF);
        var audioReceiver = rtpReceiver;
        if (audioReceiver != null && ssrc == options.remoteAudioSsrc()) {
            audioReceiver.onSrtpPacket(protectedPacket);
            audioReceiver.drain();
            return;
        }
        for (var track : videoTracks.values()) {
            if (ssrc == track.options().remoteVideoSsrc()) {
                track.receiver().onSrtpPacket(protectedPacket);
                track.receiver().drain();
                return;
            }
        }
    }

    /**
     * Listener for the RTP receiver — when a payload arrives, wraps
     * it back into an {@link OpusPacket} and feeds the inbound
     * pipeline. PLC triggers (missing markers) are surfaced as
     * empty payloads with the {@code voiceActive=false} flag so the
     * pipeline's decoder runs concealment.
     *
     * @param inbound the receiver's emission
     */
    private void onInboundRtp(RtpReceiver.InboundRtp inbound) {
        var pipeline = audio;
        if (pipeline == null) {
            return;
        }
        long ptsMs = inbound.timestamp() * 1000 / options.audio().sampleRate();
        var packet = new OpusPacket(inbound.payload(), ptsMs, !inbound.missing() && inbound.marker());
        pipeline.feedInboundPacket(packet);
    }

    /**
     * Listener for the video RTP receiver — wraps the inbound
     * payload back into a {@link VideoPacket} and feeds the video
     * pipeline. Missing markers (PLC triggers) are surfaced via the
     * pipeline's {@link VideoPipeline#requestKeyframe()} so the peer
     * gets a fresh IDR rather than try to conceal a lost video
     * packet (concealing video tends to look worse than waiting one
     * GOP for a keyframe).
     *
     * @param inbound the receiver's emission
     */
    private void onInboundVideoRtp(VideoTrackOptions opts, RtpReceiver.InboundRtp inbound) {
        var track = videoTracks.get(opts.kind());
        if (track == null) {
            return;
        }
        if (inbound.missing()) {
            track.pipeline().requestKeyframe();
            return;
        }
        long ptsMs = inbound.timestamp() * 1000 / 90_000;
        track.pipeline().feedInboundPacket(new VideoPacket(inbound.payload(), ptsMs,
                opts.pipeline().width(), opts.pipeline().height(), inbound.marker()));
    }

    /**
     * Listener for the video pipeline's outbound side — packetises
     * each encoded VP8/H.264 frame into RTP and ships it through the
     * DTLS driver. Sets the M bit on keyframes per RFC 7741 §4.4.
     *
     * @param packet the encoded outbound packet
     */
    private void onEncodedVideoOutbound(RtpSender sender, VideoPacket packet) {
        if (sender == null) {
            return;
        }
        try {
            sender.send(packet.payload(), packet.ptsMs(), packet.keyFrame());
        } catch (RuntimeException _) {
        }
    }

    /**
     * Listener for the audio pipeline's outbound side — packetises
     * each encoded Opus frame into RTP and ships it through the
     * DTLS driver.
     *
     * @param packet the encoded outbound packet
     */
    private void onEncodedOutbound(OpusPacket packet) {
        var sender = rtpSender;
        if (sender == null) {
            return;
        }
        long pts = outboundPtsMs.getAndAdd(packetDurationMs());
        try {
            sender.send(packet.payload(), pts, packet.voiceActive());
        } catch (RuntimeException _) {
        }
    }

    /**
     * Returns the duration of one encoded outbound frame in
     * milliseconds — derived from the audio pipeline's frame size
     * and sample rate.
     *
     * @return the frame duration in ms
     */
    private long packetDurationMs() {
        return 1000L * options.audio().frameSize() / options.audio().sampleRate();
    }

    /**
     * Handles a handshake failure — closes the session and hangs up
     * the call so the application sees the {@code ENDED} state.
     */
    private void handshakeFailed() {
        try {
            close();
        } finally {
            try {
                call.hangup();
            } catch (Throwable _) {
            }
        }
    }
}
