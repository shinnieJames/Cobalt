package com.github.auties00.cobalt.socket.layer.application.websocket.frame;

/**
 * Constants for WebSocket frame encoding and decoding as defined by
 * <a href="https://datatracker.ietf.org/doc/html/rfc6455">RFC 6455</a>.
 *
 * @implNote No WhatsApp Web counterpart: WA Web relies on the browser's
 *     native {@code WebSocket} object, so the RFC 6455 wire format is
 *     never touched by application code.  Cobalt implements the wire
 *     format itself and therefore needs these constants.
 */
public final class WebSocketFrameConstants {
    /**
     * Opcode {@code 0x0}: continuation of a fragmented message.
     */
    public static final byte OPCODE_CONTINUATION = 0x0;

    /**
     * Opcode {@code 0x1}: UTF-8 text frame.
     */
    public static final byte OPCODE_TEXT = 0x1;

    /**
     * Opcode {@code 0x2}: binary frame.  The only data opcode used by
     * WhatsApp.
     */
    public static final byte OPCODE_BINARY = 0x2;

    /**
     * Opcode {@code 0x8}: close control frame.
     */
    public static final byte OPCODE_CLOSE = 0x8;

    /**
     * Opcode {@code 0x9}: ping control frame.
     */
    public static final byte OPCODE_PING = 0x9;

    /**
     * Opcode {@code 0xA}: pong control frame.
     */
    public static final byte OPCODE_PONG = 0xA;

    /**
     * Inclusive upper bound for a frame payload length that fits in the
     * 7-bit field of the second header byte.
     */
    public static final int SMALL_PAYLOAD_LIMIT = 125;

    /**
     * Marker value in the 7-bit payload-length field signalling that an
     * additional 16-bit extended length follows.
     */
    public static final int EXTENDED_16_PAYLOAD_MARKER = 126;

    /**
     * Marker value in the 7-bit payload-length field signalling that an
     * additional 64-bit extended length follows.
     */
    public static final int EXTENDED_64_PAYLOAD_MARKER = 127;

    /**
     * Maximum payload length permitted for any control frame, as required
     * by <a href="https://datatracker.ietf.org/doc/html/rfc6455#section-5.5">RFC 6455 §5.5</a>.
     */
    public static final int CONTROL_PAYLOAD_MAX_LENGTH = 125;

    /**
     * Size of the reusable chunk buffer used by the decoder to batch
     * unmasking and delivery of data-frame payloads.
     */
    public static final int UNMASK_CHUNK_SIZE = 8192;

    /**
     * Protective upper bound on a single frame's payload length; the
     * decoder rejects any frame larger than this.  Sized well above any
     * payload WhatsApp is expected to send (16 MiB).
     */
    public static final long MAX_FRAME_LENGTH = 16L * 1024 * 1024;

    /**
     * Prevents instantiation of this constants holder.
     */
    private WebSocketFrameConstants() {

    }

    /**
     * Returns the mask byte for the given index within a 4-byte masking key.
     *
     * @param maskKey the 4-byte masking key
     * @param index   the byte index within the payload
     * @return the mask byte for the given index
     */
    public static byte maskByte(int maskKey, int index) {
        return (byte) (maskKey >>> ((~index & 3) << 3));
    }
}
