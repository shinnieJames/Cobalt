package com.github.auties00.cobalt.call.session;

import com.github.auties00.cobalt.call.CallRuntime;
import com.github.auties00.cobalt.call.CallService;
import com.github.auties00.cobalt.call.audio.AudioPipeline;
import com.github.auties00.cobalt.call.audio.opus.OpusPacket;
import com.github.auties00.cobalt.call.rtp.RtpReceiver;
import com.github.auties00.cobalt.call.rtp.RtpSender;
import com.github.auties00.cobalt.call.rtp.srtp.CallKeyDerivation;
import com.github.auties00.cobalt.call.rtp.srtp.SrtpEndpoint;
import com.github.auties00.cobalt.call.rtp.srtp.SrtpRole;
import com.github.auties00.cobalt.call.transport.dtls.DtlsCertificate;
import com.github.auties00.cobalt.call.transport.dtls.DtlsSrtpDriver;
import com.github.auties00.cobalt.call.transport.ice.DatagramTransport;
import com.github.auties00.cobalt.call.video.VideoCodec;
import com.github.auties00.cobalt.call.video.VideoPacket;
import com.github.auties00.cobalt.call.video.VideoPipeline;
import com.github.auties00.cobalt.model.call.CallEndReason;

import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Drives the media plane of a one-to-one voice call, owning the DTLS-SRTP handshake driver, the RTP
 * sender and receiver, and the audio pipeline bound to a {@link CallRuntime}'s media streams.
 *
 * <p>Construction takes the connected {@link DatagramTransport} produced by the ICE agent and the
 * {@link DtlsCertificate} the call signaling exchanged. {@link #start()} drives the DTLS handshake to
 * completion, derives the SRTP keys, wires the {@link RtpSender} and {@link RtpReceiver} to the
 * resulting {@link SrtpEndpoint}, and starts the audio pipeline so PCM frames written to
 * {@link CallRuntime#audioOut()} flow through Opus encoding, RTP packetisation, and SRTP
 * protection to the peer, while inbound frames flow the other way to
 * {@link CallRuntime#audioIn()}. Video tracks may be added on top through
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
 *   <li>{@link #close()}, or ending the owning {@link CallRuntime}, tears everything down in reverse order.</li>
 * </ol>
 *
 * <p>This session does not speak WhatsApp call signaling. It assumes the offer/answer exchange, ICE
 * candidate gathering, and DTLS fingerprint trade have already happened in the call signaling layer,
 * which feeds this session a connected transport plus the peer's fingerprint. Group-call media reuses
 * these same primitives behind {@link GroupCallSession}, which adds an SFU-style multipoint layer on
 * top.
 */
public final class VoiceCallSession implements CallMediaSession {
    /**
     * The call runtime whose media streams drive this session.
     */
    private final CallRuntime call;

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
     * The 30-byte hop-by-hop key the relay handed out in the {@code <hbh_key>} relay-block element,
     * or {@code null} when media keying falls back to the peer DTLS-SRTP export.
     *
     * <p>When set, the media {@link SrtpEndpoint} is keyed from this hop-by-hop key via
     * {@link SrtpEndpoint#fromHopByHopKey(byte[])} rather than from the DTLS handshake, matching real
     * WhatsApp where the relayed media RTP is protected hop-by-hop to the edge relay and the peer
     * DTLS-SRTP path is disabled. Left {@code null} by the loopback test sessions, which key from a
     * shared DTLS handshake instead.
     */
    private volatile byte[] hopByHopKey;

    /**
     * The 32-byte end-to-end call key the media payload SRTP is derived from (Family B), or
     * {@code null} on the loopback test path.
     */
    private volatile byte[] callKey;

    /**
     * The local participant {@code <lid>:<device>@lid} JID that keys the outbound media stream.
     */
    private volatile String localParticipantJid;

    /**
     * The peer participant {@code <lid>:<device>@lid} JID that keys the inbound media stream.
     */
    private volatile String peerParticipantJid;

    /**
     * The truncated HMAC-SHA1 tag length from the relay {@code warp_mi_tag_len}, defaulting to the
     * profile's 10.
     */
    private volatile int authTagLength = SrtpEndpoint.DEFAULT_AUTH_TAG_LENGTH;

    /**
     * The SRTCP endpoint protecting the relay-hop control traffic (Family A, hop-by-hop keyed), set
     * alongside the media endpoint when the keys are wired.
     */
    private volatile SrtpEndpoint srtcpEndpoint;

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
     * The SRTP endpoint media is keyed with, set by {@link #wireMedia(SrtpEndpoint)}.
     *
     * <p>On the hop-by-hop relay path this is the {@link SrtpEndpoint#fromHopByHopKey(byte[])} endpoint
     * rather than a DTLS-handshake export, so {@link #startVideoTrack(VideoTrackOptions)} reads it here
     * (the DTLS driver's {@code srtpEndpoint()} is {@code null} when no peer DTLS handshake ran).
     */
    private volatile SrtpEndpoint activeSrtp;

    /**
     * The interval, in milliseconds, between periodic RTCP sender reports.
     *
     * @implNote This implementation uses {@code 1000 ms}. WhatsApp's voip engine ends a media leg with
     * {@code reason=timeout} after roughly five missing report intervals (observed near 18 to 19
     * seconds); a one-second cadence keeps the reverse RTCP path comfortably fresh while the RTCP
     * traffic stays a negligible fraction of the audio bandwidth.
     */
    private static final long RTCP_INTERVAL_MS = 1000L;

    /**
     * The RTP timestamp clock rate, in Hz, for the Opus audio stream: the codec sample rate, not the
     * 48 kHz clock RFC 7587 specifies for Opus.
     *
     * @implNote This implementation uses the codec's 16 kHz sample rate, because the WhatsApp voip engine
     * deviates from RFC 7587 and clocks its Opus RTP at the SILK-WB sample rate rather than 48 kHz. This
     * was measured on the wire: the peer's inbound RTP timestamps advance at 16000 ticks per real second
     * (wall-clock correlation over a 13.5 s capture gave 16090 Hz, and the first 60 packets gave
     * 16214 Hz), and the inbound path interprets those timestamps at the same sample rate. Stamping
     * outbound at 48 kHz instead makes every 40 ms frame advance 1920 ticks, which the peer reads as
     * 120 ms and schedules three times too far apart, starving its jitter buffer so it plays roughly one
     * frame in three and conceals the rest as near-continuous silence.
     */
    private static final int OPUS_RTP_CLOCK_RATE = 16_000;

    /**
     * The thread that emits periodic RTCP sender reports on the audio stream for the call's lifetime,
     * or {@code null} until {@link #wireMedia(SrtpEndpoint)} starts it.
     *
     * <p>WhatsApp's voip engine treats a media leg as dead when it receives no RTCP from the remote
     * within roughly five report intervals and ends the call with {@code reason=timeout}; it also
     * withholds its own uplink audio until the reverse RTCP path is confirmed. Cobalt therefore must
     * keep this ticker running so the peer observes a live reverse RTCP path.
     */
    private volatile Thread rtcpTicker;

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
     * Whether the most recently encoded outbound frame carried voice, used to derive the RTP marker bit
     * as the rising edge of voice activity.
     *
     * <p>Confined to the single audio capture thread that calls {@link #onEncodedOutbound(OpusPacket)},
     * so it needs no synchronization. It starts {@code false} so the first frame of the stream is itself a
     * talkspurt onset.
     */
    private boolean previousFrameVoiceActive;

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
    public VoiceCallSession(CallRuntime call, DatagramTransport transport,
                            SrtpRole role, DtlsCertificate localCert,
                            byte[] expectedPeerFingerprint, VoiceCallOptions options) {
        this(call,
                new DtlsSrtpDriver(
                        Objects.requireNonNull(transport, "transport cannot be null"),
                        Objects.requireNonNull(role, "role cannot be null"),
                        Objects.requireNonNull(localCert, "localCert cannot be null"),
                        Objects.requireNonNull(expectedPeerFingerprint, "expectedPeerFingerprint cannot be null")),
                role, localCert, expectedPeerFingerprint, options);
    }

    /**
     * Constructs a one-to-one voice-call session bound to the given call and an already-built
     * {@link DtlsSrtpDriver}.
     *
     * <p>Used when the transport layer ({@link com.github.auties00.cobalt.call.transport.ActiveCallTransport})
     * has already constructed the driver, possibly already driven its handshake, so the session
     * shares the same DTLS/SRTP demultiplexer as the SCTP data-channel transport. The role,
     * certificate, and expected peer fingerprint are still passed in so the session can rebuild
     * the driver on {@link #reconnect(DatagramTransport)}.
     *
     * @param call                    the active call whose media ports drive the session
     * @param driver                  the pre-built DTLS-SRTP driver
     * @param role                    the DTLS-SRTP role exchanged via signaling, either
     *                                {@link SrtpRole#CLIENT} or {@link SrtpRole#SERVER}
     * @param localCert               the local DTLS certificate
     * @param expectedPeerFingerprint the peer's SHA-256 certificate fingerprint from signaling
     * @param options                 the per-call configuration
     * @throws NullPointerException if any argument is {@code null}
     */
    public VoiceCallSession(CallRuntime call, DtlsSrtpDriver driver,
                            SrtpRole role, DtlsCertificate localCert,
                            byte[] expectedPeerFingerprint, VoiceCallOptions options) {
        this.call = Objects.requireNonNull(call, "call cannot be null");
        this.options = Objects.requireNonNull(options, "options cannot be null");
        this.role = Objects.requireNonNull(role, "role cannot be null");
        this.localCert = Objects.requireNonNull(localCert, "localCert cannot be null");
        Objects.requireNonNull(expectedPeerFingerprint, "expectedPeerFingerprint cannot be null");
        this.expectedPeerFingerprint = expectedPeerFingerprint.clone();
        this.dtls = Objects.requireNonNull(driver, "driver cannot be null");
    }

    /**
     * Keys the media {@link SrtpEndpoint} from the 30-byte hop-by-hop key the relay handed out in the
     * {@code <hbh_key>} relay-block element instead of from the peer DTLS-SRTP export.
     *
     * <p>Must be called before {@link #start()}. When set, {@link #wireMedia(SrtpEndpoint)} replaces
     * the DTLS-derived endpoint with {@link SrtpEndpoint#fromHopByHopKey(byte[])}, so the relayed
     * media RTP is protected hop-by-hop to the edge relay as real WhatsApp does; the DTLS driver is
     * still used as the byte transport and for the SCTP control channel. Passing {@code null} leaves
     * the session on the DTLS-export keying used by the loopback tests.
     *
     * @param hbhKey the 30-byte hop-by-hop key, or {@code null} to keep DTLS-export keying
     * @return this session, for chaining before {@link #start()}
     */
    public VoiceCallSession useHopByHopKey(byte[] hbhKey) {
        this.hopByHopKey = hbhKey == null ? null : hbhKey.clone();
        return this;
    }

    /**
     * Installs the end-to-end call key that keys the per-participant SRTP protecting the media payload.
     *
     * <p>Must be called before {@link #start()}. The media RTP is double protected: the relay hop is
     * keyed by the {@code "hbh srtp"} master (see {@link #useHopByHopKey(byte[])}), and the payload
     * itself is end-to-end SRTP keyed from the call key per participant JID (the wasm
     * {@code generate_srtp_and_p2p_keys_for_participant} schedule), which the relay cannot read. The
     * local participant JID keys the outbound stream and the peer participant JID keys the inbound one,
     * via {@link SrtpEndpoint#fromParticipantKeys(byte[], String, String, int)}. A 1:1 call carries no
     * SFrame layer on top of this: the peer forwards and unwraps the participant-keyed SRTP directly.
     *
     * @param callKey       the 32-byte end-to-end call key fanned out in the offer {@code <enc>}
     * @param localJid      the local participant {@code <lid>:<device>@lid} JID
     * @param peerJid       the peer participant {@code <lid>:<device>@lid} JID
     * @param authTagLength the truncated HMAC-SHA1 tag length from the relay {@code warp_mi_tag_len}
     * @return this session, for chaining before {@link #start()}
     */
    public VoiceCallSession useParticipantKeys(byte[] callKey, String localJid, String peerJid,
                                               int authTagLength) {
        this.callKey = callKey == null ? null : callKey.clone();
        this.localParticipantJid = localJid;
        this.peerParticipantJid = peerJid;
        this.authTagLength = authTagLength;
        return this;
    }

    /**
     * Returns the active call this session drives.
     *
     * @return the active call
     */
    public CallRuntime call() {
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
        // The relay (hop-by-hop) path keys media from the call key / hop-by-hop key, not a peer
        // DTLS-SRTP handshake, and {@link #completeAfterHandshake} wires media immediately without
        // awaiting one. Kicking the DTLS handshake there only fires a stray DTLS ClientHello at the
        // raw-UDP relay, which the native peer never sends; the relay then misclassifies the stream as
        // DTLS rather than raw SRTP and stops forwarding it to the peer. Skip the handshake on the relay
        // path (the inbound demux is installed in the driver constructor, so receive still works) and
        // kick it only for a genuine peer DTLS-SRTP leg.
        if (callKey == null && hopByHopKey == null) {
            dtls.start();
        }
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
        // In hop-by-hop mode the SRTP keys are known up front, so there is no peer DTLS handshake to
        // await; only wait for the media pipeline to wire. The DTLS path still awaits the handshake.
        if (hopByHopKey == null) {
            dtls.awaitHandshake(timeout, unit);
        }
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
     * {@link VideoPipeline} driving {@link CallRuntime#videoOut()} and
     * {@link CallRuntime#videoIn()}. The call layer drives this from
     * {@link CallService#placeCall(com.github.auties00.cobalt.model.jid.Jid, com.github.auties00.cobalt.call.stream.AudioOutputStream, com.github.auties00.cobalt.call.stream.AudioInputStream, com.github.auties00.cobalt.call.stream.VideoOutputStream, com.github.auties00.cobalt.call.stream.VideoInputStream)}
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
        var srtp = activeSrtp != null ? activeSrtp : dtls.srtpEndpoint();
        if (srtp == null) {
            throw new IllegalStateException(
                    "media is not yet keyed; cannot start a video track yet");
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
            System.getLogger(VoiceCallSession.class.getName()).log(System.Logger.Level.INFO,
                    "VIDEO TRACK STARTED kind=" + trackOptions.kind() + " ssrc=" + trackOptions.localVideoSsrc()
                            + " pt=" + trackOptions.videoPayloadType());
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
     * Applies a freshly-published end-to-end rekey bundle to this session's SRTP endpoint.
     *
     * <p>Walks {@link com.github.auties00.cobalt.model.call.datachannel.E2eRekeyPayload#keys()} and
     * for each {@link com.github.auties00.cobalt.model.call.datachannel.RekeyKeyType#AUDIO AUDIO} or
     * {@link com.github.auties00.cobalt.model.call.datachannel.RekeyKeyType#VIDEO VIDEO} entry calls
     * {@link com.github.auties00.cobalt.call.rtp.srtp.SrtpEndpoint#rotateMasterKey(byte[])}
     * on the shared endpoint owned by this session's DTLS driver. The session uses one
     * {@code SrtpEndpoint} for both audio and video tracks (they share DTLS-SRTP keys), so a payload
     * carrying both an AUDIO and a VIDEO entry installs the AUDIO key first and then the VIDEO key
     * second; in practice only the last entry's key is retained.
     *
     * <p>{@link com.github.auties00.cobalt.model.call.datachannel.RekeyKeyType#APPDATA APPDATA}
     * entries describe the DataChannel rekey and do not touch this session's SRTP endpoint.
     *
     * @param payload the rekey bundle
     * @throws NullPointerException if {@code payload} is {@code null}
     */
    @Override
    public void applyRekey(com.github.auties00.cobalt.model.call.datachannel.E2eRekeyPayload payload) {
        Objects.requireNonNull(payload, "payload cannot be null");
        var srtp = activeSrtp;
        if (srtp == null) {
            return;
        }
        for (var entry : payload.keys()) {
            switch (entry.type()) {
                case AUDIO, VIDEO -> srtp.rotateMasterKey(entry.key());
                case APPDATA -> { /* DataChannel rekey; outside the SRTP endpoint */ }
            }
        }
    }

    /**
     * Tears down the session, closing every video track, the audio pipeline, and the DTLS driver
     * (which closes the transport).
     *
     * <p>Calling this more than once is a no-op. The underlying {@link CallRuntime}'s ended state
     * propagates from the call layer that owns the session.
     */
    @Override
    public synchronized void close() {
        if (closed) {
            return;
        }
        closed = true;
        var ticker = this.rtcpTicker;
        if (ticker != null) {
            ticker.interrupt();
            this.rtcpTicker = null;
        }
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
        // Relay path: the media keys are known up front (the e2e call key keys the payload, the relay
        // hop-by-hop key keys SRTCP), so there is no peer DTLS-SRTP handshake to await. Wire media
        // immediately over the relay transport; the driver moves raw SRTP via sendSrtp/srtpHandler
        // regardless of DTLS handshake state (RFC 7983 byte-0 demux).
        if (callKey != null || hopByHopKey != null) {
            wireMedia(null);
            return;
        }
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
     * Returns the participant JID with an explicit device, mapping a bare {@code <lid>@lid} (the primary
     * device) to {@code <lid>:0@lid}.
     *
     * <p>The end-to-end participant key derivation ({@link CallKeyDerivation}) keys each stream by the
     * sender's {@code <lid>:<device>@lid} JID; the primary device is carried on the wire as a bare
     * {@code <lid>@lid} but keys as device {@code 0}, so a bare JID is normalised here before it feeds
     * the HKDF info.
     *
     * @param jid the participant JID, possibly bare
     * @return the JID with an explicit device suffix
     */
    private static String deviceExplicit(String jid) {
        if (jid == null) {
            return null;
        }
        var at = jid.indexOf('@');
        if (at < 0 || jid.lastIndexOf(':', at) >= 0) {
            return jid;
        }
        return jid.substring(0, at) + ":0" + jid.substring(at);
    }

    /**
     * Wires the RTP loop and audio pipeline once the SRTP keys are known.
     *
     * <p>On the first handshake this also instantiates and starts the {@link AudioPipeline}; on a
     * {@link #reconnect} the pipeline is preserved and only the RTP and SRTP plumbing is swapped. If
     * pipeline startup fails on the first handshake, the session is failed through
     * {@link #handshakeFailed()}.
     *
     * @param dtlsSrtp the negotiated SRTP endpoint
     */
    private synchronized void wireMedia(SrtpEndpoint dtlsSrtp) {
        if (closed) {
            return;
        }
        // 1:1 relay media is end-to-end SRTP keyed per participant from the 32-byte call key (the wasm
        // generate_srtp_and_p2p_keys_for_participant schedule): the outbound stream is keyed by the
        // local participant device JID and the inbound stream by the sender's participant device JID,
        // and the SFU forwards the sender's protected media without re-keying it under the relay hop
        // key. The DTLS-export endpoint is the loopback-test fallback used when no call key was supplied.
        SrtpEndpoint mediaSrtp = callKey != null
                ? SrtpEndpoint.fromParticipantKeys(
                        callKey, deviceExplicit(localParticipantJid), deviceExplicit(peerParticipantJid), authTagLength)
                : dtlsSrtp;
        if (mediaSrtp == null) {
            handshakeFailed();
            return;
        }
        this.srtcpEndpoint = hopByHopKey != null
                ? SrtpEndpoint.fromHopByHopSrtcp(hopByHopKey, authTagLength)
                : mediaSrtp;
        this.activeSrtp = mediaSrtp;
        // WhatsApp clocks its Opus RTP at the 16 kHz SILK-WB sample rate, not RFC 7587's 48 kHz; the
        // inbound path reads the peer's timestamps at the same rate, so the outbound stream must match.
        this.rtpSender = new RtpSender(options.opusPayloadType(), options.localAudioSsrc(),
                OPUS_RTP_CLOCK_RATE, mediaSrtp, dtls::sendSrtp);
        this.rtpReceiver = new RtpReceiver(mediaSrtp, options.remoteAudioSsrc(),
                options.opusPayloadType(), this::onInboundRtp);
        // 1:1 audio carries a raw Opus payload directly inside the end-to-end participant SRTP keyed from
        // the call key (the Family B per-participant schedule above): the relay cannot read it because it
        // lacks the participant key, so no further SFrame layer is applied. SFrame is used only for group
        // calls. This is verified on the wire: the peer's inbound RTP payloads, after participant-SRTP
        // unprotect, decode cleanly as Opus packets (config 10 SILK-WB 40 ms and config 0 DTX), not as
        // SFrame ciphertext, so neither stream applies a payload transform.
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
        startRtcpTicker();
    }

    /**
     * Starts the periodic RTCP sender-report ticker once, if not already running.
     *
     * <p>The ticker emits a compound RTCP sender report on the audio SSRC shortly after media is
     * wired and then at a steady interval for the call's lifetime, so the peer observes a live reverse
     * RTCP path and does not end the call with {@code reason=timeout}. A {@link #reconnect} preserves
     * the running ticker rather than starting a second one.
     */
    private synchronized void startRtcpTicker() {
        if (rtcpTicker != null || closed) {
            return;
        }
        rtcpTicker = Thread.ofVirtual().name("call-rtcp-" + call.call().callId()).start(() -> {
            try {
                Thread.sleep(250);
                while (!closed && !Thread.currentThread().isInterrupted()) {
                    sendSenderReport();
                    Thread.sleep(RTCP_INTERVAL_MS);
                }
            } catch (InterruptedException _) {
                Thread.currentThread().interrupt();
            }
        });
    }

    /**
     * Builds, SRTCP-protects, and sends one compound RTCP packet (a sender report followed by a
     * source-description CNAME chunk) on the audio stream.
     *
     * <p>The sender report carries the audio SSRC, an NTP-format wall-clock timestamp, the RTP
     * timestamp and packet and octet counts taken from the live {@link RtpSender}, and no reception
     * report blocks. The packet is protected with the SRTCP context keyed by the local audio SSRC and
     * written to the relay over the same raw transport sink as media SRTP; the peer demultiplexes it
     * by its RTCP payload-type byte. Any failure is swallowed so a transient transport error does not
     * stop the ticker.
     */
    private void sendSenderReport() {
        var srtp = srtcpEndpoint;
        var sender = rtpSender;
        if (srtp == null || sender == null) {
            return;
        }
        try {
            var report = buildSenderReport(options.localAudioSsrc(),
                    sender.lastRtpTimestamp(), sender.sentPackets(), sender.sentOctets());
            var protectedReport = srtp.protectRtcp(report, options.localAudioSsrc());
            dtls.sendSrtp(protectedReport);
        } catch (RuntimeException _) {
        }
    }

    /**
     * {@inheritDoc}
     *
     * @implNote This implementation sends one RTCP Picture Loss Indication per active video track, each
     * addressed to that track's {@link VideoTrackOptions#remoteVideoSsrc() remote video SSRC} with the
     * track's {@link VideoTrackOptions#localVideoSsrc() local SSRC} as the packet sender, SRTCP-protected
     * by the hop-by-hop control endpoint and written to the relay over the same sink as the sender
     * reports. The PLI carries a trailing 32-bit request sequence number after the standard 12-byte
     * feedback header (16 bytes total), matching the hop-by-hop form the WhatsApp voip engine emits so
     * the relay can deduplicate retransmitted requests. It is a no-op when no video track is active or
     * the SRTCP endpoint is not yet keyed; a failed send on one track does not stop the others.
     */
    @Override
    public void requestKeyframe() {
        var srtp = srtcpEndpoint;
        if (srtp == null || videoTracks.isEmpty()) {
            return;
        }
        for (var track : videoTracks.values()) {
            var localSsrc = track.options().localVideoSsrc();
            var pli = buildPli(localSsrc, track.options().remoteVideoSsrc(), (int) pliSequence.incrementAndGet());
            try {
                dtls.sendSrtp(srtp.protectRtcp(pli, localSsrc));
            } catch (RuntimeException _) {
            }
        }
    }

    /**
     * Encodes a hop-by-hop RTCP Picture Loss Indication (RFC 4585 section 6.3.1, payload-specific
     * feedback) requesting a key frame from the peer's video sender.
     *
     * <p>The packet is the 12-byte PLI feedback header followed by a 32-bit request sequence number; the
     * WhatsApp relay path appends that word (length field 3, not the bare-PLI 2) so duplicate requests
     * can be dropped. The {@code mediaSsrc} is the SSRC of the video sender being asked to refresh.
     *
     * @param senderSsrc the local SSRC stamped as the feedback packet's sender
     * @param mediaSsrc  the peer video SSRC the key-frame request targets
     * @param sequence   the monotonically increasing per-session request sequence number
     * @return the plaintext RTCP PLI packet
     */
    private static byte[] buildPli(int senderSsrc, int mediaSsrc, int sequence) {
        var out = new byte[16];
        out[0] = (byte) 0x81;          // V=2, P=0, FMT=1 (PLI)
        out[1] = (byte) 206;           // PT=PSFB
        out[2] = 0x00;
        out[3] = 0x03;                 // length = 3 (4 words - 1)
        writeInt(out, 4, senderSsrc);
        writeInt(out, 8, mediaSsrc);
        writeInt(out, 12, sequence);
        return out;
    }

    /**
     * Encodes a compound RTCP packet of a sender report with no reception report blocks followed by a
     * source-description chunk carrying a CNAME item, per RFC 3550 sections 6.4.1 and 6.5.
     *
     * @param ssrc          the sender SSRC carried by both the sender report and the SDES chunk
     * @param rtpTimestamp  the RTP timestamp of the most recently sent media packet
     * @param packetCount   the cumulative count of media packets sent
     * @param octetCount    the cumulative count of media payload octets sent
     * @return the plaintext compound RTCP packet
     */
    private static byte[] buildSenderReport(int ssrc, long rtpTimestamp,
                                            long packetCount, long octetCount) {
        // NTP timestamp: seconds since 1900-01-01 in the high 32 bits, fractional seconds in the low.
        var nowMs = System.currentTimeMillis();
        var ntpSeconds = (nowMs / 1000L) + 2208988800L;
        var ntpFraction = ((nowMs % 1000L) << 32) / 1000L;
        var cname = ("cobalt-" + Integer.toUnsignedString(ssrc)).getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        // SDES chunk: SSRC(4) + item type(1) + item len(1) + cname + terminator(1), padded to 32 bits.
        var sdesChunkLen = 4 + 1 + 1 + cname.length + 1;
        var sdesChunkPadded = (sdesChunkLen + 3) & ~3;
        var srLen = 28;
        var sdesLen = 4 + sdesChunkPadded;
        var out = new byte[srLen + sdesLen];
        var i = 0;
        // Sender report.
        out[i++] = (byte) 0x80;            // V=2, P=0, RC=0
        out[i++] = (byte) 200;             // PT=SR
        var srWords = (srLen / 4) - 1;
        out[i++] = (byte) (srWords >> 8);
        out[i++] = (byte) srWords;
        writeInt(out, i, ssrc); i += 4;
        writeInt(out, i, (int) ntpSeconds); i += 4;
        writeInt(out, i, (int) ntpFraction); i += 4;
        writeInt(out, i, (int) rtpTimestamp); i += 4;
        writeInt(out, i, (int) packetCount); i += 4;
        writeInt(out, i, (int) octetCount); i += 4;
        // Source description.
        out[i++] = (byte) 0x81;            // V=2, P=0, SC=1
        out[i++] = (byte) 202;             // PT=SDES
        var sdesWords = (sdesLen / 4) - 1;
        out[i++] = (byte) (sdesWords >> 8);
        out[i++] = (byte) sdesWords;
        writeInt(out, i, ssrc); i += 4;
        out[i++] = 1;                       // CNAME item type
        out[i++] = (byte) cname.length;
        System.arraycopy(cname, 0, out, i, cname.length); i += cname.length;
        // remaining bytes stay zero: the null item terminator and 32-bit padding.
        return out;
    }

    /**
     * Writes a big-endian 32-bit integer into the buffer at the given offset.
     *
     * @param b      the destination buffer
     * @param offset the offset at which the integer begins
     * @param value  the value to write
     */
    private static void writeInt(byte[] b, int offset, int value) {
        b[offset] = (byte) (value >>> 24);
        b[offset + 1] = (byte) (value >>> 16);
        b[offset + 2] = (byte) (value >>> 8);
        b[offset + 3] = (byte) value;
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
        if (protectedPacket.length < 2) {
            return;
        }
        // RFC 5761 multiplexing: a packet whose payload-type byte masks into [64, 95] is RTCP, since
        // RTP payload types never fall in that range. Inbound RTCP (the peer's sender reports and
        // feedback) is not media; drop it here rather than mismatching it against a media SSRC.
        var ptByte = protectedPacket[1] & 0x7F;
        if (ptByte >= 64 && ptByte <= 95) {
            return;
        }
        if (protectedPacket.length < 12) {
            return;
        }
        var ssrc = ((protectedPacket[8] & 0xFF) << 24)
                   | ((protectedPacket[9] & 0xFF) << 16)
                   | ((protectedPacket[10] & 0xFF) << 8)
                   | (protectedPacket[11] & 0xFF);
        // A known video SSRC routes to its track; everything else is the single audio stream.
        for (var track : videoTracks.values()) {
            if (ssrc == track.options().remoteVideoSsrc()) {
                track.receiver().onSrtpPacket(protectedPacket);
                track.receiver().drain();
                return;
            }
        }
        var audioReceiver = rtpReceiver;
        if (audioReceiver != null) {
            // A relayed WhatsApp peer chooses its own audio SSRC and stamps a fixed media payload type
            // that need not equal the locally assumed default; latch onto the first inbound stream.
            if (ssrc != audioReceiver.expectedSsrc()) {
                audioReceiver.adoptSsrc(ssrc);
            }
            if (ptByte != audioReceiver.expectedPayloadType()) {
                audioReceiver.adoptPayloadType(ptByte);
            }
            audioReceiver.onSrtpPacket(protectedPacket);
            audioReceiver.drain();
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
            var n = videoFramesSent.incrementAndGet();
            if (n == 1 || n % 50 == 0) {
                System.getLogger(VoiceCallSession.class.getName()).log(System.Logger.Level.INFO,
                        "VIDEO RTP SENT frames=" + n + " (last payload=" + packet.payload().length + "B, keyframe="
                                + packet.keyFrame() + ")");
            }
        } catch (RuntimeException _) {
        }
    }

    /**
     * Counts encoded outbound video frames shipped as RTP, for bring-up diagnostics.
     */
    private final AtomicLong videoFramesSent = new AtomicLong();

    /**
     * Supplies the monotonically increasing request sequence number stamped on each outbound RTCP PLI so
     * the relay can deduplicate retransmitted key-frame requests.
     */
    private final AtomicLong pliSequence = new AtomicLong();

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
        // The RTP marker bit flags a talkspurt onset (the first packet after silence), not every voiced
        // packet; setting it on every packet makes the peer's jitter buffer resynchronise each packet and
        // the audio stutters. Derive it from the rising edge of voice activity.
        var talkspurtOnset = packet.voiceActive() && !previousFrameVoiceActive;
        previousFrameVoiceActive = packet.voiceActive();
        try {
            sender.send(packet.payload(), pts, talkspurtOnset);
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
                call.end(CallEndReason.UNKNOWN, null);
            } catch (Throwable _) {
            }
        }
    }

}
