package com.github.auties00.cobalt.socket.transport.websocket.frame;

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

    public static byte maskByte(int maskKey, int index) {
        return switch (index & 3) {
            case 0 -> (byte) (maskKey >>> 24);
            case 1 -> (byte) (maskKey >>> 16);
            case 2 -> (byte) (maskKey >>> 8);
            default -> (byte) maskKey;
        };
    }
}
