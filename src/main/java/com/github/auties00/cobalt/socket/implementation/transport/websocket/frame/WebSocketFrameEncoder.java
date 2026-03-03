package com.github.auties00.cobalt.socket.implementation.transport.websocket.frame;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.concurrent.ThreadLocalRandom;

public final class WebSocketFrameEncoder {
    private static final ByteBuffer EMPTY_PAYLOAD = ByteBuffer.allocate(0);

    private WebSocketFrameEncoder() {

    }

    public static WebSocketEncodedFrame encodeBinaryFrame(ByteBuffer payload) {
        return encodeFrame(WebSocketFrameConstants.OPCODE_BINARY, payload);
    }

    public static WebSocketEncodedFrame encodeControlFrame(byte opcode, byte[] payload, int length) {
        if (length < 0 || length > WebSocketFrameConstants.CONTROL_PAYLOAD_MAX_LENGTH) {
            throw new IllegalArgumentException("Invalid control payload length: " + length);
        }

        var content = length == 0
                ? EMPTY_PAYLOAD
                : ByteBuffer.wrap(Arrays.copyOf(payload, length));
        return encodeFrame(opcode, content);
    }

    public static WebSocketEncodedFrame encodeFrame(byte opcode, ByteBuffer payload) {
        var payloadView = payload.duplicate();
        var payloadLength = payloadView.remaining();
        var maskKey = ThreadLocalRandom.current().nextInt();

        var header = ByteBuffer.allocate(headerLength(payloadLength));
        header.put((byte) (0x80 | (opcode & 0x0F)));
        if (payloadLength <= WebSocketFrameConstants.SMALL_PAYLOAD_LIMIT) {
            header.put((byte) (0x80 | payloadLength));
        } else if (payloadLength <= 0xFFFF) {
            header.put((byte) (0x80 | WebSocketFrameConstants.EXTENDED_16_PAYLOAD_MARKER));
            header.putShort((short) payloadLength);
        } else {
            header.put((byte) (0x80 | WebSocketFrameConstants.EXTENDED_64_PAYLOAD_MARKER));
            header.putLong(payloadLength);
        }
        header.putInt(maskKey);
        header.flip();

        if (payloadLength == 0) {
            return new WebSocketEncodedFrame(header, EMPTY_PAYLOAD);
        }

        var maskedPayload = maskPayload(payloadView, maskKey);
        return new WebSocketEncodedFrame(header, maskedPayload);
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
            var value = (byte) (source.get() ^ WebSocketFrameConstants.maskByte(maskKey, i));
            masked.put(value);
        }
        masked.flip();
        return masked;
    }

    private static void applyMask(byte[] array, int offset, int length, int maskKey, int maskOffset) {
        for (var i = 0; i < length; i++) {
            array[offset + i] ^= WebSocketFrameConstants.maskByte(maskKey, maskOffset + i);
        }
    }

    private static int headerLength(int payloadLength) {
        if (payloadLength <= WebSocketFrameConstants.SMALL_PAYLOAD_LIMIT) {
            return 2 + Integer.BYTES;
        }

        if (payloadLength <= 0xFFFF) {
            return 2 + Short.BYTES + Integer.BYTES;
        }

        return 2 + Long.BYTES + Integer.BYTES;
    }
}
