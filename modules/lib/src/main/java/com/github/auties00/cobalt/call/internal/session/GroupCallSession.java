package com.github.auties00.cobalt.call.internal.session;

import com.github.auties00.cobalt.call.ActiveCall;
import com.github.auties00.cobalt.call.internal.audio.AudioPipeline;
import com.github.auties00.cobalt.call.internal.audio.opus.OpusPacket;
import com.github.auties00.cobalt.call.internal.audio.opus.OpusDecoder;
import com.github.auties00.cobalt.call.frame.audio.AudioFrame;
import com.github.auties00.cobalt.call.internal.transport.dtls.DtlsCertificate;
import com.github.auties00.cobalt.call.internal.transport.dtls.DtlsSrtpDriver;
import com.github.auties00.cobalt.call.internal.transport.ice.DatagramTransport;
import com.github.auties00.cobalt.call.internal.rtp.RtpReceiver;
import com.github.auties00.cobalt.call.internal.rtp.RtpSender;
import com.github.auties00.cobalt.call.internal.rtp.srtp.SrtpEndpoint;
import com.github.auties00.cobalt.call.internal.rtp.srtp.SrtpRole;
import com.github.auties00.cobalt.model.jid.Jid;

import java.io.IOException;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import com.github.auties00.cobalt.call.session.VoiceCallOptions;
import com.github.auties00.cobalt.call.session.GroupCallParticipant;

/**
 * Drives the media plane of a many-to-many group call over a single SFU-relayed transport.
 *
 * <p>The outbound side carries one local audio track: the local microphone, encoded to Opus by an
 * {@link AudioPipeline} and sent up to the SFU on the local SSRC supplied by {@link VoiceCallOptions}.
 * The inbound side carries one audio stream per remote participant, each forwarded by the SFU on a
 * distinct SSRC and demultiplexed back to a per-peer {@link GroupCallParticipant} listener. There is
 * no server-side mixing: every participant's audio arrives as a separate stream and the application
 * is responsible for mixing the decoded frames it receives.
 *
 * <p>This session differs from the one-to-one {@link VoiceCallSession} in four ways:
 *
 * <ul>
 *   <li>One sender, many receivers. A single {@link RtpSender} feeds the SFU, which re-broadcasts to
 *       every participant; inbound audio is routed to a per-peer consumer rather than to a single
 *       remote audio source.</li>
 *   <li>Per-participant decoder. Each peer's SSRC owns its own {@link OpusDecoder} so the SFU's
 *       forwarded streams do not corrupt each other's packet-loss-concealment state.</li>
 *   <li>Outbound preprocessing reads only from {@link ActiveCall#localAudioSink()}; the application's
 *       mix of all decoded participants is what feeds the echo canceller's far-end reference.</li>
 *   <li>Group SSRC management. Participants join and leave through {@link #addParticipant} and
 *       {@link #removeParticipant}, driven by the group-call signaling layer above this session.</li>
 * </ul>
 *
 * <p>This session does not speak group-call signaling. Invite, remove, and group-state changes live
 * in the call layer above it, which feeds this session a connected {@link DatagramTransport} and the
 * peer or SFU DTLS fingerprint.
 */
public final class GroupCallSession implements AutoCloseable {
    /**
     * The active call whose media ports drive this session.
     */
    private final ActiveCall call;

    /**
     * The per-call configuration supplying the local SSRC, the Opus payload type, and the audio
     * pipeline options; never mutated after construction.
     */
    private final VoiceCallOptions options;

    /**
     * The DTLS-SRTP handshake driver that owns the underlying {@link DatagramTransport} and derives
     * the SRTP keys shared by the outbound sender and every per-participant receiver.
     *
     * <p>The local SSRC and Opus payload type come from {@link #options}; each participant supplies
     * its own SFU-assigned receive SSRC through {@link GroupCallParticipant#audioSsrc()}.
     */
    private final DtlsSrtpDriver dtls;

    /**
     * The outbound RTP sender for the single local audio track, instantiated once the handshake
     * completes; {@code null} until then.
     */
    private RtpSender sender;

    /**
     * The audio pipeline that drives microphone frames through Opus encoding into outbound RTP,
     * instantiated once the handshake completes; {@code null} until then.
     */
    private AudioPipeline audio;

    /**
     * The per-peer subscriber state, keyed on the participant's receive SSRC.
     *
     * <p>The SFU forwards each peer's audio with a distinct SSRC, so inbound SRTP is demultiplexed by
     * reading the SSRC from the packet header and looking up the matching {@link Subscriber}.
     */
    private final ConcurrentHashMap<Integer, Subscriber> subscribers = new ConcurrentHashMap<>();

    /**
     * Whether {@link #start()} has been called, guarding against a second handshake.
     */
    private volatile boolean started;

    /**
     * Whether {@link #close()} has been called, after which media delivery and participant changes
     * are suppressed.
     */
    private volatile boolean closed;

    /**
     * Bundles the per-participant state required to receive and decode one forwarded audio stream.
     *
     * <p>Each subscriber owns the {@link RtpReceiver} that filters on the participant's SSRC, a
     * dedicated {@link OpusDecoder} so packet-loss-concealment state is independent of other peers,
     * and the application listener carried on the {@link GroupCallParticipant}.
     */
    private static final class Subscriber {
        /**
         * The participant this subscriber receives audio for, carrying its JID, SSRC, and listener.
         */
        final GroupCallParticipant participant;

        /**
         * The RTP receiver filtering inbound SRTP on the participant's SSRC.
         */
        final RtpReceiver receiver;

        /**
         * The decoder dedicated to this participant so its concealment state stays isolated.
         */
        final OpusDecoder decoder;

        /**
         * The per-channel sample count of one decoded frame, taken from the audio options.
         */
        final int frameSize;

        /**
         * Constructs a subscriber bundle for one participant.
         *
         * @param participant the participant whose stream this subscriber receives
         * @param receiver    the RTP receiver filtering on the participant's SSRC
         * @param decoder     the decoder dedicated to this participant
         * @param frameSize   the per-channel sample count of one decoded frame
         */
        Subscriber(GroupCallParticipant participant, RtpReceiver receiver,
                   OpusDecoder decoder, int frameSize) {
            this.participant = participant;
            this.receiver = receiver;
            this.decoder = decoder;
            this.frameSize = frameSize;
        }
    }

    /**
     * Constructs a group-call session bound to the given call and transport.
     *
     * <p>The remote SSRC carried by {@code options} is ignored: participants supply their own receive
     * SSRCs when they join through {@link #addParticipant}.
     *
     * @param call                    the active call whose media ports drive the session
     * @param transport               the connected datagram path to the SFU
     * @param role                    the DTLS role to play in the handshake
     * @param localCert               the local DTLS certificate
     * @param expectedPeerFingerprint the expected SFU certificate fingerprint
     * @param options                 the per-call configuration; supplies the local SSRC and Opus
     *                                payload type, with the remote SSRC ignored
     * @throws NullPointerException if any argument is {@code null}
     */
    public GroupCallSession(ActiveCall call, DatagramTransport transport,
                            SrtpRole role, DtlsCertificate localCert,
                            byte[] expectedPeerFingerprint, VoiceCallOptions options) {
        this.call = Objects.requireNonNull(call, "call cannot be null");
        this.options = Objects.requireNonNull(options, "options cannot be null");
        Objects.requireNonNull(role, "role cannot be null");
        Objects.requireNonNull(localCert, "localCert cannot be null");
        Objects.requireNonNull(expectedPeerFingerprint, "expectedPeerFingerprint cannot be null");
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
     * Returns whether the handshake has completed and the media plane is wired.
     *
     * <p>This reports {@code true} once both the audio pipeline and the outbound sender have been
     * instantiated by the completer thread, which happens only after the DTLS handshake succeeds.
     *
     * @return {@code true} if media is flowing
     */
    public boolean connected() {
        return audio != null && sender != null;
    }

    /**
     * Returns a snapshot of the receive SSRCs of the currently-subscribed participants.
     *
     * @return an immutable copy of the subscribed SSRC set
     */
    public Set<Integer> subscribedSsrcs() {
        return Set.copyOf(subscribers.keySet());
    }

    /**
     * Starts the DTLS handshake and spawns the completer thread that wires the outbound media plane.
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
                .name("group-call-completer")
                .start(this::completeAfterHandshake);
    }

    /**
     * Blocks until the handshake completes and the local microphone pipeline is wired, or fails.
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
                throw new IOException("group call did not finish wiring within " + timeout + " " + unit);
            }
            if (closed) {
                throw new IOException("session closed before media wiring");
            }
            Thread.sleep(5);
        }
    }

    /**
     * Subscribes to a participant's forwarded audio stream.
     *
     * <p>This requires the DTLS handshake to have completed so the SRTP endpoint is available, and is
     * idempotent for a given SSRC: subscribing again with the same {@link GroupCallParticipant#audioSsrc()}
     * closes the previous decoder and replaces the subscriber so the latest listener wins.
     *
     * @param participant the participant to subscribe to
     * @throws NullPointerException  if {@code participant} is {@code null}
     * @throws IllegalStateException if the session is closed or the handshake has not completed
     */
    public synchronized void addParticipant(GroupCallParticipant participant) {
        Objects.requireNonNull(participant, "participant cannot be null");
        if (closed) {
            throw new IllegalStateException("session is closed");
        }
        var srtp = dtls.srtpEndpoint();
        if (srtp == null) {
            throw new IllegalStateException("DTLS handshake has not completed");
        }
        var existing = subscribers.remove(participant.audioSsrc());
        if (existing != null) {
            try {
                existing.decoder.close();
            } catch (Throwable _) {
            }
        }
        subscribers.put(participant.audioSsrc(),
                buildSubscriber(srtp, participant));
    }

    /**
     * Removes the participant with the given JID, closing its decoder and stopping frame delivery.
     *
     * <p>The first subscriber whose participant matches {@code jid} is removed; if none matches, this
     * is a no-op.
     *
     * @param jid the JID of the participant to remove
     * @throws NullPointerException if {@code jid} is {@code null}
     */
    public synchronized void removeParticipant(Jid jid) {
        Objects.requireNonNull(jid, "jid cannot be null");
        Integer toRemove = null;
        for (var entry : subscribers.entrySet()) {
            if (entry.getValue().participant.jid().equals(jid)) {
                toRemove = entry.getKey();
                break;
            }
        }
        if (toRemove == null) {
            return;
        }
        var sub = subscribers.remove(toRemove);
        if (sub != null) {
            try {
                sub.decoder.close();
            } catch (Throwable _) {
            }
        }
    }

    /**
     * Tears down the session, closing every subscriber decoder, the audio pipeline, and the DTLS
     * driver (which closes the transport).
     *
     * <p>Calling this more than once is a no-op.
     */
    @Override
    public synchronized void close() {
        if (closed) {
            return;
        }
        closed = true;
        for (var sub : Map.copyOf(subscribers).values()) {
            try {
                sub.decoder.close();
            } catch (Throwable _) {
            }
        }
        subscribers.clear();
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
     * Runs on the completer thread: awaits the handshake and then builds the outbound sender and
     * audio pipeline.
     *
     * <p>On handshake failure the session is closed and the call is hung up. On success, and only if
     * the session is still open, this installs the outbound sender, registers the inbound SRTP
     * handler, and starts the audio pipeline; if the pipeline fails to start, the session is closed.
     *
     * @implNote This implementation bounds the handshake wait at 30 seconds, matching the call layer's
     * setup timeout, and any failure path hangs the call up so the application observes the ended
     * state.
     */
    private void completeAfterHandshake() {
        SrtpEndpoint srtp;
        try {
            srtp = dtls.awaitHandshake(30, TimeUnit.SECONDS);
        } catch (Throwable _) {
            close();
            try {
                call.hangup();
            } catch (Throwable suppressed) {
            }
            return;
        }
        synchronized (this) {
            if (closed) {
                return;
            }
            this.sender = new RtpSender(options.opusPayloadType(), options.localAudioSsrc(),
                    options.audio().sampleRate(), srtp, dtls::sendSrtp);
            dtls.setSrtpHandler(this::onProtectedSrtp);
            var pipeline = new AudioPipeline(call, this::onEncodedOutbound, options.audio());
            try {
                pipeline.start();
            } catch (RuntimeException e) {
                try {
                    pipeline.close();
                } catch (Throwable _) {
                }
                close();
                return;
            }
            this.audio = pipeline;
        }
    }

    /**
     * Builds a {@link Subscriber} bundle for the given participant.
     *
     * <p>The receiver filters on the participant's SSRC and the Opus payload type, dispatching each
     * inbound payload to {@link #deliverFromPeer}; the decoder is dedicated to this participant.
     *
     * @param srtp        the negotiated SRTP endpoint
     * @param participant the participant to build the subscriber for
     * @return the constructed subscriber
     */
    private Subscriber buildSubscriber(SrtpEndpoint srtp, GroupCallParticipant participant) {
        var decoder = new OpusDecoder(options.audio().sampleRate(), options.audio().channels());
        var receiver = new RtpReceiver(srtp, participant.audioSsrc(),
                options.opusPayloadType(),
                inbound -> deliverFromPeer(participant, decoder, inbound));
        return new Subscriber(participant, receiver, decoder, options.audio().frameSize());
    }

    /**
     * Decodes one inbound RTP payload from a participant and forwards the resulting {@link AudioFrame}
     * to its listener.
     *
     * <p>A missing marker on the inbound emission drives the decoder's packet-loss concealment instead
     * of decoding bytes. Empty decoder output and decode failures are dropped silently, and any
     * exception thrown by the listener is swallowed so one misbehaving consumer cannot stall the media
     * plane.
     *
     * @param participant the source participant
     * @param decoder     the participant's dedicated decoder
     * @param inbound     the receiver's emission
     */
    private void deliverFromPeer(GroupCallParticipant participant, OpusDecoder decoder,
                                 RtpReceiver.InboundRtp inbound) {
        if (closed) {
            return;
        }
        short[] pcm;
        try {
            pcm = inbound.missing()
                    ? decoder.decodePacketLoss(options.audio().frameSize())
                    : decoder.decode(inbound.payload(), options.audio().frameSize());
        } catch (RuntimeException _) {
            return;
        }
        if (pcm.length == 0) {
            return;
        }
        var ptsMs = inbound.timestamp() * 1000L / options.audio().sampleRate();
        var frame = new AudioFrame(pcm, ptsMs);
        try {
            participant.onAudio().accept(frame);
        } catch (Throwable _) {
        }
    }

    /**
     * Demultiplexes one inbound SRTP packet by SSRC and routes it to the matching subscriber.
     *
     * <p>Packets shorter than the RTP header are dropped. The SSRC is read from the clear header,
     * the matching {@link Subscriber} is looked up, and the packet is handed to its receiver before
     * draining the jitter buffer; packets with an unknown SSRC are ignored.
     *
     * @param packet the SRTP-protected bytes
     * @implNote This implementation reads the SSRC from bytes 8 through 11 of the RTP header, which is
     * in clear under SRTP, so the right subscriber is selected before any AES-CM decryption cost; the
     * 12-byte guard is the minimum RTP header length.
     */
    private void onProtectedSrtp(byte[] packet) {
        if (packet.length < 12) {
            return;
        }
        var ssrc = ((packet[8] & 0xFF) << 24) | ((packet[9] & 0xFF) << 16)
                   | ((packet[10] & 0xFF) << 8) | (packet[11] & 0xFF);
        var subscriber = subscribers.get(ssrc);
        if (subscriber == null) {
            return;
        }
        subscriber.receiver.onSrtpPacket(packet);
        subscriber.receiver.drain();
    }

    /**
     * Forwards one encoded outbound Opus frame to the single RTP sender, which the SFU re-broadcasts
     * to every participant.
     *
     * <p>If the sender is not yet wired the frame is dropped, and any send failure is swallowed so a
     * transient transport error does not stop the pipeline.
     *
     * @param packet the encoded outbound packet
     */
    private void onEncodedOutbound(OpusPacket packet) {
        var s = sender;
        if (s == null) {
            return;
        }
        try {
            s.send(packet.payload(), packet.ptsMs(), packet.voiceActive());
        } catch (RuntimeException _) {
        }
    }

    /**
     * Returns the negotiated SRTP endpoint, or {@code null} until the handshake completes.
     *
     * @return the SRTP endpoint, or {@code null} if the handshake has not completed
     */
    public SrtpEndpoint srtpEndpoint() {
        return dtls.srtpEndpoint();
    }
}
