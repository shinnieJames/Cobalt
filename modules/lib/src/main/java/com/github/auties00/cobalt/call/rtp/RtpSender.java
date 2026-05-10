package com.github.auties00.cobalt.call.rtp;

import com.github.auties00.cobalt.call.rtp.srtp.SrtpEndpoint;
import com.github.auties00.cobalt.exception.WhatsAppCallException;

import java.security.SecureRandom;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Outbound side of an RTP stream — packetises payloads (Opus
 * frames, VP8 frames, etc.), assigns sequence numbers and RTP
 * timestamps, runs them through {@link SrtpEndpoint#protectRtp}, and
 * hands the protected bytes to a {@link Consumer} (typically wired to
 * {@link com.github.auties00.cobalt.call.transport.dtls.DtlsSrtpDriver#sendSrtp}).
 *
 * <p>RTP timestamps are computed from the {@code ptsMs} the codec
 * supplies, scaled by the configured {@link #clockRate} (RFC 3551
 * §6 — 48 kHz for Opus, 90 kHz for video). The internal sequence
 * number wraps modulo 65536 per RFC 3550.
 *
 * <p>The first sequence number and timestamp are randomised at
 * construction per RFC 3550 §5.1 — this isn't a security guarantee
 * (SRTP provides confidentiality), it's a rule the spec mandates so
 * receivers don't snap to wraparound on a fresh stream.
 */
public final class RtpSender {
    /**
     * The configured RTP payload type (0..127).
     */
    private final int payloadType;

    /**
     * The 32-bit synchronization source identifier this stream
     * announces.
     */
    private final int ssrc;

    /**
     * Codec clock rate in Hz — 48000 for Opus, 90000 for video.
     */
    private final int clockRate;

    /**
     * The SRTP endpoint that protects every packet before it ships.
     */
    private final SrtpEndpoint srtp;

    /**
     * Where protected SRTP bytes are delivered.
     */
    private final Consumer<byte[]> protectedSink;

    /**
     * Next sequence number to use. Wraps at 0xFFFF.
     */
    private int nextSequenceNumber;

    /**
     * Initial timestamp offset — added to every per-packet timestamp
     * computed from {@code ptsMs}.
     */
    private final long timestampBase;

    /**
     * The {@code ptsMs} of the last sent packet, retained so callers
     * can omit the parameter on subsequent calls (e.g. when the codec
     * doesn't carry its own pts) and still get a monotonic stream.
     */
    private long lastPtsMs = -1;

    /**
     * Constructs a new sender with a randomised initial sequence and
     * timestamp.
     *
     * @param payloadType    RTP payload type (0..127)
     * @param ssrc           SSRC identifier
     * @param clockRate      codec clock rate in Hz
     * @param srtp           SRTP endpoint
     * @param protectedSink  consumer for the protected bytes
     * @throws NullPointerException     if any required argument is
     *                                  {@code null}
     * @throws IllegalArgumentException if {@code payloadType} or
     *                                  {@code clockRate} is out of
     *                                  range
     */
    public RtpSender(int payloadType, int ssrc, int clockRate,
                     SrtpEndpoint srtp, Consumer<byte[]> protectedSink) {
        this(payloadType, ssrc, clockRate, srtp, protectedSink, new SecureRandom());
    }

    /**
     * Test-friendly constructor that takes an explicit
     * {@link SecureRandom} so the initial sequence/timestamp are
     * reproducible.
     *
     * @param payloadType   RTP payload type
     * @param ssrc          SSRC identifier
     * @param clockRate     codec clock rate
     * @param srtp          SRTP endpoint
     * @param protectedSink protected bytes sink
     * @param random        random source
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
     * Sends one payload as an RTP packet — assigns the next sequence
     * number, computes the RTP timestamp from {@code ptsMs}, encodes
     * the header, applies SRTP protection, and dispatches.
     *
     * @param payload the codec payload bytes (e.g. one Opus packet)
     * @param ptsMs   the source-frame presentation timestamp in ms;
     *                must be monotonically non-decreasing (the sender
     *                doesn't reorder)
     * @param marker  whether to set the M bit (audio: first packet of
     *                a talkspurt; video: last packet of a frame)
     * @throws NullPointerException     if {@code payload} is {@code null}
     * @throws IllegalArgumentException if {@code ptsMs} is negative or
     *                                  goes backwards
     * @throws WhatsAppCallException.Rtp             if SRTP protection fails
     */
    public void send(byte[] payload, long ptsMs, boolean marker) {
        Objects.requireNonNull(payload, "payload cannot be null");
        if (ptsMs < 0) {
            throw new IllegalArgumentException("ptsMs must be ≥ 0");
        }
        if (lastPtsMs >= 0 && ptsMs < lastPtsMs) {
            throw new IllegalArgumentException(
                    "ptsMs must be monotonically non-decreasing: prev=" + lastPtsMs
                            + " now=" + ptsMs);
        }
        long ticks = ptsMs * clockRate / 1000;
        long timestamp = (timestampBase + ticks) & 0xFFFFFFFFL;
        int seq = nextSequenceNumber & 0xFFFF;
        nextSequenceNumber = (nextSequenceNumber + 1) & 0xFFFF;
        var packet = new RtpPacket(marker, payloadType, seq, timestamp, ssrc, payload);
        var protectedBytes = srtp.protectRtp(packet.encode());
        try {
            protectedSink.accept(protectedBytes);
        } catch (RuntimeException e) {
            throw new WhatsAppCallException.Rtp("protected sink threw", e);
        }
        lastPtsMs = ptsMs;
    }

    /**
     * Returns the next sequence number that {@link #send} would use.
     *
     * @return the next sequence number (0..65535)
     */
    public int nextSequenceNumber() {
        return nextSequenceNumber;
    }

    /**
     * Returns the SSRC this sender stamps on every packet.
     *
     * @return the SSRC
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
