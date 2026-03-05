package com.github.auties00.cobalt.socket.layer.application.websocket.frame.encoder;

import com.github.auties00.cobalt.socket.layer.application.websocket.frame.WebSocketFrameConstants;
import jdk.incubator.vector.ByteVector;
import jdk.incubator.vector.VectorOperators;
import jdk.incubator.vector.VectorSpecies;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Encodes WebSocket frames according to
 * <a href="https://datatracker.ietf.org/doc/html/rfc6455">RFC 6455</a>.
 *
 * <p>All frames produced by this encoder are masked with a randomly
 * generated four-byte key, as required for client-to-server WebSocket
 * communication.
 *
 * <p>Masking uses the Vector API (SIMD) for bulk throughput with an
 * int-wise scalar fallback for short tails.  For payloads below
 * {@code VECTORIZE_THRESHOLD} the SIMD path is skipped entirely to
 * avoid vector-setup overhead on small frames.
 *
 * <p>Three buffer types are supported transparently: writable
 * array-backed (heap) buffers are masked in-place via the backing
 * array, writable direct buffers are masked in-place via
 * {@link MemorySegment}, and read-only buffers are copied into a new
 * heap array before masking.
 *
 * <p>This is a stateless utility class.  All methods are static and
 * thread-safe.
 */
public final class WebSocketFrameEncoder {

    /**
     * A shared empty payload buffer returned for zero-length frames.
     */
    private static final ByteBuffer EMPTY_PAYLOAD = ByteBuffer.allocate(0);

    /**
     * A shared empty result.
     */
    private static final ByteBuffer[] EMPTY_RESULT = new ByteBuffer[0];

    /**
     * The preferred hardware vector species used for SIMD bulk masking.
     */
    private static final VectorSpecies<Byte> BYTE_SPECIES = ByteVector.SPECIES_PREFERRED;

    /**
     * The number of bytes processed per SIMD iteration, equal to the
     * lane count of {@link #BYTE_SPECIES}.  Always a multiple of 4.
     */
    private static final int VECTOR_LENGTH = BYTE_SPECIES.length();

    /**
     * Minimum number of mask-aligned bytes required before the SIMD path
     * is entered.  Below this threshold the int-wise and byte-wise paths
     * handle the entire payload, avoiding vector-setup overhead on small
     * frames.
     */
    private static final int VECTORIZE_THRESHOLD = VECTOR_LENGTH * 2;

    /**
     * A {@link VarHandle} that reads and writes {@code int} values from a
     * {@code byte[]} in big-endian order, matching the mask-byte layout
     * produced by {@link WebSocketFrameConstants#maskByte(int, int)}.
     */
    private static final VarHandle INT_HANDLE =
            MethodHandles.byteArrayViewVarHandle(int[].class, ByteOrder.BIG_ENDIAN);

    /**
     * Prevents instantiation of this utility class.
     *
     * @throws UnsupportedOperationException always
     */
    private WebSocketFrameEncoder() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated.");
    }

    /**
     * Encodes a single binary WebSocket message from one or more payload
     * buffers.
     *
     * <p>The returned array is ordered as {@code [header, payload...]}
     * and can be sent directly with gather-write APIs such as
     * {@link java.nio.channels.GatheringByteChannel#write(ByteBuffer[])}.
     * All payload buffers are masked as one logical message, so the mask
     * offset continues across buffer boundaries.
     *
     * <p>If every buffer in {@code payloads} is {@code null} or empty and
     * at least one non-{@code null} buffer was present, the returned
     * array contains a single header-only frame.  If {@code payloads}
     * itself is {@code null} or zero-length, an empty array is returned.
     *
     * @param payloads the payload buffers in message order; individual
     *        entries may be {@code null} or empty and will be skipped
     * @return frame buffers ready to send, or an empty array if there
     *         is nothing to encode
     */
    public static ByteBuffer[] encodeBinaryMessage(ByteBuffer... payloads) {
        if (payloads == null || payloads.length == 0) {
            return new ByteBuffer[0];
        }

        var sawPayloadArgument = false;
        var segmentCount = 0;
        var payloadLength = 0;
        for (var payload : payloads) {
            if (payload == null) {
                continue;
            }
            sawPayloadArgument = true;
            if (!payload.hasRemaining()) {
                continue;
            }
            payloadLength = Math.addExact(payloadLength, payload.remaining());
            segmentCount++;
        }

        if (payloadLength == 0) {
            if (!sawPayloadArgument) {
                return EMPTY_RESULT;
            }
            var frame = encodeFrame(WebSocketFrameConstants.OPCODE_BINARY, EMPTY_PAYLOAD);
            return new ByteBuffer[]{frame.header()};
        }

        var maskKey = ThreadLocalRandom.current().nextInt();
        var header = buildHeader(WebSocketFrameConstants.OPCODE_BINARY, payloadLength, maskKey);

        var result = new ByteBuffer[segmentCount + 1];
        result[0] = header;

        var maskOffset = 0;
        var resultIndex = 1;
        for (var payload : payloads) {
            if (payload == null || !payload.hasRemaining()) {
                continue;
            }
            var remaining = payload.remaining();
            result[resultIndex++] = applyMaskToBuffer(payload.duplicate(), maskKey, maskOffset);
            maskOffset += remaining;
        }

        return result;
    }

    /**
     * Encodes a control frame (ping, pong, or close).
     *
     * <p>The first {@code length} bytes of {@code payload} are
     * defensively copied before masking, so the caller's array is never
     * modified.
     *
     * @param opcode  the control opcode
     *        ({@link WebSocketFrameConstants#OPCODE_PING},
     *        {@link WebSocketFrameConstants#OPCODE_PONG}, or
     *        {@link WebSocketFrameConstants#OPCODE_CLOSE})
     * @param payload the control payload bytes
     * @param length  the number of valid bytes in {@code payload},
     *        from 0 to
     *        {@value WebSocketFrameConstants#CONTROL_PAYLOAD_MAX_LENGTH}
     *        inclusive
     * @return the encoded frame containing a header and a masked payload
     * @throws IllegalArgumentException if {@code length} is negative or
     *         exceeds the control payload maximum
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
     * <p>A duplicate of {@code payload} is used for masking, so the
     * caller's buffer position and limit are not modified.  The backing
     * data may be modified in place if the buffer is writable (see
     * {@link #applyMaskToBuffer(ByteBuffer, int, int)} for details).
     *
     * @param opcode  the frame opcode
     * @param payload the payload to encode
     * @return the encoded frame containing a header and a masked payload
     */
    public static WebSocketEncodedFrame encodeFrame(byte opcode, ByteBuffer payload) {
        var payloadView = payload.duplicate();
        var payloadLength = payloadView.remaining();
        var maskKey = ThreadLocalRandom.current().nextInt();

        var header = buildHeader(opcode, payloadLength, maskKey);

        if (payloadLength == 0) {
            return new WebSocketEncodedFrame(header, EMPTY_PAYLOAD);
        }

        var maskedPayload = applyMaskToBuffer(payloadView, maskKey, 0);
        return new WebSocketEncodedFrame(header, maskedPayload);
    }

    /**
     * Builds a complete frame header including the FIN bit, opcode,
     * masked-payload-length encoding, and four-byte mask key.
     *
     * <p>The returned buffer is flipped and ready to read.
     *
     * @param opcode        the frame opcode
     * @param payloadLength the total payload length in bytes
     * @param maskKey       the four-byte masking key
     * @return a heap {@link ByteBuffer} containing the encoded header
     */
    private static ByteBuffer buildHeader(byte opcode, int payloadLength, int maskKey) {
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
        return header;
    }

    /**
     * Returns the number of bytes required for a frame header with the
     * given payload length: 2 bytes for the base header, 0 / 2 / 8
     * bytes for the extended length, and 4 bytes for the mask key.
     *
     * @param payloadLength the total payload length in bytes
     * @return the header size in bytes
     */
    private static int headerLength(int payloadLength) {
        if (payloadLength <= WebSocketFrameConstants.SMALL_PAYLOAD_LIMIT) {
            return 2 + Integer.BYTES;
        }
        if (payloadLength <= 0xFFFF) {
            return 2 + Short.BYTES + Integer.BYTES;
        }
        return 2 + Long.BYTES + Integer.BYTES;
    }

    /**
     * Applies the WebSocket mask to a buffer, choosing the fastest path
     * based on the buffer type.
     *
     * <p>For writable array-backed buffers the mask is applied
     * <b>in-place</b> and the original buffer is returned.  For writable
     * direct buffers the mask is applied in-place via
     * {@link MemorySegment#ofBuffer(Buffer)}.  For read-only buffers a
     * new heap buffer is allocated, the data is copied, and the copy is
     * masked.
     *
     * @param payload    the buffer to mask
     * @param maskKey    the four-byte masking key
     * @param maskOffset the starting position in the four-byte mask cycle
     * @return the masked buffer (may be {@code payload} itself)
     */
    private static ByteBuffer applyMaskToBuffer(ByteBuffer payload, int maskKey, int maskOffset) {
        var length = payload.remaining();
        if (length == 0) {
            return EMPTY_PAYLOAD;
        }

        if (!payload.isReadOnly() && payload.hasArray()) {
            var array = payload.array();
            var start = payload.arrayOffset() + payload.position();
            applyMaskToArray(array, start, length, maskKey, maskOffset);
            return payload;
        }

        if (!payload.isReadOnly()) {
            applyMaskViaSegment(MemorySegment.ofBuffer(payload), payload.position(), length, maskKey, maskOffset);
            return payload;
        }

        var masked = new byte[length];
        payload.duplicate().get(masked);
        applyMaskToArray(masked, 0, length, maskKey, maskOffset);
        return ByteBuffer.wrap(masked);
    }

    /**
     * Applies the WebSocket XOR mask to a region of a {@code byte[]}
     * using a three-tier strategy.
     *
     * <p>First, a scalar lead-in aligns the mask offset to a four-byte
     * boundary.  Then, if the aligned remainder is at least
     * {@link #VECTORIZE_THRESHOLD} bytes, a SIMD bulk pass XORs one
     * {@link ByteVector} width at a time.  An int-wise pass via
     * {@link VarHandle} handles the gap between the last full vector and
     * the final sub-int tail, which is finished byte-by-byte.
     *
     * @param array      the byte array to mask in place
     * @param offset     the index of the first byte to mask
     * @param length     the number of bytes to mask
     * @param intMask    the four-byte masking key
     * @param maskOffset the starting position in the four-byte mask cycle
     */
    private static void applyMaskToArray(byte[] array, int offset, int length, int intMask, int maskOffset) {
        var i = 0;

        var align = maskOffset & 3;
        if (align != 0) {
            var leading = Math.min(4 - align, length);
            for (; i < leading; i++) {
                array[offset + i] ^= WebSocketFrameConstants.maskByte(intMask, maskOffset + i);
            }
        }

        var remaining = length - i;

        if (remaining >= VECTORIZE_THRESHOLD) {
            var maskVec = buildAlignedMaskVector(intMask);
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
                INT_HANDLE.set(array, idx, val ^ intMask);
            }
        }

        for (; i < length; i++) {
            array[offset + i] ^= WebSocketFrameConstants.maskByte(intMask, maskOffset + i);
        }
    }

    /**
     * Applies the WebSocket XOR mask to a region of a
     * {@link MemorySegment} using the same three-tier strategy as
     * {@link #applyMaskToArray(byte[], int, int, int, int)}.
     *
     * <p>This path is used for writable direct {@link ByteBuffer}s,
     * where the backing memory is not accessible as a {@code byte[]}.
     * The SIMD pass uses
     * {@link ByteVector#fromMemorySegment(VectorSpecies, MemorySegment, long, ByteOrder)}
     * in native byte order, and the int-wise pass uses
     * {@link ValueLayout#JAVA_INT_UNALIGNED} to avoid alignment
     * constraints.
     *
     * @param segment    the memory segment to mask in place
     * @param position   the byte offset of the first byte to mask
     * @param length     the number of bytes to mask
     * @param maskKey    the four-byte masking key
     * @param maskOffset the starting position in the four-byte mask cycle
     */
    private static void applyMaskViaSegment(MemorySegment segment, int position, int length, int maskKey, int maskOffset) {
        var i = 0;

        var align = maskOffset & 3;
        if (align != 0) {
            var leading = Math.min(4 - align, length);
            for (; i < leading; i++) {
                long idx = position + i;
                byte b = segment.get(ValueLayout.JAVA_BYTE, idx);
                segment.set(ValueLayout.JAVA_BYTE, idx,
                        (byte) (b ^ WebSocketFrameConstants.maskByte(maskKey, maskOffset + i)));
            }
        }

        var remaining = length - i;

        if (remaining >= VECTORIZE_THRESHOLD) {
            var maskVec = buildAlignedMaskVector(maskKey);
            var vectorBound = i + BYTE_SPECIES.loopBound(remaining);
            for (; i < vectorBound; i += VECTOR_LENGTH) {
                long idx = position + i;
                var data = ByteVector.fromMemorySegment(BYTE_SPECIES, segment, idx, ByteOrder.nativeOrder());
                data.lanewise(VectorOperators.XOR, maskVec)
                        .intoMemorySegment(segment, idx, ByteOrder.nativeOrder());
            }
        }

        if (length - i >= 4) {
            int intMask;
            if ((ByteOrder.nativeOrder() == ByteOrder.BIG_ENDIAN)) {
                intMask = maskKey;
            } else {
                intMask = Integer.reverseBytes(maskKey);
            }
            for (; i + 3 < length; i += 4) {
                long idx = position + i;
                var val = segment.get(ValueLayout.JAVA_INT_UNALIGNED, idx);
                segment.set(ValueLayout.JAVA_INT_UNALIGNED, idx, val ^ intMask);
            }
        }

        for (; i < length; i++) {
            long idx = position + i;
            byte b = segment.get(ValueLayout.JAVA_BYTE, idx);
            segment.set(ValueLayout.JAVA_BYTE, idx,
                    (byte) (b ^ WebSocketFrameConstants.maskByte(maskKey, maskOffset + i)));
        }
    }

    /**
     * Builds a SIMD vector containing the four-byte mask pattern repeated
     * to fill every lane.
     *
     * <p>The caller must have aligned the mask offset to a four-byte
     * boundary so that byte 0 of the pattern maps to lane 0.
     *
     * @param maskKey the four-byte masking key
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