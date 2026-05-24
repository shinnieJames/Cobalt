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
 * The M5 group-call media session — outbound side is one local
 * audio track (our mic, encoded to Opus) sent up to the SFU; inbound
 * side is N audio streams (one per other participant), each
 * forwarded by the SFU on a distinct SSRC, demultiplexed back to
 * per-peer {@link GroupCallParticipant} listeners.
 *
 * <p>Differences from the 1:1 {@link VoiceCallSession}:
 *
 * <ul>
 *   <li><b>One sender, many receivers</b> — instead of a single
 *       remote audio source on the call, each participant's audio
 *       is routed to a per-peer consumer.</li>
 *   <li><b>Per-participant decoder</b> — each peer's SSRC has its
 *       own {@link OpusDecoder} so the SFU's forwarded streams
 *       don't trample each other's PLC state.</li>
 *   <li><b>Outbound preprocessing</b> — uses the same
 *       {@link AudioPipeline} as 1:1 but reading from the call's
 *       {@link ActiveCall#localAudioSink} only; the AEC's far-end
 *       reference is the application's mix of all decoded
 *       participants (the application is responsible for mixing —
 *       we don't do server-side mixing in M5).</li>
 *   <li><b>Group SSRC management</b> — participants come and go via
 *       {@link #addParticipant} / {@link #removeParticipant}
 *       wired from the group-call signaling layer.</li>
 * </ul>
 *
 * <p>For the M5 scope, the local audio capture is wired through the
 * {@link ActiveCall#localAudioSink}; group-specific signaling
 * (invite, remove, group-state changes) lives in the call layer
 * above this session.
 */
public final class GroupCallSession implements AutoCloseable {
    /**
     * The active call.
     */
    private final ActiveCall call;

    /**
     * Configuration.
     */
    private final VoiceCallOptions options;

    /**
     * The DTLS-SRTP driver. The local SSRC and Opus PT come from
     * {@code options}; participants share the SFU-assigned receive
     * SSRCs.
     */
    private final DtlsSrtpDriver dtls;

    /**
     * Outbound RTP sender, sized for one local audio track.
     */
    private RtpSender sender;

    /**
     * The audio pipeline. Drives mic frames → Opus → outbound RTP.
     */
    private AudioPipeline audio;

    /**
     * Per-peer subscriber state keyed on SSRC. The SFU forwards
     * each peer's audio with a distinct SSRC; we look up the right
     * subscriber on inbound.
     */
    private final ConcurrentHashMap<Integer, Subscriber> subscribers = new ConcurrentHashMap<>();

    /**
     * Whether {@link #start()} has been called.
     */
    private volatile boolean started;

    /**
     * Whether {@link #close()} has been called.
     */
    private volatile boolean closed;

    /**
     * Per-participant subscriber bundle — owns the
     * {@link RtpReceiver} that filters on the participant's SSRC,
     * a per-peer {@link OpusDecoder} so PLC state is independent,
     * and the application's audio listener.
     */
    private static final class Subscriber {
        final GroupCallParticipant participant;
        final RtpReceiver receiver;
        final OpusDecoder decoder;
        final int frameSize;

        Subscriber(GroupCallParticipant participant, RtpReceiver receiver,
                   OpusDecoder decoder, int frameSize) {
            this.participant = participant;
            this.receiver = receiver;
            this.decoder = decoder;
            this.frameSize = frameSize;
        }
    }

    /**
     * Constructs a new session.
     *
     * @param call                    the active call
     * @param transport               the connected datagram path
     * @param role                    DTLS role
     * @param localCert               local DTLS cert
     * @param expectedPeerFingerprint the peer/SFU fingerprint
     * @param options                 voice options (the local SSRC
     *                                + opus PT; remote SSRC is
     *                                ignored — participants supply
     *                                their own)
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
     * Returns the underlying call.
     *
     * @return the call
     */
    public ActiveCall call() {
        return call;
    }

    /**
     * Returns whether the DTLS handshake has completed.
     *
     * @return {@code true} if connected
     */
    public boolean connected() {
        return audio != null && sender != null;
    }

    /**
     * Returns the JIDs of the currently-subscribed participants.
     *
     * @return the participant JID set
     */
    public Set<Integer> subscribedSsrcs() {
        return Set.copyOf(subscribers.keySet());
    }

    /**
     * Spawns the handshake thread + media wiring. Idempotent.
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
     * Blocks until the handshake completes and the local mic
     * pipeline is wired.
     *
     * @param timeout maximum wait
     * @param unit    unit
     * @throws IOException          if the handshake failed
     * @throws InterruptedException if interrupted
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
     * Idempotent for the given SSRC — calling twice with the same
     * {@code participant.audioSsrc()} replaces the listener.
     *
     * @param participant the new participant
     * @throws IllegalStateException if the session is closed
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
     * Removes a participant — closes its decoder and stops
     * delivering frames to the listener.
     *
     * @param jid the participant's JID
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
     * Tears down the session. Idempotent.
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
     * Body of the completer thread — awaits the handshake, then
     * builds the outbound sender + audio pipeline.
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
     * Builds a {@link Subscriber} for the given participant.
     *
     * @param srtp        the negotiated SRTP endpoint
     * @param participant the participant
     * @return the subscriber
     */
    private Subscriber buildSubscriber(SrtpEndpoint srtp, GroupCallParticipant participant) {
        var decoder = new OpusDecoder(options.audio().sampleRate(), options.audio().channels());
        var receiver = new RtpReceiver(srtp, participant.audioSsrc(),
                options.opusPayloadType(),
                inbound -> deliverFromPeer(participant, decoder, inbound));
        return new Subscriber(participant, receiver, decoder, options.audio().frameSize());
    }

    /**
     * Decodes an inbound RTP payload from the given participant and
     * forwards the resulting {@link AudioFrame} to the registered
     * consumer. Missing markers (PLC triggers) are passed through
     * Opus's built-in concealment.
     *
     * @param participant the source peer
     * @param decoder     the per-peer decoder
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
     * Demultiplex inbound SRTP by SSRC: looks up the right
     * subscriber and routes the packet through its receiver.
     *
     * @param packet the protected bytes
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
     * Forwards each encoded outbound Opus frame to the single
     * outbound RTP sender — the SFU re-broadcasts to every
     * participant.
     *
     * @param packet the encoded packet
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
     * Returns the negotiated SRTP endpoint, or {@code null} until
     * the handshake completes.
     *
     * @return the endpoint, or {@code null}
     */
    public SrtpEndpoint srtpEndpoint() {
        return dtls.srtpEndpoint();
    }
}
