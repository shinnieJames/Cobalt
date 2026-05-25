package com.github.auties00.cobalt.call.internal.rtp;

import com.github.auties00.cobalt.call.internal.rtp.srtp.SrtpEndpoint;
import com.github.auties00.cobalt.call.internal.transport.dtls.DtlsSrtpDriver;
import com.github.auties00.cobalt.exception.WhatsAppCallException;
import com.github.auties00.cobalt.util.DataUtils;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * Receives SRTP-protected packets, reorders them, and delivers each in-order payload downstream.
 *
 * <p>Inbound bytes (typically routed from {@link DtlsSrtpDriver#setSrtpHandler(Consumer)}) are
 * unprotected via {@link SrtpEndpoint#unprotectRtp(byte[])}, parsed into an {@link RtpPacket},
 * filtered against the configured SSRC and payload type, queued into the {@link JitterBuffer}, and
 * then emitted in sequence (each carrying a missing-packet flag for concealment triggers) to the
 * configured {@link InboundListener}.
 *
 * <p>The lifecycle is:
 *
 * <ol>
 *   <li>Construct with the SRTP endpoint, expected SSRC and payload type, and a downstream
 *       {@link InboundListener}.</li>
 *   <li>Wire {@link #onSrtpPacket(byte[])} to the DTLS driver's SRTP handler.</li>
 *   <li>Periodically call {@link #drain()} on the call's media scheduler; each drain emits as many
 *       in-order packets and concealment triggers as the jitter buffer can currently supply.</li>
 * </ol>
 *
 * <p>{@link #onSrtpPacket(byte[])} is safe to call from the network-receive thread and
 * {@link #drain()} from the media thread; both share the non-synchronised {@link JitterBuffer},
 * which this class guards by synchronising both methods on its own monitor.
 */
public final class RtpReceiver {
    /**
     * SRTP endpoint that unprotects inbound packets.
     */
    private final SrtpEndpoint srtp;

    /**
     * SSRC accepted by this receiver; a packet bearing a different SSRC is dropped, since one
     * receiver does not multiplex multiple sources.
     */
    private final int expectedSsrc;

    /**
     * Payload type accepted by this receiver; a packet bearing a different type is dropped.
     */
    private final int expectedPayloadType;

    /**
     * Reorder and miss-detection buffer feeding {@link #drain()}.
     */
    private final JitterBuffer jitter;

    /**
     * Listener that receives each decoded payload and concealment trigger.
     */
    private final InboundListener downstream;

    /**
     * Receives one in-order RTP payload, real or synthesised, from an {@link RtpReceiver}.
     */
    @FunctionalInterface
    public interface InboundListener {
        /**
         * Receives one inbound RTP payload or packet-loss-concealment marker.
         *
         * @param inbound the delivered payload
         */
        void onInbound(InboundRtp inbound);
    }

    /**
     * One delivered RTP payload, either a real packet or a missing-packet marker for which the
     * codec should run packet-loss concealment.
     *
     * @param payload        the codec payload bytes, empty when {@code missing} is {@code true}
     * @param timestamp      the 32-bit unsigned RTP timestamp
     * @param sequenceNumber the wire sequence number in {@code [0, 65535]}; for a missing marker,
     *                       the sequence the lost packet would have carried
     * @param marker         the RTP M bit
     * @param missing        whether this is a synthesised concealment trigger rather than a real
     *                       payload
     */
    public record InboundRtp(byte[] payload, long timestamp, int sequenceNumber,
                             boolean marker, boolean missing) {
        /**
         * Validates the components and rejects a null payload.
         *
         * @throws NullPointerException if {@code payload} is {@code null}
         */
        public InboundRtp {
            Objects.requireNonNull(payload, "payload cannot be null");
        }
    }

    /**
     * Constructs a receiver with the default jitter-buffer sizing.
     *
     * @param srtp                the SRTP endpoint
     * @param expectedSsrc        the SSRC to accept
     * @param expectedPayloadType the payload type to accept
     * @param downstream          the listener that receives decoded payloads
     * @throws NullPointerException if {@code srtp} or {@code downstream} is {@code null}
     * @implNote This implementation sizes the default {@link JitterBuffer} at capacity 64 and
     * maximum gap 5, which spans roughly 640 ms of audio at 10 ms frames or roughly 2 s of video at
     * 30 fps.
     */
    public RtpReceiver(SrtpEndpoint srtp, int expectedSsrc, int expectedPayloadType,
                       InboundListener downstream) {
        this(srtp, expectedSsrc, expectedPayloadType, downstream, new JitterBuffer(64, 5));
    }

    /**
     * Constructs a receiver with an explicit jitter buffer.
     *
     * <p>This overload lets a caller supply a buffer with non-default capacity or gap tolerance,
     * for example a smaller buffer in tests.
     *
     * @param srtp                the SRTP endpoint
     * @param expectedSsrc        the SSRC to accept
     * @param expectedPayloadType the payload type to accept
     * @param downstream          the listener that receives decoded payloads
     * @param jitter              the jitter buffer to use
     * @throws NullPointerException if {@code srtp}, {@code downstream}, or {@code jitter} is
     *                              {@code null}
     */
    public RtpReceiver(SrtpEndpoint srtp, int expectedSsrc, int expectedPayloadType,
                       InboundListener downstream, JitterBuffer jitter) {
        this.srtp = Objects.requireNonNull(srtp, "srtp cannot be null");
        this.expectedSsrc = expectedSsrc;
        this.expectedPayloadType = expectedPayloadType;
        this.jitter = Objects.requireNonNull(jitter, "jitter cannot be null");
        this.downstream = Objects.requireNonNull(downstream, "downstream cannot be null");
    }

    /**
     * Returns the SSRC this receiver accepts.
     *
     * @return the accepted SSRC
     */
    public int expectedSsrc() {
        return expectedSsrc;
    }

    /**
     * Returns the payload type this receiver accepts.
     *
     * @return the accepted payload type
     */
    public int expectedPayloadType() {
        return expectedPayloadType;
    }

    /**
     * Accepts one inbound SRTP packet and queues its decoded form for later emission.
     *
     * <p>The bytes are unprotected, decoded, and filtered against the expected SSRC and payload
     * type, then offered to the jitter buffer. A packet that fails to unprotect, fails to decode,
     * or does not match the SSRC and payload type is silently dropped. The method returns once the
     * packet has been queued; emission to {@code downstream} happens in {@link #drain()}.
     *
     * @param srtpBytes the protected SRTP packet
     * @throws NullPointerException if {@code srtpBytes} is {@code null}
     */
    public synchronized void onSrtpPacket(byte[] srtpBytes) {
        Objects.requireNonNull(srtpBytes, "srtpBytes cannot be null");
        byte[] rtpBytes;
        try {
            rtpBytes = srtp.unprotectRtp(srtpBytes);
        } catch (RuntimeException _) {
            return;
        }
        RtpPacket packet;
        try {
            packet = RtpPacket.decode(rtpBytes);
        } catch (WhatsAppCallException.Rtp _) {
            return;
        }
        if (packet.ssrc() != expectedSsrc || packet.payloadType() != expectedPayloadType) {
            return;
        }
        jitter.offer(packet);
    }

    /**
     * Drains the jitter buffer, emitting every in-order packet and concealment trigger currently
     * available.
     *
     * <p>The method loops until {@link JitterBuffer#hasNext()} reports no in-order packet and
     * {@link JitterBuffer#pollMissing()} reports no pending gap, delivering each polled packet and
     * each concealment trigger to {@code downstream}. The receiver's next-emitted sequence advances
     * by zero or more positions per call and never moves backwards.
     *
     * @return the number of packets and markers emitted during this call
     */
    public synchronized int drain() {
        var emitted = 0;
        while (true) {
            var packet = jitter.poll();
            if (packet != null) {
                deliver(packet, false);
                emitted++;
                continue;
            }
            var gap = jitter.pollMissing();
            if (gap == 0) {
                return emitted;
            }
            deliver(null, true);
            emitted++;
        }
    }

    /**
     * Builds one {@link InboundRtp} and dispatches it to the downstream listener.
     *
     * <p>For a missing marker the payload is {@link DataUtils#EMPTY_BYTE_ARRAY} and the timestamp,
     * sequence number, and marker bit are zeroed, since the exact values the lost packet carried
     * are unknown. A listener exception is swallowed so one bad downstream consumer cannot stall the
     * drain loop.
     *
     * @param packet  the source packet, or {@code null} for a missing marker
     * @param missing whether to flag this delivery as a concealment trigger
     */
    private void deliver(RtpPacket packet, boolean missing) {
        var inbound = missing
                ? new InboundRtp(DataUtils.EMPTY_BYTE_ARRAY, 0L, 0, false, true)
                : new InboundRtp(packet.payload(), packet.timestamp(), packet.sequenceNumber(),
                        packet.marker(), false);
        try {
            downstream.onInbound(inbound);
        } catch (Throwable _) {
        }
    }
}
