package com.github.auties00.cobalt.call.internal.session;

import com.github.auties00.cobalt.call.ActiveCall;
import com.github.auties00.cobalt.call.internal.CallService;
import com.github.auties00.cobalt.call.internal.audio.AudioPipeline;
import com.github.auties00.cobalt.call.internal.audio.opus.OpusPacket;
import com.github.auties00.cobalt.call.internal.rtp.srtp.SrtpEndpoint;
import com.github.auties00.cobalt.call.internal.transport.dtls.DtlsCertificate;
import com.github.auties00.cobalt.call.internal.transport.dtls.DtlsSrtpDriver;
import com.github.auties00.cobalt.call.internal.transport.ice.DatagramTransport;
import com.github.auties00.cobalt.call.internal.rtp.RtpReceiver;
import com.github.auties00.cobalt.call.internal.rtp.RtpSender;
import com.github.auties00.cobalt.call.internal.rtp.srtp.SrtpRole;
import com.github.auties00.cobalt.call.internal.video.VideoCodec;
import com.github.auties00.cobalt.call.internal.video.VideoPacket;
import com.github.auties00.cobalt.call.internal.video.VideoPipeline;

import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import com.github.auties00.cobalt.call.session.VoiceCallOptions;
import com.github.auties00.cobalt.call.session.VideoTrackOptions;

/**
 * Drives the media plane of a one-to-one voice call, owning the DTLS-SRTP handshake driver, the RTP
 * sender and receiver, and the audio pipeline bound to an {@link ActiveCall}'s media ports.
 *
 * <p>Construction takes the connected {@link DatagramTransport} produced by the ICE agent and the
 * {@link DtlsCertificate} the call signaling exchanged. {@link #start()} drives the DTLS handshake to
 * completion, derives the SRTP keys, wires the {@link RtpSender} and {@link RtpReceiver} to the
 * resulting {@link SrtpEndpoint}, and starts the audio pipeline so PCM frames written to
 * {@link ActiveCall#localAudioSink()} flow through Opus encoding, RTP packetisation, and SRTP
 * protection to the peer, while inbound frames flow the other way to
 * {@link ActiveCall#remoteAudioSource()}. Video tracks may be added on top through
 * {@link #startVideoTrack(VideoTrackOptions)}; a camera track and a screen-share track can run at the
 * same time, each on its own SSRC and payload type but sharing the audio track's SRTP keys.
 *
 * <p>The lifecycle proceeds in order:
 *
 * <ol>
 *   <li>Construct with the active call, the connected transport, the local DTLS role and
 *       certificate, and the peer's fingerprint from signaling.</li>
 *   <li>{@link #start()} drives the DTLS handshake on a virtual thread, then wires the RTP loop and
 *       starts the audio pipeline.</li>
 *   <li>{@link #awaitConnected} blocks until the handshake completes and media is flowing.</li>
 *   <li>{@link #close()}, or {@link ActiveCall#close()}, tears everything down in reverse order.</li>
 * </ol>
 *
 * <p>This session does not speak WhatsApp call signaling. It assumes the offer/answer exchange, ICE
 * candidate gathering, and DTLS fingerprint trade have already happened in the call signaling layer,
 * which feeds this session a connected transport plus the peer's fingerprint. Group-call media reuses
 * these same primitives behind {@link GroupCallSession}, which adds an SFU-style multipoint layer on
 * top.
 */
public final class VoiceCallSession implements AutoCloseable {
    /**
     * The active call whose media ports drive this session.
     */
    private final ActiveCall call;

    /**
     * The per-call configuration supplying the SSRCs, Opus payload type, and audio pipeline options;
     * never mutated after construction.
     */
    private final VoiceCallOptions options;

    /**
     * The DTLS role played across this handshake and any future {@link #reconnect reconnection}.
     */
    private final SrtpRole role;

    /**
     * The local DTLS certificate, reused across reconnects so the peer's signaling-advertised
     * fingerprint stays valid.
     */
    private final DtlsCertificate localCert;

    /**
     * The expected peer certificate fingerprint, also stable across reconnects; a defensive copy is
     * held so the caller's array cannot mutate it.
     */
    private final byte[] expectedPeerFingerprint;

    /**
     * The active DTLS-SRTP handshake driver, which owns the underlying {@link DatagramTransport}.
     *
     * <p>Closing the session closes the driver, which closes the transport. This field is replaced by
     * {@link #reconnect(DatagramTransport)} when the call layer detects a network change.
     */
    private DtlsSrtpDriver dtls;

    /**
     * The audio pipeline, instantiated once the handshake completes and preserved across reconnects;
     * {@code null} until then.
     */
    private AudioPipeline audio;

    /**
     * The audio RTP sender, instantiated once the handshake completes and nulled during a reconnect
     * window; {@code null} until then.
     */
    private RtpSender rtpSender;

    /**
     * The audio RTP receiver, instantiated once the handshake completes and nulled during a reconnect
     * window; {@code null} until then.
     */
    private RtpReceiver rtpReceiver;

    /**
     * The active video tracks, keyed by {@link VideoTrackOptions.Kind}.
     *
     * <p>Keying on the kind lets a camera track and a screen-share track coexist on the same session,
     * each with its own codec, pipeline, RTP sender and receiver, and distinct SSRC.
     */
    private final ConcurrentHashMap<VideoTrackOptions.Kind, VideoTrack> videoTracks =
            new ConcurrentHashMap<>();

    /**
     * Bundles the state of one active video track: its configuration, codec, pipeline, and RTP
     * plumbing.
     *
     * @param options  the configuration this track was started with
     * @param codec    the codec adapter, closed by the pipeline
     * @param pipeline the video pipeline driving the encode/decode loop
     * @param sender   the RTP sender for outbound frames
     * @param receiver the RTP receiver for inbound frames
     */
    private record VideoTrack(VideoTrackOptions options, VideoCodec codec,
                              VideoPipeline pipeline, RtpSender sender, RtpReceiver receiver) {
    }

    /**
     * The monotonic outbound presentation timestamp, in milliseconds, stamped onto each encoded Opus
     * frame.
     *
     * @implNote This implementation steps the counter by one frame duration per outbound packet,
     * which is 10 ms in the WhatsApp voice profile, so the RTP sender receives a strictly increasing
     * timestamp without depending on wall-clock timing of the encoder.
     */
    private final AtomicLong outboundPtsMs = new AtomicLong();

    /**
     * Whether {@link #start()} has been called, guarding against a second handshake.
     */
    private volatile boolean started;

    /**
     * Whether {@link #close()} has been called, after which media wiring and track changes are
     * suppressed.
     */
    private volatile boolean closed;

    /**
     * Constructs a one-to-one voice-call session bound to the given call and transport.
     *
     * @param call                    the active call whose media ports drive the session
     * @param transport               the connected datagram transport from the ICE agent
     * @param role                    the DTLS-SRTP role exchanged via signaling, either
     *                                {@link SrtpRole#CLIENT} or {@link SrtpRole#SERVER}
     * @param localCert               the local DTLS certificate
     * @param expectedPeerFingerprint the peer's SHA-256 certificate fingerprint from signaling
     * @param options                 the per-call configuration
     * @throws NullPointerException     if any argument is {@code null}
     * @throws IllegalArgumentException if {@code expectedPeerFingerprint} is not exactly 32 bytes
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
     * Returns the DTLS-SRTP driver, exposed so the call layer can wire its STUN handler back to the
     * ICE agent for keepalives.
     *
     * @return the DTLS-SRTP driver
     */
    public DtlsSrtpDriver dtlsDriver() {
        return dtls;
    }

    /**
     * Returns the audio pipeline, or {@code null} until the handshake completes.
     *
     * @return the audio pipeline, or {@code null} if the handshake has not completed
     */
    public AudioPipeline audio() {
        return audio;
    }

    /**
     * Returns whether the handshake has completed and the RTP plumbing is currently wired.
     *
     * <p>This reports {@code false} during a {@link #reconnect} window, between the old session being
     * torn down and the new handshake completing, because the RTP plumbing is nulled across the swap.
     *
     * @return {@code true} if media is flowing
     */
    public boolean connected() {
        return audio != null && rtpSender != null;
    }

    /**
     * Starts the DTLS handshake and spawns the completer thread that wires the media plane.
     *
     * <p>The handshake runs on a virtual thread; this method returns immediately. Calling it more
     * than once, or after {@link #close()}, is a no-op.
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
     * Blocks until the handshake completes and the media pipeline starts, or fails.
     *
     * <p>This first waits for the DTLS handshake, then polls until the completer thread has
     * instantiated the audio pipeline. It throws if the session is closed before wiring finishes or
     * if wiring does not complete within the given budget.
     *
     * @param timeout the maximum time to wait
     * @param unit    the unit of {@code timeout}
     * @throws IOException          if the handshake failed, the session closed early, or wiring timed
     *                              out
     * @throws InterruptedException if the calling thread is interrupted while waiting
     * @implNote This implementation polls the {@code audio} field every 5 ms after the handshake
     * completes because the completer thread wires the pipeline asynchronously; the same {@code unit}
     * budget bounds both the handshake wait and the wiring poll.
     */
    public void awaitConnected(long timeout, TimeUnit unit) throws IOException, InterruptedException {
        dtls.awaitHandshake(timeout, unit);
        var deadline = System.nanoTime() + unit.toNanos(timeout);
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
     * Starts a video track on this call, which must already be {@link #connected()}.
     *
     * <p>This constructs a {@link VideoCodec} of the configured kind, an RTP sender and receiver that
     * share the audio track's SRTP keys but use a separate SSRC and payload type, and a
     * {@link VideoPipeline} driving {@link ActiveCall#localVideoSink()} and
     * {@link ActiveCall#remoteVideoSource()}. The call layer drives this from
     * {@link CallService#placeCall(com.github.auties00.cobalt.model.jid.Jid, com.github.auties00.cobalt.call.CallOptions)}
     * when the user requests video at call setup, from a video-upgrade signaling exchange, or from a
     * screen-share request. If pipeline startup fails, the partially-built pipeline and codec are
     * closed and the exception propagates.
     *
     * @param trackOptions the video track configuration
     * @throws NullPointerException  if {@code trackOptions} is {@code null}
     * @throws IllegalStateException if the session is closed, the handshake has not completed, or a
     *                               track of the same kind is already running
     * @implNote This implementation stamps outbound video RTP with a 90 kHz clock, the RTP standard
     * video timestamp rate.
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
     * Stops the video track of the given kind, closing its pipeline.
     *
     * <p>This is idempotent for the kind: stopping a kind that is not running is a no-op.
     *
     * @param kind the track kind to stop
     * @throws NullPointerException if {@code kind} is {@code null}
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
     * Returns whether a video track of the given kind is currently active.
     *
     * @param kind the track kind to check
     * @return {@code true} if a track of that kind is running
     */
    public boolean videoActive(VideoTrackOptions.Kind kind) {
        return videoTracks.containsKey(kind);
    }

    /**
     * Returns whether any video track, camera or screen-share, is currently active.
     *
     * @return {@code true} if at least one video track is running
     */
    public boolean videoActive() {
        return !videoTracks.isEmpty();
    }

    /**
     * Starts a screen-share track, equivalent to {@link #startVideoTrack(VideoTrackOptions)} with
     * options configured for {@link VideoTrackOptions.Kind#SCREEN_SHARE}.
     *
     * @param options the screen-share track options, whose kind must be
     *                {@link VideoTrackOptions.Kind#SCREEN_SHARE}
     * @throws NullPointerException     if {@code options} is {@code null}
     * @throws IllegalArgumentException if the options are not configured for
     *                                  {@link VideoTrackOptions.Kind#SCREEN_SHARE}
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
     * Stops the active screen-share track, equivalent to
     * {@link #stopVideoTrack(VideoTrackOptions.Kind)} with
     * {@link VideoTrackOptions.Kind#SCREEN_SHARE}.
     */
    public void stopScreenShare() {
        stopVideoTrack(VideoTrackOptions.Kind.SCREEN_SHARE);
    }

    /**
     * Re-establishes the DTLS and SRTP layer against a freshly-supplied {@link DatagramTransport},
     * typically a new ICE-nominated path after a network change or relay swap.
     *
     * <p>The audio pipeline keeps running across the swap; the encoded outbound frames produced during
     * the brief gap are dropped because the RTP plumbing is nulled while the new handshake runs, and
     * inbound playback skips the missing samples. Calling this on a closed session is a no-op; calling
     * it twice in quick succession cancels the in-flight handshake and starts a new one. The
     * reconnection reuses the original DTLS role, local certificate, and expected peer fingerprint.
     *
     * @param newTransport the new connected datagram path
     * @throws NullPointerException if {@code newTransport} is {@code null}
     */
    public synchronized void reconnect(DatagramTransport newTransport) {
        Objects.requireNonNull(newTransport, "newTransport cannot be null");
        if (closed) {
            return;
        }
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
     * Tears down the session, closing every video track, the audio pipeline, and the DTLS driver
     * (which closes the transport).
     *
     * <p>Calling this more than once is a no-op. The underlying {@link ActiveCall}'s ended state
     * propagates from the call layer that owns the session.
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
     * Runs on the completer thread: waits for the DTLS handshake, then wires the media plane.
     *
     * <p>If the SRTP endpoint is already available it is used directly; otherwise this awaits the
     * handshake and, on failure, fails the session through {@link #handshakeFailed()}.
     *
     * @implNote This implementation bounds the handshake wait at 30 seconds, matching the call layer's
     * setup timeout.
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
     * Wires the RTP loop and audio pipeline once the SRTP keys are known.
     *
     * <p>On the first handshake this also instantiates and starts the {@link AudioPipeline}; on a
     * {@link #reconnect} the pipeline is preserved and only the RTP and SRTP plumbing is swapped. If
     * pipeline startup fails on the first handshake, the session is failed through
     * {@link #handshakeFailed()}.
     *
     * @param srtp the negotiated SRTP endpoint
     */
    private synchronized void wireMedia(SrtpEndpoint srtp) {
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
     * Demultiplexes one inbound SRTP packet by SSRC and routes it to the audio or matching video
     * receiver.
     *
     * <p>Packets shorter than the RTP header are dropped. The audio SSRC is matched first; otherwise
     * the video tracks are scanned for a matching receive SSRC. The chosen receiver is handed the
     * packet and its jitter buffer is drained.
     *
     * @param protectedPacket the SRTP-protected bytes
     * @implNote This implementation reads the SSRC from bytes 8 through 11 of the RTP header, which is
     * in clear under SRTP since only the payload and auth tag are encrypted; this picks the right
     * receiver before paying the AES-CM decryption cost. The 12-byte guard is the minimum RTP header
     * length.
     */
    private void onProtectedSrtp(byte[] protectedPacket) {
        if (protectedPacket.length < 12) {
            return;
        }
        var ssrc = ((protectedPacket[8] & 0xFF) << 24)
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
     * Wraps one inbound audio payload back into an {@link OpusPacket} and feeds the audio pipeline.
     *
     * <p>If the pipeline is not yet wired the emission is dropped. A missing marker is surfaced as an
     * empty payload with the voice-active flag cleared so the pipeline's decoder runs packet-loss
     * concealment; the marker bit is propagated only for non-missing packets.
     *
     * @param inbound the receiver's emission
     */
    private void onInboundRtp(RtpReceiver.InboundRtp inbound) {
        var pipeline = audio;
        if (pipeline == null) {
            return;
        }
        var ptsMs = inbound.timestamp() * 1000 / options.audio().sampleRate();
        var packet = new OpusPacket(inbound.payload(), ptsMs, !inbound.missing() && inbound.marker());
        pipeline.feedInboundPacket(packet);
    }

    /**
     * Wraps one inbound video payload back into a {@link VideoPacket} and feeds the matching video
     * pipeline.
     *
     * <p>If no track of the given kind is active the emission is dropped. A missing marker requests a
     * fresh keyframe through {@link VideoPipeline#requestKeyframe()} rather than concealing the loss,
     * since concealing video tends to look worse than waiting one group of pictures for a keyframe.
     *
     * @param opts    the video track options identifying the track
     * @param inbound the receiver's emission
     * @implNote This implementation derives the presentation timestamp from a 90 kHz clock, the RTP
     * standard video timestamp rate.
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
        var ptsMs = inbound.timestamp() * 1000 / 90_000;
        track.pipeline().feedInboundPacket(new VideoPacket(inbound.payload(), ptsMs,
                opts.pipeline().width(), opts.pipeline().height(), inbound.marker()));
    }

    /**
     * Packetises one encoded outbound video frame into RTP and ships it through the DTLS driver.
     *
     * <p>If the sender is not wired the frame is dropped, and any send failure is swallowed so a
     * transient transport error does not stop the pipeline. The frame's keyframe flag is passed to the
     * sender so it can set the marker bit on keyframes.
     *
     * @param sender the RTP sender for the track
     * @param packet the encoded outbound packet
     * @implNote This implementation marks keyframes per RFC 7741 section 4.4.
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
     * Packetises one encoded outbound Opus frame into RTP and ships it through the DTLS driver.
     *
     * <p>If the sender is not wired the frame is dropped, and any send failure is swallowed so a
     * transient transport error does not stop the pipeline. The outbound timestamp comes from the
     * monotonic counter stepped by one frame duration per packet rather than from the encoder's
     * presentation timestamp.
     *
     * @param packet the encoded outbound packet
     */
    private void onEncodedOutbound(OpusPacket packet) {
        var sender = rtpSender;
        if (sender == null) {
            return;
        }
        var pts = outboundPtsMs.getAndAdd(packetDurationMs());
        try {
            sender.send(packet.payload(), pts, packet.voiceActive());
        } catch (RuntimeException _) {
        }
    }

    /**
     * Returns the duration of one encoded outbound frame in milliseconds, derived from the audio
     * pipeline's frame size and sample rate.
     *
     * @return the frame duration in milliseconds
     */
    private long packetDurationMs() {
        return 1000L * options.audio().frameSize() / options.audio().sampleRate();
    }

    /**
     * Fails the session after a handshake failure, closing it and hanging up the call so the
     * application observes the ended state.
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
