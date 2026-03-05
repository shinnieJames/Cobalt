package com.github.auties00.cobalt.socket.layer.application.websocket.frame.decoder;

import com.github.auties00.cobalt.socket.layer.application.websocket.frame.WebSocketFrameConstants;
import jdk.incubator.vector.ByteVector;
import jdk.incubator.vector.VectorOperators;
import jdk.incubator.vector.VectorSpecies;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * A stateful decoder that parses WebSocket frames from a byte stream
 * according to <a href="https://datatracker.ietf.org/doc/html/rfc6455">RFC 6455</a>.
 *
 * <p>The decoder maintains internal state across calls to
 * {@link #decode(ByteBuffer)}, allowing frames to be decoded incrementally
 * as data arrives from the network.  Each call consumes as many bytes as
 * possible from the source buffer and returns the first actionable result:
 * a data chunk, a complete control frame, an indication that more bytes are
 * needed, or a protocol error.
 *
 * <p>Data payloads are delivered as {@code byte[]} slices via
 * {@link WebSocketDecodedFrame#data(ByteBuffer)}.  The backing array
 * is owned by this decoder and is reused across calls, so callers must
 * consume or copy the contents before the next invocation of
 * {@code decode}.
 *
 * <p>Unmasking uses the Vector API (SIMD) for bulk throughput with an
 * int-wise scalar fallback for short tails, matching the strategy used by
 * {@code WebSocketFrameEncoder}.
 *
 * <p>Instances of this class are <b>not</b> thread-safe.  A single
 * decoder must be used by one reader thread at a time.
 */
public final class WebSocketFrameDecoder {
    private static final int READ_HEADER_FIRST = 0;
    private static final int READ_HEADER_SECOND = 1;
    private static final int READ_EXTENDED_LENGTH = 2;
    private static final int READ_MASK = 3;
    private static final int READ_PAYLOAD = 4;

    private static final VectorSpecies<Byte> BYTE_SPECIES = ByteVector.SPECIES_PREFERRED;
    private static final int VECTOR_LENGTH = BYTE_SPECIES.length();
    private static final int VECTORIZE_THRESHOLD = VECTOR_LENGTH * 2;

    /**
     * A {@link VarHandle} that reads and writes {@code int} values from a
     * {@code byte[]} in big-endian order, matching the mask-byte layout
     * produced by {@link WebSocketFrameConstants#maskByte(int, int)}.
     */
    private static final VarHandle INT_HANDLE =
            MethodHandles.byteArrayViewVarHandle(int[].class, ByteOrder.BIG_ENDIAN);

    /**
     * Current position in the read-state machine.
     */
    private int readState = READ_HEADER_FIRST;

    /**
     * First byte of the current frame header.
     */
    private byte firstByte;

    /**
     * Whether the FIN bit is set on the current frame.
     */
    private boolean finalFragment;

    /**
     * Opcode of the current frame.
     */
    private byte opcode;

    /**
     * Whether the MASK bit is set on the current frame.
     */
    private boolean masked;

    /**
     * Number of extended-length bytes still to be read.
     */
    private int extendedLengthBytesRemaining;

    /**
     * Total payload length declared by the frame header.
     */
    private long payloadLength;

    /**
     * Number of payload bytes still to be consumed.
     */
    private long payloadRemaining;

    /**
     * Accumulator for multi-byte extended-length values.
     */
    private long extendedLengthValue;

    /**
     * The four-byte masking key, assembled from the frame header.
     */
    private int maskingKey;

    /**
     * Number of masking-key bytes read so far (0–4).
     */
    private int maskingKeyBytesRead;

    /**
     * Running offset into the mask cycle.  Incremented as payload bytes
     * are consumed so the mask is applied continuously across
     * {@code decode} calls.
     */
    private int maskOffset;

    /**
     * Number of control-payload bytes accumulated so far.
     */
    private int controlPayloadRead;

    /**
     * Accumulator for control-frame payloads (ping, pong, close).
     * Control frames are limited to
     * {@value WebSocketFrameConstants#CONTROL_PAYLOAD_MAX_LENGTH} bytes.
     */
    private final byte[] controlPayload;

    /**
     * Reusable work buffer for data-payload chunks.  Its contents are
     * valid only between a {@link WebSocketDecodedFrame.Data} return and
     * the next call to {@link #decode(ByteBuffer)}.
     */
    private final byte[] chunkBuffer;

    /**
     * Creates a new WebSocket frame decoder with an internal chunk buffer
     * of {@link WebSocketFrameConstants#UNMASK_CHUNK_SIZE} bytes.
     */
    public WebSocketFrameDecoder() {
        this.controlPayload = new byte[WebSocketFrameConstants.CONTROL_PAYLOAD_MAX_LENGTH];
        this.chunkBuffer = new byte[WebSocketFrameConstants.UNMASK_CHUNK_SIZE];
    }

    /**
     * Decodes the next frame from the given source buffer.
     *
     * <p>Consumes as many bytes as possible from {@code source} and returns
     * the first actionable result.  The returned {@link WebSocketDecodedFrame}
     * is one of:
     * <ul>
     * <li>a {@link WebSocketDecodedFrame.Data} containing a chunk of
     *     unmasked payload bytes referencing this decoder's internal
     *     {@code chunkBuffer}
     * <li>a {@link WebSocketDecodedFrame.Control} containing a complete
     *     control frame (ping, pong, or close)
     * <li>a {@link WebSocketDecodedFrame.None} indicating that more bytes
     *     are needed before a result can be produced
     * <li>a {@link WebSocketDecodedFrame.Invalid} indicating a protocol
     *     error
     * </ul>
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

    /**
     * Initialises per-frame state from the first two header bytes.
     *
     * @param firstByte  the first byte of the frame header (FIN, RSV, opcode)
     * @param secondByte the second byte of the frame header (MASK, payload length)
     */
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

    /**
     * Validates the frame header after the full payload length is known.
     *
     * <p>A frame is invalid if its payload length exceeds
     * {@link WebSocketFrameConstants#MAX_FRAME_LENGTH}, or if it is a
     * control frame that is either fragmented or exceeds the control
     * payload limit.
     *
     * @return {@code true} if the frame header is valid
     */
    private boolean validateFrame() {
        if (payloadLength > WebSocketFrameConstants.MAX_FRAME_LENGTH) {
            return false;
        }

        return !isControlOpcode(opcode)
               || finalFragment && payloadLength <= WebSocketFrameConstants.CONTROL_PAYLOAD_MAX_LENGTH;
    }

    /**
     * Routes payload consumption to the appropriate handler based on the
     * current frame's opcode.
     *
     * @param source the buffer containing incoming bytes
     * @return a decoded frame result, or {@link WebSocketDecodedFrame#none()}
     *         if no complete result is available yet
     */
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

    /**
     * Consumes up to one chunk of data-frame payload from {@code source},
     * unmasking in bulk if the frame is masked.
     *
     * <p>The bytes are bulk-copied into {@link #chunkBuffer} via
     * {@link ByteBuffer#get(byte[], int, int)}, then — if the frame is
     * masked — a SIMD / int-wise XOR pass is applied over the same array.
     * This separates the copy from the XOR, allowing each to be
     * individually optimised by the JDK and the hardware.
     *
     * @param source   the buffer containing incoming bytes
     * @param readable the number of payload bytes available to read
     * @return a {@link WebSocketDecodedFrame.Data} referencing
     *         {@link #chunkBuffer}
     */
    private WebSocketDecodedFrame consumeDataPayload(ByteBuffer source, int readable) {
        var chunk = Math.min(readable, chunkBuffer.length);
        source.get(chunkBuffer, 0, chunk);
        if (masked) {
            applyMaskToArray(chunkBuffer, 0, chunk, maskingKey, maskOffset);
            maskOffset += chunk;
        }
        payloadRemaining -= chunk;
        return WebSocketDecodedFrame.data(ByteBuffer.wrap(chunkBuffer, 0, chunk));
    }

    /**
     * Accumulates control-frame payload bytes into
     * {@link #controlPayload}, unmasking in bulk if the frame is masked.
     *
     * @param source   the buffer containing incoming bytes
     * @param readable the number of payload bytes available to read
     */
    private void consumeControlPayload(ByteBuffer source, int readable) {
        source.get(controlPayload, controlPayloadRead, readable);
        if (masked) {
            applyMaskToArray(controlPayload, controlPayloadRead, readable, maskingKey, maskOffset);
            maskOffset += readable;
        }
        controlPayloadRead += readable;
        payloadRemaining -= readable;
    }

    /**
     * Produces the final result for the current frame once all payload
     * bytes have been consumed.
     *
     * <p>Data and continuation frames have already been delivered
     * chunk-by-chunk, so they yield {@link WebSocketDecodedFrame#none()}.
     * Ping and close frames yield a
     * {@link WebSocketDecodedFrame#control(byte, byte[], int)} containing
     * the accumulated control payload.
     *
     * @return the frame-completion result
     */
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

    /**
     * Resets all per-frame state in preparation for the next frame.
     */
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

    /**
     * Returns {@code true} if the opcode identifies a data frame
     * ({@link WebSocketFrameConstants#OPCODE_BINARY} or
     * {@link WebSocketFrameConstants#OPCODE_CONTINUATION}).
     *
     * @param opcode the frame opcode to test
     * @return {@code true} if the opcode is a data opcode
     */
    private static boolean isDataOpcode(byte opcode) {
        return opcode == WebSocketFrameConstants.OPCODE_BINARY
               || opcode == WebSocketFrameConstants.OPCODE_CONTINUATION;
    }

    /**
     * Returns {@code true} if the opcode identifies a control frame
     * ({@link WebSocketFrameConstants#OPCODE_CLOSE},
     * {@link WebSocketFrameConstants#OPCODE_PING}, or
     * {@link WebSocketFrameConstants#OPCODE_PONG}).
     *
     * @param opcode the frame opcode to test
     * @return {@code true} if the opcode is a control opcode
     */
    private static boolean isControlOpcode(byte opcode) {
        return opcode == WebSocketFrameConstants.OPCODE_CLOSE
               || opcode == WebSocketFrameConstants.OPCODE_PING
               || opcode == WebSocketFrameConstants.OPCODE_PONG;
    }

    /**
     * Applies the WebSocket XOR mask to a region of a {@code byte[]}
     * using a three-tier strategy.
     *
     * <p>First, a scalar lead-in aligns the mask offset to a four-byte
     * boundary.  Then, if the aligned remainder is at least
     * {@code VECTORIZE_THRESHOLD} bytes, a SIMD bulk pass XORs one
     * {@link ByteVector} width at a time.  An int-wise pass via
     * {@link VarHandle} handles the gap between the last full vector and
     * the final sub-int tail, which is finished byte-by-byte.
     *
     * @param array      the byte array to mask in place
     * @param offset     the index of the first byte to mask
     * @param length     the number of bytes to mask
     * @param maskKey    the four-byte masking key from the frame header
     * @param maskOffset the starting position in the four-byte mask cycle
     */
    private static void applyMaskToArray(byte[] array, int offset, int length, int maskKey, int maskOffset) {
        var i = 0;

        var align = maskOffset & 3;
        if (align != 0) {
            var leading = Math.min(4 - align, length);
            for (; i < leading; i++) {
                array[offset + i] ^= WebSocketFrameConstants.maskByte(maskKey, maskOffset + i);
            }
        }

        var remaining = length - i;

        if (remaining >= VECTORIZE_THRESHOLD) {
            var maskVec = buildAlignedMaskVector(maskKey);
            var vectorBound = i + BYTE_SPECIES.loopBound(remaining);
            for (; i < vectorBound; i += VECTOR_LENGTH) {
                var data = ByteVector.fromArray(BYTE_SPECIES, array, offset + i);
                data.lanewise(VectorOperators.XOR, maskVec)
                        .intoArray(array, offset + i);
            }
        }

        if (length - i >= 4) {
            for (; i + 3 < length; i += 4) {
                var idx = offset + i;
                var val = (int) INT_HANDLE.get(array, idx);
                INT_HANDLE.set(array, idx, val ^ maskKey);
            }
        }

        for (; i < length; i++) {
            array[offset + i] ^= WebSocketFrameConstants.maskByte(maskKey, maskOffset + i);
        }
    }

    /**
     * Builds a SIMD vector containing the four-byte mask pattern repeated
     * to fill every lane.
     *
     * <p>The caller must have aligned the mask offset to a four-byte
     * boundary so that byte 0 of the pattern maps to lane 0.
     *
     * @param maskKey the four-byte masking key from the frame header
     * @return a {@link ByteVector} filled with the repeating mask pattern
     */
    private static ByteVector buildAlignedMaskVector(int maskKey) {
        var mask = new byte[VECTOR_LENGTH];
        for (int i = 0; i < VECTOR_LENGTH; i += 4) {
            INT_HANDLE.set(mask, i, maskKey);
        }
        return ByteVector.fromArray(BYTE_SPECIES, mask, 0);
    }
}