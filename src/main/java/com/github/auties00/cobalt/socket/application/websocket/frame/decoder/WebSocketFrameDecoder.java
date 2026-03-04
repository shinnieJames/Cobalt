package com.github.auties00.cobalt.socket.application.websocket.frame.decoder;

import com.github.auties00.cobalt.socket.application.websocket.frame.WebSocketFrameConstants;

import java.nio.ByteBuffer;

/**
 * A stateful decoder that parses WebSocket frames from a byte stream.
 *
 * <p>The decoder maintains internal state across calls to
 * {@link #decode(ByteBuffer)}, allowing frames to be decoded incrementally
 * as data arrives from the network.
 */
public final class WebSocketFrameDecoder {
    private static final int READ_HEADER_FIRST = 0;
    private static final int READ_HEADER_SECOND = 1;
    private static final int READ_EXTENDED_LENGTH = 2;
    private static final int READ_MASK = 3;
    private static final int READ_PAYLOAD = 4;

    private int readState = READ_HEADER_FIRST;
    private byte firstByte;
    private boolean finalFragment;
    private byte opcode;
    private boolean masked;
    private int extendedLengthBytesRemaining;
    private long payloadLength;
    private long payloadRemaining;
    private long extendedLengthValue;
    private int maskingKey;
    private int maskingKeyBytesRead;
    private int maskOffset;
    private int controlPayloadRead;

    private final byte[] controlPayload;
    private final ByteBuffer unmaskBuffer;

    /**
     * Creates a new WebSocket frame decoder.
     */
    public WebSocketFrameDecoder() {
        this.controlPayload = new byte[WebSocketFrameConstants.CONTROL_PAYLOAD_MAX_LENGTH];
        this.unmaskBuffer = ByteBuffer.allocate(WebSocketFrameConstants.UNMASK_CHUNK_SIZE);
    }

    /**
     * Decodes the next frame from the given source buffer.
     *
     * <p>Returns a {@link WebSocketDecodedFrame} indicating the result:
     * a complete data or control frame, no frame available yet, or an
     * invalid frame.
     *
     * @param source the buffer containing incoming bytes, in read mode
     * @return the decoded frame result
     */
    public WebSocketDecodedFrame decode(ByteBuffer source) {
        while (source.hasRemaining()) {
            switch (readState) {
                case READ_HEADER_FIRST -> {
                    firstByte = source.get();
                    readState = READ_HEADER_SECOND;
                }
                case READ_HEADER_SECOND -> {
                    var secondByte = source.get();
                    startFrame(firstByte, secondByte);
                    var payloadLength = secondByte & 0x7F;
                    if (payloadLength <= WebSocketFrameConstants.SMALL_PAYLOAD_LIMIT) {
                        this.payloadLength = payloadLength;
                        payloadRemaining = payloadLength;
                        if (!validateFrame()) {
                            return WebSocketDecodedFrame.invalid();
                        }
                        readState = masked ? READ_MASK : READ_PAYLOAD;
                    } else {
                        extendedLengthBytesRemaining = payloadLength == WebSocketFrameConstants.EXTENDED_16_PAYLOAD_MARKER ? 2 : 8;
                        readState = READ_EXTENDED_LENGTH;
                    }
                }
                case READ_EXTENDED_LENGTH -> {
                    while (source.hasRemaining() && extendedLengthBytesRemaining > 0) {
                        extendedLengthValue = (extendedLengthValue << 8) | (source.get() & 0xFFL);
                        extendedLengthBytesRemaining--;
                    }

                    if (extendedLengthBytesRemaining == 0) {
                        payloadLength = extendedLengthValue;
                        payloadRemaining = payloadLength;
                        if (!validateFrame()) {
                            return WebSocketDecodedFrame.invalid();
                        }
                        readState = masked ? READ_MASK : READ_PAYLOAD;
                    }
                }
                case READ_MASK -> {
                    while (source.hasRemaining() && maskingKeyBytesRead < Integer.BYTES) {
                        maskingKey = (maskingKey << 8) | (source.get() & 0xFF);
                        maskingKeyBytesRead++;
                    }

                    if (maskingKeyBytesRead == Integer.BYTES) {
                        readState = READ_PAYLOAD;
                    }
                }
                case READ_PAYLOAD -> {
                    var payloadResult = consumePayload(source);
                    if (!(payloadResult instanceof WebSocketDecodedFrame.None)) {
                        return payloadResult;
                    }

                    if (payloadRemaining == 0) {
                        var finishResult = finishFrame();
                        if (finishResult instanceof WebSocketDecodedFrame.Invalid) {
                            return finishResult;
                        }
                        resetFrame();
                        if (!(finishResult instanceof WebSocketDecodedFrame.None)) {
                            return finishResult;
                        }
                    }
                }
            }
        }
        return WebSocketDecodedFrame.none();
    }

    private void startFrame(byte firstByte, byte secondByte) {
        this.firstByte = firstByte;
        finalFragment = (firstByte & 0x80) != 0;
        opcode = (byte) (firstByte & 0x0F);
        masked = (secondByte & 0x80) != 0;
        extendedLengthBytesRemaining = 0;
        payloadLength = 0;
        payloadRemaining = 0;
        extendedLengthValue = 0;
        maskingKey = 0;
        maskingKeyBytesRead = 0;
        maskOffset = 0;
        controlPayloadRead = 0;
    }

    private boolean validateFrame() {
        if (payloadLength > WebSocketFrameConstants.MAX_FRAME_LENGTH) {
            return false;
        }

        return !isControlOpcode(opcode)
                || finalFragment && payloadLength <= WebSocketFrameConstants.CONTROL_PAYLOAD_MAX_LENGTH;
    }

    private WebSocketDecodedFrame consumePayload(ByteBuffer source) {
        var readable = (int) Math.min(source.remaining(), payloadRemaining);
        if (readable == 0) {
            return WebSocketDecodedFrame.none();
        }

        if (isDataOpcode(opcode)) {
            return consumeDataPayload(source, readable);
        }

        if (isControlOpcode(opcode)) {
            consumeControlPayload(source, readable);
            return WebSocketDecodedFrame.none();
        }

        if (opcode == WebSocketFrameConstants.OPCODE_TEXT) {
            source.position(source.position() + readable);
            if (masked) {
                maskOffset += readable;
            }
            payloadRemaining -= readable;
            return WebSocketDecodedFrame.none();
        }

        return WebSocketDecodedFrame.invalid();
    }

    private WebSocketDecodedFrame consumeDataPayload(ByteBuffer source, int readable) {
        if (!masked) {
            var payload = source.slice(source.position(), readable);
            source.position(source.position() + readable);
            payloadRemaining -= readable;
            return WebSocketDecodedFrame.data(payload);
        }

        var chunk = Math.min(readable, unmaskBuffer.capacity());
        unmaskBuffer.clear();
        for (var i = 0; i < chunk; i++) {
            var value = (byte) (source.get() ^ WebSocketFrameConstants.maskByte(maskingKey, maskOffset++));
            unmaskBuffer.put(value);
        }
        unmaskBuffer.flip();
        payloadRemaining -= chunk;

        return WebSocketDecodedFrame.data(unmaskBuffer);
    }

    private void consumeControlPayload(ByteBuffer source, int readable) {
        for (var i = 0; i < readable; i++) {
            var value = source.get();
            if (masked) {
                value = (byte) (value ^ WebSocketFrameConstants.maskByte(maskingKey, maskOffset++));
            }
            controlPayload[controlPayloadRead++] = value;
        }
        payloadRemaining -= readable;
    }

    private WebSocketDecodedFrame finishFrame() {
        return switch (opcode) {
            case WebSocketFrameConstants.OPCODE_BINARY,
                    WebSocketFrameConstants.OPCODE_CONTINUATION,
                    WebSocketFrameConstants.OPCODE_TEXT,
                    WebSocketFrameConstants.OPCODE_PONG -> WebSocketDecodedFrame.none();
            case WebSocketFrameConstants.OPCODE_PING,
                    WebSocketFrameConstants.OPCODE_CLOSE -> WebSocketDecodedFrame.control(opcode, controlPayload, controlPayloadRead);
            default -> WebSocketDecodedFrame.invalid();
        };
    }

    private void resetFrame() {
        readState = READ_HEADER_FIRST;
        firstByte = 0;
        finalFragment = false;
        opcode = 0;
        masked = false;
        extendedLengthBytesRemaining = 0;
        payloadLength = 0;
        payloadRemaining = 0;
        extendedLengthValue = 0;
        maskingKey = 0;
        maskingKeyBytesRead = 0;
        maskOffset = 0;
        controlPayloadRead = 0;
    }

    private static boolean isDataOpcode(byte opcode) {
        return opcode == WebSocketFrameConstants.OPCODE_BINARY
                || opcode == WebSocketFrameConstants.OPCODE_CONTINUATION;
    }

    private static boolean isControlOpcode(byte opcode) {
        return opcode == WebSocketFrameConstants.OPCODE_CLOSE
                || opcode == WebSocketFrameConstants.OPCODE_PING
                || opcode == WebSocketFrameConstants.OPCODE_PONG;
    }
}
