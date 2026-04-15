package com.github.auties00.cobalt.socket.layer.application.websocket.frame;

/**
 * Constants for WebSocket frame encoding and decoding as defined by
 * <a href="https://datatracker.ietf.org/doc/html/rfc6455">RFC 6455</a>.
 */
public final class WebSocketFrameConstants {
    public static final byte OPCODE_CONTINUATION = 0x0;
    public static final byte OPCODE_TEXT = 0x1;
    public static final byte OPCODE_BINARY = 0x2;
    public static final byte OPCODE_CLOSE = 0x8;
    public static final byte OPCODE_PING = 0x9;
    public static final byte OPCODE_PONG = 0xA;

    public static final int SMALL_PAYLOAD_LIMIT = 125;
    public static final int EXTENDED_16_PAYLOAD_MARKER = 126;
    public static final int EXTENDED_64_PAYLOAD_MARKER = 127;
    public static final int CONTROL_PAYLOAD_MAX_LENGTH = 125;
    public static final int UNMASK_CHUNK_SIZE = 8192;
    public static final long MAX_FRAME_LENGTH = 16L * 1024 * 1024;

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
