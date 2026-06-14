package com.github.auties00.cobalt.call.rtp;

import com.github.auties00.cobalt.call.rtp.srtp.SrtpEndpoint;
import com.github.auties00.cobalt.call.transport.dtls.DtlsSrtpDriver;
import com.github.auties00.cobalt.exception.WhatsAppCallException;

import java.security.SecureRandom;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Packetises outbound codec payloads into protected SRTP and hands them to a downstream sink.
 *
 * <p>Each call to {@link #send(byte[], long, boolean)} assigns the next sequence number, derives
 * the RTP timestamp from the supplied presentation timestamp, builds an {@link RtpPacket}, protects
 * it with {@link SrtpEndpoint#protectRtp(byte[])}, and passes the protected bytes to the configured
 * {@link Consumer} (typically wired to {@link DtlsSrtpDriver#sendSrtp(byte[])}). The sequence number
 * wraps modulo 65536 per RFC 3550.
 *
 * <p>The initial sequence number and timestamp are randomised at construction. This is not a
 * confidentiality measure, which SRTP provides, but the RFC 3550 section 5.1 rule that keeps a
 * fresh stream from snapping a receiver to a wraparound boundary.
 */
public final class RtpSender {
    /**
     * Payload type stamped on every packet, in {@code [0, 127]}.
     */
    private final int payloadType;

    /**
     * 32-bit synchronization source identifier announced by this stream.
     */
    private final int ssrc;

    /**
     * Codec clock rate in Hz used to scale presentation timestamps into RTP ticks; 48000 for Opus
     * and 90000 for video.
     */
    private final int clockRate;

    /**
     * SRTP endpoint that protects every packet before it is dispatched.
     */
    private final SrtpEndpoint srtp;

    /**
     * Sink that receives the protected SRTP bytes.
     */
    private final Consumer<byte[]> protectedSink;

    /**
     * Optional end-to-end transform applied to each codec payload before it is packetized and SRTP
     * protected, or {@code null} to send the payload unchanged. On a real call this is the SFrame
     * encryptor that seals the Opus payload end to end inside the hop-by-hop SRTP.
     */
    private volatile java.util.function.UnaryOperator<byte[]> outboundTransform;

    /**
     * Next sequence number to assign, wrapping at {@code 0xFFFF}.
     */
    private int nextSequenceNumber;

    /**
     * Random initial timestamp offset added to every per-packet tick count derived from the
     * presentation timestamp.
     */
    private final long timestampBase;

    /**
     * Presentation timestamp of the most recently sent packet in milliseconds, or {@code -1} before
     * the first send, retained to enforce a monotonically non-decreasing stream.
     */
    private long lastPtsMs = -1;

    /**
     * The RTP timestamp stamped on the most recently sent packet, exposed for RTCP sender reports.
     */
    private volatile long lastRtpTimestamp;

    /**
     * The running count of packets sent on this stream, exposed for the RTCP sender report's
     * sender's packet count field.
     */
    private volatile long sentPackets;

    /**
     * The running count of payload octets sent on this stream, exposed for the RTCP sender report's
     * sender's octet count field.
     */
    private volatile long sentOctets;

    /**
     * Constructs a sender with a randomised initial sequence number and timestamp.
     *
     * @param payloadType   the RTP payload type in {@code [0, 127]}
     * @param ssrc          the SSRC identifier
     * @param clockRate     the codec clock rate in Hz
     * @param srtp          the SRTP endpoint
     * @param protectedSink the consumer for the protected bytes
     * @throws NullPointerException     if {@code srtp} or {@code protectedSink} is {@code null}
     * @throws IllegalArgumentException if {@code payloadType} is outside {@code [0, 127]} or
     *                                  {@code clockRate} is not positive
     */
    public RtpSender(int payloadType, int ssrc, int clockRate,
                     SrtpEndpoint srtp, Consumer<byte[]> protectedSink) {
        this(payloadType, ssrc, clockRate, srtp, protectedSink, new SecureRandom());
    }

    /**
     * Constructs a sender seeded from an explicit random source.
     *
     * <p>This overload makes the randomised initial sequence number and timestamp reproducible, for
     * example under a deterministic random source in tests.
     *
     * @param payloadType   the RTP payload type in {@code [0, 127]}
     * @param ssrc          the SSRC identifier
     * @param clockRate     the codec clock rate in Hz
     * @param srtp          the SRTP endpoint
     * @param protectedSink the consumer for the protected bytes
     * @param random        the random source seeding the initial sequence number and timestamp
     * @throws NullPointerException     if {@code srtp}, {@code protectedSink}, or {@code random} is
     *                                  {@code null}
     * @throws IllegalArgumentException if {@code payloadType} is outside {@code [0, 127]} or
     *                                  {@code clockRate} is not positive
     */
    public RtpSender(int payloadType, int ssrc, int clockRate,
                     SrtpEndpoint srtp, Consumer<byte[]> protectedSink, SecureRandom random) {
        if (payloadType < 0 || payloadType > 0x7F) {
            throw new IllegalArgumentException(
                    "payloadType out of range [0, 127]: " + payloadType);
        }
        if (clockRate <= 0) {
            throw new IllegalArgumentException("clockRate must be > 0");
        }
        this.payloadType = payloadType;
        this.ssrc = ssrc;
        this.clockRate = clockRate;
        this.srtp = Objects.requireNonNull(srtp, "srtp cannot be null");
        this.protectedSink = Objects.requireNonNull(protectedSink, "protectedSink cannot be null");
        Objects.requireNonNull(random, "random cannot be null");
        this.nextSequenceNumber = random.nextInt(0x10000);
        this.timestampBase = Integer.toUnsignedLong(random.nextInt());
    }

    /**
     * Sends one codec payload as a protected RTP packet.
     *
     * <p>The method assigns the next sequence number, scales {@code ptsMs} by the clock rate and
     * adds the random timestamp base to form the 32-bit RTP timestamp, encodes the packet, protects
     * it with SRTP, and dispatches the protected bytes to the sink. The presentation timestamp must
     * be monotonically non-decreasing because the sender does not reorder.
     *
     * @param payload the codec payload bytes, for example one Opus packet
     * @param ptsMs   the source-frame presentation timestamp in milliseconds, which must be
     *                monotonically non-decreasing
     * @param marker  whether to set the M bit (audio: first packet of a talkspurt; video: last
     *                packet of a frame)
     * @throws NullPointerException      if {@code payload} is {@code null}
     * @throws IllegalArgumentException  if {@code ptsMs} is negative or less than the previous
     *                                   {@code ptsMs}
     * @throws WhatsAppCallException.Rtp if the protected sink throws while accepting the bytes
     */
    public void send(byte[] payload, long ptsMs, boolean marker) {
        Objects.requireNonNull(payload, "payload cannot be null");
        if (ptsMs < 0) {
            throw new IllegalArgumentException("ptsMs must be >= 0");
        }
        if (lastPtsMs >= 0 && ptsMs < lastPtsMs) {
            throw new IllegalArgumentException(
                    "ptsMs must be monotonically non-decreasing: prev=" + lastPtsMs
                            + " now=" + ptsMs);
        }
        var transform = outboundTransform;
        var effectivePayload = transform == null ? payload : transform.apply(payload);
        var ticks = ptsMs * clockRate / 1000;
        var timestamp = (timestampBase + ticks) & 0xFFFFFFFFL;
        var seq = nextSequenceNumber & 0xFFFF;
        nextSequenceNumber = (nextSequenceNumber + 1) & 0xFFFF;
        var packet = new RtpPacket(marker, payloadType, seq, timestamp, ssrc, effectivePayload);
        var encoded = packet.encode();
        var protectedBytes = srtp.protectRtp(encoded);
        try {
            protectedSink.accept(protectedBytes);
        } catch (RuntimeException e) {
            throw new WhatsAppCallException.Rtp("protected sink threw", e);
        }
        lastPtsMs = ptsMs;
        lastRtpTimestamp = timestamp;
        sentPackets++;
        sentOctets += effectivePayload.length;
    }

    /**
     * Sets the end-to-end payload transform applied before packetization, or clears it with
     * {@code null}.
     *
     * <p>The transform maps each cleartext codec payload to the bytes carried in the RTP payload; on a
     * real call it is the SFrame encryptor. Passing {@code null} sends payloads unchanged, as the
     * loopback tests do.
     *
     * @param transform the payload transform, or {@code null} for no transform
     */
    public void setOutboundTransform(java.util.function.UnaryOperator<byte[]> transform) {
        this.outboundTransform = transform;
    }

    /**
     * Returns the RTP timestamp stamped on the most recently sent packet.
     *
     * <p>Used to populate the RTP timestamp field of an RTCP sender report so the report shares the
     * stream's timeline; the value is {@code 0} before the first {@link #send(byte[], long, boolean)}.
     *
     * @return the last sent RTP timestamp as an unsigned 32-bit value held in a {@code long}
     */
    public long lastRtpTimestamp() {
        return lastRtpTimestamp;
    }

    /**
     * Returns the number of packets sent on this stream.
     *
     * @return the cumulative sent packet count
     */
    public long sentPackets() {
        return sentPackets;
    }

    /**
     * Returns the number of payload octets sent on this stream, excluding RTP headers.
     *
     * @return the cumulative sent octet count
     */
    public long sentOctets() {
        return sentOctets;
    }

    /**
     * Returns the sequence number that the next {@link #send(byte[], long, boolean)} would assign.
     *
     * @return the next sequence number in {@code [0, 65535]}
     */
    public int nextSequenceNumber() {
        return nextSequenceNumber;
    }

    /**
     * Returns the SSRC stamped on every packet.
     *
     * @return the SSRC identifier
     */
    public int ssrc() {
        return ssrc;
    }

    /**
     * Returns the configured codec clock rate.
     *
     * @return the clock rate in Hz
     */
    public int clockRate() {
        return clockRate;
    }
}
