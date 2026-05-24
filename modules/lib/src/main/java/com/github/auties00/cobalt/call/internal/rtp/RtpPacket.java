package com.github.auties00.cobalt.call.internal.rtp;

import com.github.auties00.cobalt.exception.WhatsAppCallException;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Objects;

/**
 * One decoded RTP packet (RFC 3550 §5.1) — the fixed 12-byte header
 * plus its payload bytes. The encoder/decoder live as static helpers
 * on this record; the {@link RtpSender} and {@link RtpReceiver} use
 * them.
 *
 * <p>Wire format:
 *
 * <pre>{@code
 *  0                   1                   2                   3
 *  0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |V=2|P|X|  CC   |M|     PT      |       sequence number         |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |                           timestamp                           |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |           synchronization source (SSRC) identifier            |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |            contributing source (CSRC) identifiers (CC * 4 B)  |
 * |                             ....                              |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |                            payload                            |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * }</pre>
 *
 * <p>Cobalt does not emit padding ({@code P=0}), header extensions
 * ({@code X=0}), or contributing sources ({@code CC=0}) — the WebRTC
 * defaults. Inbound packets that carry any of these are decoded and
 * the relevant bytes skipped, so peer-supplied extensions don't break
 * us.
 *
 * @param marker      the M bit — for audio set on the first packet
 *                    of each talkspurt; for video set on the last
 *                    packet of each frame (RFC 3551 §5)
 * @param payloadType the 7-bit payload type (RFC 3551 — 111 for
 *                    Opus, 96 for VP8 in WebRTC)
 * @param sequenceNumber 16-bit sequence number, wraps modulo 65536
 * @param timestamp   32-bit RTP timestamp; clock rate depends on the
 *                    payload type (48 kHz for Opus, 90 kHz for video)
 * @param ssrc        the 32-bit synchronization source identifier
 * @param payload     the payload bytes (without any RTP header)
 */
public record RtpPacket(
        boolean marker,
        int payloadType,
        int sequenceNumber,
        long timestamp,
        int ssrc,
        byte[] payload
) {
    /**
     * RFC 3550 RTP version field — always 2.
     */
    public static final int VERSION = 2;

    /**
     * Length of the fixed RTP header (no CSRCs, no extension).
     */
    public static final int FIXED_HEADER_LENGTH = 12;

    /**
     * Compact constructor — null-checks payload and validates ranges.
     */
    public RtpPacket {
        Objects.requireNonNull(payload, "payload cannot be null");
        if (payloadType < 0 || payloadType > 0x7F) {
            throw new IllegalArgumentException(
                    "payloadType out of range [0, 127]: " + payloadType);
        }
        if (sequenceNumber < 0 || sequenceNumber > 0xFFFF) {
            throw new IllegalArgumentException(
                    "sequenceNumber out of range [0, 65535]: " + sequenceNumber);
        }
        if (timestamp < 0 || timestamp > 0xFFFFFFFFL) {
            throw new IllegalArgumentException(
                    "timestamp out of range [0, 2^32): " + timestamp);
        }
    }

    /**
     * Encodes this packet to its on-wire bytes.
     *
     * @return a freshly-allocated byte array
     */
    public byte[] encode() {
        var buf = ByteBuffer.allocate(FIXED_HEADER_LENGTH + payload.length).order(ByteOrder.BIG_ENDIAN);
        buf.put((byte) (VERSION << 6));                                // V=2, P=0, X=0, CC=0
        buf.put((byte) ((marker ? 0x80 : 0) | (payloadType & 0x7F))); // M | PT
        buf.putShort((short) sequenceNumber);
        buf.putInt((int) timestamp);
        buf.putInt(ssrc);
        buf.put(payload);
        return buf.array();
    }

    /**
     * Decodes an inbound RTP packet from its on-wire bytes.
     *
     * @param bytes the captured / received bytes
     * @return the parsed packet
     * @throws WhatsAppCallException.Rtp         if the bytes are too short or the
     *                              version field is not 2
     * @throws NullPointerException if {@code bytes} is {@code null}
     */
    public static RtpPacket decode(byte[] bytes) {
        Objects.requireNonNull(bytes, "bytes cannot be null");
        if (bytes.length < FIXED_HEADER_LENGTH) {
            throw new WhatsAppCallException.Rtp("packet too short for RTP header: " + bytes.length);
        }
        var buf = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN);
        var b0 = buf.get() & 0xFF;
        var version = b0 >>> 6;
        if (version != VERSION) {
            throw new WhatsAppCallException.Rtp("unexpected RTP version: " + version);
        }
        var padding = (b0 & 0x20) != 0;
        var extension = (b0 & 0x10) != 0;
        var csrcCount = b0 & 0x0F;

        var b1 = buf.get() & 0xFF;
        var marker = (b1 & 0x80) != 0;
        var payloadType = b1 & 0x7F;
        var sequenceNumber = Short.toUnsignedInt(buf.getShort());
        var timestamp = Integer.toUnsignedLong(buf.getInt());
        var ssrc = buf.getInt();

        var csrcBytes = csrcCount * 4;
        if (buf.remaining() < csrcBytes) {
            throw new WhatsAppCallException.Rtp("truncated CSRC list: need " + csrcBytes
                    + ", have " + buf.remaining());
        }
        buf.position(buf.position() + csrcBytes);

        if (extension) {
            if (buf.remaining() < 4) {
                throw new WhatsAppCallException.Rtp("truncated extension header");
            }
            buf.getShort(); // profile-specific id, ignored
            var extLengthWords = Short.toUnsignedInt(buf.getShort());
            var extLengthBytes = extLengthWords * 4;
            if (buf.remaining() < extLengthBytes) {
                throw new WhatsAppCallException.Rtp("truncated extension data: need " + extLengthBytes
                        + ", have " + buf.remaining());
            }
            buf.position(buf.position() + extLengthBytes);
        }

        var payloadLength = buf.remaining();
        if (padding && payloadLength > 0) {
            var padBytes = bytes[bytes.length - 1] & 0xFF;
            if (padBytes > payloadLength) {
                throw new WhatsAppCallException.Rtp("padding count " + padBytes
                        + " exceeds payload length " + payloadLength);
            }
            payloadLength -= padBytes;
        }
        var payload = new byte[payloadLength];
        buf.get(payload);
        return new RtpPacket(marker, payloadType, sequenceNumber, timestamp, ssrc, payload);
    }
}
