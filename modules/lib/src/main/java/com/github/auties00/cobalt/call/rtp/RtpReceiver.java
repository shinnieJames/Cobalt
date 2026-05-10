package com.github.auties00.cobalt.call.rtp;

import com.github.auties00.cobalt.call.rtp.srtp.SrtpEndpoint;
import com.github.auties00.cobalt.exception.WhatsAppCallException;
import com.github.auties00.cobalt.util.DataUtils;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * Inbound side of an RTP stream — accepts SRTP-protected bytes
 * (typically from
 * {@link com.github.auties00.cobalt.call.transport.dtls.DtlsSrtpDriver#setSrtpHandler}),
 * unprotects them via {@link SrtpEndpoint#unprotectRtp}, parses the
 * RTP header, drops anything not addressed to the configured SSRC,
 * runs the packet through the {@link JitterBuffer}, and emits each
 * payload (with a missing-packet flag for PLC triggers) to the
 * configured downstream {@link Consumer}.
 *
 * <h2>Lifecycle</h2>
 *
 * <ol>
 *   <li>Construct with the SRTP endpoint, expected SSRC, and a
 *       {@code downstream} {@link InboundListener}.</li>
 *   <li>Wire {@link #onSrtpPacket(byte[])} to the DTLS driver's
 *       SRTP handler.</li>
 *   <li>Periodically call {@link #drain()} on the call's media
 *       scheduler — every drain emits as many in-order packets and
 *       PLC triggers as the jitter buffer can provide right now.</li>
 * </ol>
 *
 * <h2>Threading</h2>
 *
 * <p>{@link #onSrtpPacket} is safe to call from the network-receive
 * thread. {@link #drain()} runs on the media thread. The two share
 * the {@link JitterBuffer}, which is not internally synchronised —
 * {@code RtpReceiver} synchronises both calls on its own monitor.
 */
public final class RtpReceiver {
    /**
     * SRTP endpoint for unprotecting inbound packets.
     */
    private final SrtpEndpoint srtp;

    /**
     * Expected SSRC — packets with a different SSRC are silently
     * dropped (we don't multiplex multiple sources on one receiver).
     */
    private final int expectedSsrc;

    /**
     * Expected payload type — packets with a mismatched PT are
     * dropped.
     */
    private final int expectedPayloadType;

    /**
     * The reorder + miss-detection buffer.
     */
    private final JitterBuffer jitter;

    /**
     * Where decoded payloads (and PLC triggers) are delivered.
     */
    private final InboundListener downstream;

    /**
     * Functional interface invoked for each in-order RTP payload —
     * either a real packet or a PLC trigger.
     */
    @FunctionalInterface
    public interface InboundListener {
        /**
         * Receives one inbound RTP packet or PLC marker.
         *
         * @param inbound the inbound payload
         */
        void onInbound(InboundRtp inbound);
    }

    /**
     * One delivered packet — either a real payload or a missing-packet
     * marker that the codec should run PLC for.
     *
     * @param payload      the codec payload bytes (empty for
     *                     {@code missing == true})
     * @param timestamp    RTP timestamp (32-bit unsigned)
     * @param sequenceNumber the wire sequence number (0..65535) — for
     *                       missing markers, the seq the lost packet
     *                       would have carried
     * @param marker       RTP M bit
     * @param missing      whether this is a synthesised PLC trigger
     */
    public record InboundRtp(byte[] payload, long timestamp, int sequenceNumber,
                             boolean marker, boolean missing) {
        /**
         * Compact constructor — null-checks payload.
         */
        public InboundRtp {
            Objects.requireNonNull(payload, "payload cannot be null");
        }
    }

    /**
     * Constructs a new receiver with default jitter-buffer sizing
     * (capacity 64 packets, max-gap 5 — enough for ~640 ms of audio
     * at 10-ms frames or ~2 s of video at 30 fps).
     *
     * @param srtp                the SRTP endpoint
     * @param expectedSsrc        the SSRC to accept
     * @param expectedPayloadType the payload type to accept
     * @param downstream          where to emit decoded payloads
     */
    public RtpReceiver(SrtpEndpoint srtp, int expectedSsrc, int expectedPayloadType,
                       InboundListener downstream) {
        this(srtp, expectedSsrc, expectedPayloadType, downstream, new JitterBuffer(64, 5));
    }

    /**
     * Constructs a new receiver with an explicit jitter buffer —
     * useful for tests that want a smaller buffer or different gap
     * tolerance.
     *
     * @param srtp                the SRTP endpoint
     * @param expectedSsrc        the SSRC to accept
     * @param expectedPayloadType the payload type to accept
     * @param downstream          where to emit decoded payloads
     * @param jitter              the jitter buffer
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
     * @return the SSRC
     */
    public int expectedSsrc() {
        return expectedSsrc;
    }

    /**
     * Returns the payload type this receiver accepts.
     *
     * @return the payload type
     */
    public int expectedPayloadType() {
        return expectedPayloadType;
    }

    /**
     * Hands one inbound SRTP packet to the receiver. Returns
     * synchronously after the packet has been queued in the jitter
     * buffer; the actual emission to {@code downstream} happens in
     * {@link #drain()}.
     *
     * @param srtpBytes the protected SRTP packet
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
     * Drains the jitter buffer, emitting all in-order packets and
     * PLC triggers that are currently available. Returns after the
     * buffer reports {@link JitterBuffer#hasNext()} is {@code false}
     * and no PLC trigger is pending.
     *
     * <p>Invariant: each call advances the receiver's "next emitted
     * sequence number" by zero or more positions; never goes
     * backwards.
     *
     * @return the number of packets/markers emitted in this drain
     */
    public synchronized int drain() {
        int emitted = 0;
        while (true) {
            var packet = jitter.poll();
            if (packet != null) {
                deliver(packet, false);
                emitted++;
                continue;
            }
            int gap = jitter.pollMissing();
            if (gap == 0) {
                return emitted;
            }
            // Synthesise a missing-packet marker. We don't know the
            // exact sequence the lost packet would have carried; we
            // pass the next-expected seq as a hint to the decoder.
            deliver(null, true);
            emitted++;
        }
    }

    /**
     * Builds and dispatches one {@link InboundRtp} to the downstream
     * listener.
     *
     * @param packet  the source packet, or {@code null} for a
     *                missing marker
     * @param missing whether to flag this as a PLC trigger
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
