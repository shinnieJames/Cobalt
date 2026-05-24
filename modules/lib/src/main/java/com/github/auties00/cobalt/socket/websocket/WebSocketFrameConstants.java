package com.github.auties00.cobalt.socket.websocket;

/**
 * Wire-format constants for WebSocket framing, as defined by
 * <a href="https://datatracker.ietf.org/doc/html/rfc6455">RFC 6455</a>.
 *
 * @apiNote
 * Shared between {@link WebSocketFrameInputStream} and
 * {@link WebSocketFrameOutputStream} so opcode values, length-field
 * markers and the masking helper live in exactly one place. Public
 * because both streams expose control opcodes
 * ({@link #OPCODE_PONG}, {@link #OPCODE_CLOSE}) on their APIs.
 */
public final class WebSocketFrameConstants {
    /**
     * The opcode {@code 0x0} for a continuation of a fragmented
     * message.
     */
    public static final byte OPCODE_CONTINUATION = 0x0;

    /**
     * The opcode {@code 0x1} for a UTF-8 text frame.
     */
    public static final byte OPCODE_TEXT = 0x1;

    /**
     * The opcode {@code 0x2} for a binary frame.
     *
     * @apiNote
     * The only data opcode used by WhatsApp: every WhatsApp frame on
     * the {@code web.whatsapp.com} WebSocket is binary.
     */
    public static final byte OPCODE_BINARY = 0x2;

    /**
     * The opcode {@code 0x8} for a close control frame.
     */
    public static final byte OPCODE_CLOSE = 0x8;

    /**
     * The opcode {@code 0x9} for a ping control frame.
     */
    public static final byte OPCODE_PING = 0x9;

    /**
     * The opcode {@code 0xA} for a pong control frame.
     */
    public static final byte OPCODE_PONG = 0xA;

    /**
     * The inclusive upper bound for a frame payload length that fits
     * in the seven-bit field of the second header byte.
     */
    public static final int SMALL_PAYLOAD_LIMIT = 125;

    /**
     * The marker value in the seven-bit payload-length field
     * signalling that an additional 16-bit extended length follows.
     */
    public static final int EXTENDED_16_PAYLOAD_MARKER = 126;

    /**
     * The marker value in the seven-bit payload-length field
     * signalling that an additional 64-bit extended length follows.
     */
    public static final int EXTENDED_64_PAYLOAD_MARKER = 127;

    /**
     * The maximum payload length permitted for any control frame,
     * as required by
     * <a href="https://datatracker.ietf.org/doc/html/rfc6455#section-5.5">RFC 6455 section 5.5</a>.
     */
    public static final int CONTROL_PAYLOAD_MAX_LENGTH = 125;

    /**
     * The default size of the recyclable scratch buffer used by the
     * input-side unmask path and the output-side header builder.
     */
    public static final int SCRATCH_BUFFER_SIZE = 8192;

    /**
     * The protective upper bound on a single frame's payload length.
     *
     * @apiNote
     * Sized at 16 MiB, well above any payload WhatsApp is expected
     * to send; the decoder rejects any frame larger than this so a
     * malformed length prefix cannot wedge the reader on a
     * multi-gigabyte allocation.
     */
    public static final long MAX_FRAME_LENGTH = 16L * 1024 * 1024;

    /**
     * Prevents instantiation of this constants holder.
     */
    private WebSocketFrameConstants() {

    }

    /**
     * Returns the mask byte for the given index within a four-byte
     * masking key.
     *
     * @apiNote
     * Shared by both streams so the mask cycle layout is encoded in
     * exactly one place; the formula reproduces the byte layout
     * expected by RFC 6455 section 5.3 and matches the big-endian
     * mask-key storage convention used by the SIMD unmask path.
     *
     * @param maskKey the four-byte masking key
     * @param index   the byte index within the payload
     * @return the mask byte for the given index
     */
    public static byte maskByte(int maskKey, int index) {
        return (byte) (maskKey >>> ((~index & 3) << 3));
    }
}
