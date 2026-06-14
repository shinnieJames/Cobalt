package com.github.auties00.cobalt.call.rtp;

import com.github.auties00.cobalt.exception.WhatsAppCallException;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Objects;

/**
 * One decoded RTP packet (RFC 3550 section 5.1): the fixed 12-byte header plus its payload bytes.
 *
 * <p>The {@link #encode()} and {@link #decode(byte[])} helpers serialise and parse the on-wire
 * representation; the {@link RtpSender} and {@link RtpReceiver} use them. The wire layout is:
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
 * <p>Outbound packets never carry padding ({@code P=0}) or contributing sources ({@code CC=0}), but
 * always carry an empty header extension ({@code X=1}) whose preamble is {@link #EXTENSION_PROFILE}
 * with a zero-word length, matching the shape WhatsApp's media engine stamps on every sent packet.
 * Inbound packets are decoded with any padding, CSRC list, or extension bytes skipped, so a peer that
 * populates the extension with one-byte header elements is tolerated.
 *
 * @param marker         the M bit; for audio it is set on the first packet of each talkspurt, for
 *                       video on the last packet of each frame (RFC 3551 section 5)
 * @param payloadType    the 7-bit payload type (RFC 3551; 111 for Opus, 96 for VP8 in WebRTC)
 * @param sequenceNumber the 16-bit sequence number, which wraps modulo 65536
 * @param timestamp      the 32-bit RTP timestamp, whose clock rate depends on the payload type
 *                       (48 kHz for Opus, 90 kHz for video)
 * @param ssrc           the 32-bit synchronization source identifier
 * @param payload        the payload bytes, excluding any RTP header
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
     * RTP version carried in the two high bits of the first header byte, fixed at {@code 2} by
     * RFC 3550.
     */
    public static final int VERSION = 2;

    /**
     * Length in bytes of the fixed RTP header, excluding any CSRC list or header extension.
     */
    public static final int FIXED_HEADER_LENGTH = 12;

    /**
     * Defined-by-profile value of the RTP header-extension preamble WhatsApp stamps on every outbound
     * media packet.
     *
     * @implNote This implementation uses {@code 0xDEBE} rather than the RFC 8285 one-byte profile
     * {@code 0xBEDE}; WhatsApp's media engine emits {@code 0xDEBE} verbatim, confirmed byte-for-byte
     * against captured sender packets.
     */
    public static final int EXTENSION_PROFILE = 0xDEBE;

    /**
     * Length in bytes of the extension block appended to every outbound packet: the 4-byte preamble
     * ({@link #EXTENSION_PROFILE} plus a one-word length) followed by {@link #EXTENSION_WORD} (one
     * 32-bit extension word).
     */
    public static final int EXTENSION_HEADER_LENGTH = 8;

    /**
     * The single RFC 8285 one-byte header-extension word WhatsApp stamps on every outbound media
     * packet, captured byte-for-byte from a sender as {@code 0x30 0x01 0x00 0x00}: element id 3 with a
     * one-byte value {@code 0x01}, zero-padded to the 32-bit word boundary. The native peer drops media
     * that omits this word (it carries the empty {@code 0xDEBE 0x0000} preamble alone), so it must be
     * present for the relay-bridged peer to accept and play the stream.
     */
    public static final int EXTENSION_WORD = 0x30010000;

    /**
     * Validates the components and rejects an out-of-range header field.
     *
     * @throws NullPointerException     if {@code payload} is {@code null}
     * @throws IllegalArgumentException if {@code payloadType} is outside {@code [0, 127]},
     *                                  {@code sequenceNumber} is outside {@code [0, 65535]}, or
     *                                  {@code timestamp} is outside {@code [0, 2^32)}
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
     * Encodes this packet into its big-endian on-wire byte sequence.
     *
     * <p>The output is a fresh array of {@link #FIXED_HEADER_LENGTH} plus {@link #EXTENSION_HEADER_LENGTH}
     * plus payload bytes, with {@code P} and {@code CC} zero and {@code X} set, the M bit and payload
     * type packed into the second byte, the sequence number, timestamp, and SSRC written big-endian,
     * and an empty {@link #EXTENSION_PROFILE} extension preamble with a zero-word length following the
     * SSRC, ahead of the payload.
     *
     * @return a freshly allocated byte array holding the encoded packet
     */
    public byte[] encode() {
        var buf = ByteBuffer.allocate(FIXED_HEADER_LENGTH + EXTENSION_HEADER_LENGTH + payload.length)
                .order(ByteOrder.BIG_ENDIAN);
        buf.put((byte) ((VERSION << 6) | 0x10));
        buf.put((byte) ((marker ? 0x80 : 0) | (payloadType & 0x7F)));
        buf.putShort((short) sequenceNumber);
        buf.putInt((int) timestamp);
        buf.putInt(ssrc);
        buf.putShort((short) EXTENSION_PROFILE);
        buf.putShort((short) 1);
        buf.putInt(EXTENSION_WORD);
        buf.put(payload);
        return buf.array();
    }

    /**
     * Decodes an inbound RTP packet from its big-endian on-wire byte sequence.
     *
     * <p>The fixed header is parsed and its version field checked. Any CSRC list ({@code CC}
     * entries of 4 bytes each) and one-shot header extension ({@code X=1}) are skipped. When the
     * padding bit is set and the payload is non-empty, the trailing pad-count byte is honoured and
     * the corresponding bytes are removed from the returned payload.
     *
     * @param bytes the received bytes
     * @return the parsed packet
     * @throws NullPointerException      if {@code bytes} is {@code null}
     * @throws WhatsAppCallException.Rtp if {@code bytes} is shorter than {@link #FIXED_HEADER_LENGTH},
     *                                   the version field is not {@link #VERSION}, the CSRC list or
     *                                   extension is truncated, or the padding count exceeds the
     *                                   payload length
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
            buf.getShort();
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
