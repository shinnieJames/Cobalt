package com.github.auties00.cobalt.socket.application.websocket.frame.encoder;

import com.github.auties00.cobalt.socket.application.websocket.frame.WebSocketFrameConstants;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Encodes WebSocket frames according to
 * <a href="https://datatracker.ietf.org/doc/html/rfc6455">RFC 6455</a>.
 *
 * <p>All frames produced by this encoder are masked, as required for
 * client-to-server WebSocket communication.
 */
public final class WebSocketFrameEncoder {
    private static final ByteBuffer EMPTY_PAYLOAD = ByteBuffer.allocate(0);

    private WebSocketFrameEncoder() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated.");
    }

    /**
     * Encodes a binary data frame with the given payload.
     *
     * @param payload the payload to encode
     * @return the encoded frame
     */
    public static WebSocketEncodedFrame encodeBinaryFrame(ByteBuffer payload) {
        return encodeFrame(WebSocketFrameConstants.OPCODE_BINARY, payload);
    }

    /**
     * Encodes a single binary WebSocket message from one or more payload
     * buffers.
     *
     * <p>The returned array is ordered as {@code [header, payload...]} and can
     * be sent directly with gather-write APIs. All payload buffers are masked
     * as one logical message, so the mask offset continues across buffer
     * boundaries.
     *
     * @param payloads the payload buffers in message order
     * @return frame buffers ready to send
     */
    public static ByteBuffer[] encodeBinaryMessage(ByteBuffer... payloads) {
        if (payloads == null || payloads.length == 0) {
            return new ByteBuffer[0];
        }

        var segments = new ArrayList<ByteBuffer>(payloads.length);
        var sawPayloadArgument = false;
        var payloadLength = 0;
        for (var payload : payloads) {
            if (payload == null) {
                continue;
            }
            sawPayloadArgument = true;
            if (!payload.hasRemaining()) {
                continue;
            }
            var view = payload.duplicate();
            payloadLength = Math.addExact(payloadLength, view.remaining());
            segments.add(view);
        }

        if (payloadLength == 0) {
            if (!sawPayloadArgument) {
                return new ByteBuffer[0];
            }
            var frame = encodeFrame(WebSocketFrameConstants.OPCODE_BINARY, EMPTY_PAYLOAD);
            return new ByteBuffer[]{frame.header()};
        }

        var maskKey = ThreadLocalRandom.current().nextInt();
        var header = ByteBuffer.allocate(headerLength(payloadLength));
        header.put((byte) (0x80 | (WebSocketFrameConstants.OPCODE_BINARY & 0x0F)));
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

        var result = new ByteBuffer[segments.size() + 1];
        result[0] = header;

        var maskOffset = 0;
        for (var i = 0; i < segments.size(); i++) {
            var segment = segments.get(i);
            result[i + 1] = maskPayloadSegment(segment, maskKey, maskOffset);
            maskOffset += segment.remaining();
        }

        return result;
    }

    /**
     * Encodes a control frame (ping, pong, or close).
     *
     * @param opcode  the control opcode
     * @param payload the control payload bytes
     * @param length  the number of valid bytes in the payload array
     * @return the encoded frame
     * @throws IllegalArgumentException if the length exceeds the control
     *         payload maximum
     */
    public static WebSocketEncodedFrame encodeControlFrame(byte opcode, byte[] payload, int length) {
        if (length < 0 || length > WebSocketFrameConstants.CONTROL_PAYLOAD_MAX_LENGTH) {
            throw new IllegalArgumentException("Invalid control payload length: " + length);
        }

        var content = length == 0
                ? EMPTY_PAYLOAD
                : ByteBuffer.wrap(Arrays.copyOf(payload, length));
        return encodeFrame(opcode, content);
    }

    /**
     * Encodes a WebSocket frame with the given opcode and payload.
     *
     * @param opcode  the frame opcode
     * @param payload the payload to encode
     * @return the encoded frame with header and masked payload
     */
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

        if (!payload.isReadOnly()) {
            var start = payload.position();
            for (var i = 0; i < payloadLength; i++) {
                var idx = start + i;
                var value = (byte) (payload.get(idx) ^ WebSocketFrameConstants.maskByte(maskKey, i));
                payload.put(idx, value);
            }
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

    private static ByteBuffer maskPayloadSegment(ByteBuffer payload, int maskKey, int maskOffset) {
        var payloadLength = payload.remaining();
        if (!payload.isReadOnly() && payload.hasArray()) {
            var array = payload.array();
            var start = payload.arrayOffset() + payload.position();
            applyMask(array, start, payloadLength, maskKey, maskOffset);
            return payload;
        }

        if (!payload.isReadOnly()) {
            var start = payload.position();
            for (var i = 0; i < payloadLength; i++) {
                var idx = start + i;
                var value = (byte) (payload.get(idx) ^ WebSocketFrameConstants.maskByte(maskKey, maskOffset + i));
                payload.put(idx, value);
            }
            return payload;
        }

        var masked = ByteBuffer.allocate(payloadLength);
        var source = payload.duplicate();
        for (var i = 0; i < payloadLength; i++) {
            var value = (byte) (source.get() ^ WebSocketFrameConstants.maskByte(maskKey, maskOffset + i));
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
