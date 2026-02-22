package com.github.auties00.cobalt.socket.implementation.websocket;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Encodes client websocket frames.
 */
public final class WebSocketFrameEncoder {
    public static final byte OPCODE_BINARY = 0x2;
    public static final byte OPCODE_CLOSE = 0x8;
    public static final byte OPCODE_PING = 0x9;
    public static final byte OPCODE_PONG = 0xA;

    private static final int SMALL_PAYLOAD_LIMIT = 125;
    private static final int EXTENDED_16_PAYLOAD_MARKER = 126;
    private static final int EXTENDED_64_PAYLOAD_MARKER = 127;
    private static final ByteBuffer EMPTY_PAYLOAD = ByteBuffer.allocate(0);

    private WebSocketFrameEncoder() {

    }

    public static EncodedFrame encodeBinaryFrame(ByteBuffer payload) {
        return encodeFrame(OPCODE_BINARY, payload);
    }

    public static EncodedFrame encodeControlFrame(byte opcode, byte[] payload, int length) {
        if (length < 0 || length > SMALL_PAYLOAD_LIMIT) {
            throw new IllegalArgumentException("Invalid control payload length: " + length);
        }

        var content = length == 0
                ? EMPTY_PAYLOAD
                : ByteBuffer.wrap(Arrays.copyOf(payload, length));
        return encodeFrame(opcode, content);
    }

    public static EncodedFrame encodeFrame(byte opcode, ByteBuffer payload) {
        var payloadView = payload.duplicate();
        var payloadLength = payloadView.remaining();
        var maskKey = ThreadLocalRandom.current().nextInt();

        var header = ByteBuffer.allocate(headerLength(payloadLength));
        header.put((byte) (0x80 | (opcode & 0x0F)));
        if (payloadLength <= SMALL_PAYLOAD_LIMIT) {
            header.put((byte) (0x80 | payloadLength));
        } else if (payloadLength <= 0xFFFF) {
            header.put((byte) (0x80 | EXTENDED_16_PAYLOAD_MARKER));
            header.putShort((short) payloadLength);
        } else {
            header.put((byte) (0x80 | EXTENDED_64_PAYLOAD_MARKER));
            header.putLong(payloadLength);
        }
        header.putInt(maskKey);
        header.flip();

        if (payloadLength == 0) {
            return new EncodedFrame(header, EMPTY_PAYLOAD);
        }

        var maskedPayload = maskPayload(payloadView, maskKey);
        return new EncodedFrame(header, maskedPayload);
    }

    private static ByteBuffer maskPayload(ByteBuffer payload, int maskKey) {
        var payloadLength = payload.remaining();
        if (!payload.isReadOnly() && payload.hasArray()) {
            var array = payload.array();
            var start = payload.arrayOffset() + payload.position();
            applyMask(array, start, payloadLength, maskKey, 0);
            return payload;
        }

        var masked = ByteBuffer.allocate(payloadLength);
        var source = payload.duplicate();
        for (var i = 0; i < payloadLength; i++) {
            var value = (byte) (source.get() ^ maskByte(maskKey, i));
            masked.put(value);
        }
        masked.flip();
        return masked;
    }

    private static void applyMask(byte[] array, int offset, int length, int maskKey, int maskOffset) {
        for (var i = 0; i < length; i++) {
            array[offset + i] ^= maskByte(maskKey, maskOffset + i);
        }
    }

    public static byte maskByte(int maskKey, int index) {
        return switch (index & 3) {
            case 0 -> (byte) (maskKey >>> 24);
            case 1 -> (byte) (maskKey >>> 16);
            case 2 -> (byte) (maskKey >>> 8);
            default -> (byte) maskKey;
        };
    }

    private static int headerLength(int payloadLength) {
        if (payloadLength <= SMALL_PAYLOAD_LIMIT) {
            return 2 + Integer.BYTES;
        }

        if (payloadLength <= 0xFFFF) {
            return 2 + Short.BYTES + Integer.BYTES;
        }

        return 2 + Long.BYTES + Integer.BYTES;
    }

    public record EncodedFrame(ByteBuffer header, ByteBuffer payload) {

    }
}
