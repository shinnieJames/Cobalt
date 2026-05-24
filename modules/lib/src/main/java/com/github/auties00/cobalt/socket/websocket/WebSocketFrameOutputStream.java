package com.github.auties00.cobalt.socket.websocket;

import jdk.incubator.vector.ByteVector;
import jdk.incubator.vector.IntVector;
import jdk.incubator.vector.VectorOperators;
import jdk.incubator.vector.VectorSpecies;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.nio.ByteOrder;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;

import static com.github.auties00.cobalt.socket.websocket.WebSocketFrameConstants.*;

/**
 * Wraps an {@link OutputStream} and emits RFC 6455 binary frames with
 * client-to-server masking.
 *
 * <p>The stream supports two modes, selected automatically based on
 * whether the caller invokes {@link #beginFrame(int)} before
 * writing:
 *
 * <ul>
 *   <li><strong>Streaming mode</strong>: the caller declares the
 *       payload size up front via {@link #beginFrame(int)}, and the
 *       frame header is emitted immediately. Subsequent
 *       {@code write} calls are masked <strong>in place</strong> in
 *       the caller's array and forwarded straight to the underlying
 *       stream; no intermediate buffer. The frame ends implicitly
 *       once {@code payloadSize} bytes have been written.</li>
 *   <li><strong>One-shot mode</strong>: when {@code beginFrame} is
 *       not called, each individual
 *       {@link #write(byte[], int, int)} or {@link #write(int)} call
 *       is treated as one complete frame; the stream picks a fresh
 *       mask key, builds the header, masks the payload in place, and
 *       writes header plus payload as a single frame. Used for
 *       control frames (PONG, CLOSE) issued from the paired
 *       {@link WebSocketFrameInputStream}.</li>
 * </ul>
 *
 * <p>In both modes the mask is applied <strong>in place</strong> on
 * the caller's array using the JDK Vector API for bulk throughput
 * with an int-wise scalar fallback for short tails. The mutation
 * contract is intentional and load-bearing for zero-copy: the
 * caller's buffer must not be reused or read after the call returns.
 *
 * <p>All public state-mutating methods are serialized on {@code this}
 * so the input thread's auto-PONG (which goes through
 * {@link #writeControlFrame(byte, byte[], int)}) cannot race the
 * writer thread's streaming-mode frame.
 *
 * @implNote
 * This implementation reuses the mask SIMD strategy from the
 * pre-refactor {@code WebSocketFrameEncoder}: scalar alignment
 * lead-in, {@link ByteVector#lanewise(VectorOperators.Binary, byte)}
 * bulk, {@link VarHandle} int tail, byte tail. Below
 * {@link #VECTORIZE_THRESHOLD} the SIMD path is skipped so small
 * frames pay no vector-setup cost.
 */
public final class WebSocketFrameOutputStream extends FilterOutputStream {

    /**
     * The preferred hardware vector species used for SIMD bulk
     * masking.
     */
    private static final VectorSpecies<Byte> BYTE_SPECIES = ByteVector.SPECIES_PREFERRED;

    /**
     * The preferred hardware integer vector species used for
     * building the mask broadcast.
     */
    private static final VectorSpecies<Integer> INT_SPECIES = IntVector.SPECIES_PREFERRED;

    /**
     * The number of bytes processed per SIMD iteration.
     */
    private static final int VECTOR_LENGTH = BYTE_SPECIES.length();

    /**
     * The minimum number of mask-aligned bytes required before the
     * SIMD path is entered.
     *
     * @apiNote
     * Below this threshold the int-wise and byte-wise paths handle
     * the entire payload, avoiding vector-setup overhead on small
     * frames.
     */
    private static final int VECTORIZE_THRESHOLD = VECTOR_LENGTH * 2;

    /**
     * The {@link VarHandle} that reads and writes {@code int} values
     * from a {@code byte[]} in big-endian order, matching the
     * mask-byte layout produced by
     * {@link WebSocketFrameConstants#maskByte(int, int)}.
     */
    private static final VarHandle INT_HANDLE =
            MethodHandles.byteArrayViewVarHandle(int[].class, ByteOrder.BIG_ENDIAN);

    /**
     * The maximum frame header size: two base bytes plus eight
     * extended-length bytes plus four mask bytes.
     */
    private static final int MAX_HEADER_SIZE = 14;

    /**
     * The reusable scratch buffer for the frame header; private to
     * this instance and only touched under the {@code synchronized}
     * block around the actual write.
     */
    private final byte[] header = new byte[MAX_HEADER_SIZE];

    /**
     * The number of payload bytes still expected for the
     * streaming-mode frame currently in flight, or {@code 0} when no
     * streaming-mode frame is open (the stream is in one-shot mode).
     */
    private int streamingRemaining;

    /**
     * The mask key for the currently-open streaming-mode frame;
     * reset on every {@link #beginFrame(int)} call.
     */
    private int streamingMaskKey;

    /**
     * The mask-cycle offset for the currently-open streaming-mode
     * frame; advanced as payload bytes are masked across multiple
     * {@code write} calls inside the same frame.
     */
    private int streamingMaskOffset;

    /**
     * The reusable scratch buffer for a control frame deferred while
     * a streaming-mode frame is in flight; lazily allocated on first
     * use and sized at the RFC 6455 control-payload maximum so it
     * never needs to grow.
     */
    private byte[] pendingControlPayload;

    /**
     * The opcode of the deferred control frame
     * ({@link WebSocketFrameConstants#OPCODE_PONG} or
     * {@link WebSocketFrameConstants#OPCODE_CLOSE}), or {@code 0}
     * when no control frame is pending.
     */
    private byte pendingControlOpcode;

    /**
     * The number of valid bytes in {@link #pendingControlPayload}.
     */
    private int pendingControlLength;

    /**
     * Wraps an output stream that the caller has already opened.
     *
     * @apiNote
     * Constructed by
     * {@link com.github.auties00.cobalt.socket.WhatsAppSocketClient}
     * directly above the TLS-encrypted byte stream; the WebSocket
     * frame layer sits between WhatsApp's datagram stream and the
     * TLS socket.
     *
     * @param out the underlying output stream
     * @throws NullPointerException if {@code out} is {@code null}
     */
    public WebSocketFrameOutputStream(OutputStream out) {
        super(Objects.requireNonNull(out, "out"));
    }

    /**
     * Begins one WebSocket binary frame whose payload will be the next
     * {@code payloadSize} bytes written to this stream.
     *
     * @apiNote
     * Call this exactly once before the payload's first {@code write}
     * of each frame, then write exactly {@code payloadSize} bytes,
     * then call {@link #flush()} to push the bytes downstream. The
     * companion datagram stream (
     * {@link com.github.auties00.cobalt.socket.datagram.WhatsAppDatagramOutputStream})
     * calls this directly when its downstream is this class so the
     * frame header is emitted before the first byte of ciphertext.
     *
     * @implNote
     * This implementation generates a fresh mask key, builds the
     * frame header and forwards it to the underlying stream
     * immediately, then transitions to streaming mode. Subsequent
     * {@link #write(byte[], int, int)} and {@link #write(int)} calls
     * mask {@code payloadSize} bytes in place and forward them
     * straight through; the frame ends implicitly once
     * {@code payloadSize} bytes have been written.
     *
     * @param payloadSize the exact number of body bytes the caller is
     *                    about to write; must be non-negative
     * @throws IOException              if writing the frame header
     *                                  fails
     * @throws IllegalArgumentException if {@code payloadSize} is
     *                                  negative
     * @throws IllegalStateException    if a streaming-mode frame is
     *                                  already in flight
     */
    public synchronized void beginFrame(int payloadSize) throws IOException {
        if (payloadSize < 0) {
            throw new IllegalArgumentException("payloadSize must be non-negative: " + payloadSize);
        }
        if (streamingRemaining != 0) {
            throw new IllegalStateException("Cannot begin a new message while a streaming frame "
                    + "is already in flight (" + streamingRemaining + " bytes remaining)");
        }
        var maskKey = ThreadLocalRandom.current().nextInt();
        var headerLen = buildHeader(OPCODE_BINARY, payloadSize, maskKey);
        out.write(header, 0, headerLen);
        streamingRemaining = payloadSize;
        streamingMaskKey = maskKey;
        streamingMaskOffset = 0;
    }

    /**
     * {@inheritDoc}
     *
     * @implNote
     * This implementation masks the byte using the current frame's
     * key and offset and forwards it directly in streaming mode (the
     * streaming frame is one byte closer to completion); in one-shot
     * mode the byte becomes a one-byte binary frame on its own. The
     * streaming-mode branch then drains any deferred control frame
     * if the current frame just ended.
     */
    @Override
    public synchronized void write(int b) throws IOException {
        if (streamingRemaining > 0) {
            var masked = b ^ (maskByte(streamingMaskKey, streamingMaskOffset) & 0xFF);
            out.write(masked & 0xFF);
            streamingMaskOffset++;
            streamingRemaining--;
            if (streamingRemaining == 0) {
                drainPendingControlFrame();
            }
        } else {
            write(new byte[]{(byte) b}, 0, 1);
        }
    }

    /**
     * {@inheritDoc}
     *
     * @implNote
     * In streaming mode this implementation masks the bytes in place
     * against the current frame's key and offset, then forwards them
     * (the streaming frame's remaining-byte counter is decremented;
     * writing more bytes than declared by {@link #beginFrame(int)}
     * is rejected). In one-shot mode the call produces a complete
     * one-frame write (fresh mask key, header, payload) used for
     * control frames such as PONG and CLOSE. The {@code src} array
     * is masked in place before forwarding; the caller must not
     * reuse the supplied range after the call returns.
     */
    @Override
    public synchronized void write(byte[] src, int off, int len) throws IOException {
        if (streamingRemaining > 0) {
            if (len > streamingRemaining) {
                throw new IOException("Streaming frame overflow: tried to write " + len
                        + " bytes but only " + streamingRemaining + " remain");
            }
            if (len > 0) {
                applyMaskToArray(src, off, len, streamingMaskKey, streamingMaskOffset);
                out.write(src, off, len);
                streamingMaskOffset += len;
                streamingRemaining -= len;
            }
            if (streamingRemaining == 0) {
                drainPendingControlFrame();
            }
            return;
        }
        writeFrame(OPCODE_BINARY, src, off, len);
    }

    /**
     * Writes a control frame (ping, pong, or close) carrying the
     * supplied payload bytes.
     *
     * @apiNote
     * Used by {@link WebSocketFrameInputStream} when it auto-responds
     * to a peer-initiated PING with a matching PONG, or sends a
     * CLOSE on shutdown. The payload is masked in place inside
     * {@code payload}; the input stream's small control-frame
     * scratch buffer is the only caller and the mutation is safe.
     * When a streaming-mode binary frame is in flight the control
     * frame is deferred until the frame completes so the wire stream
     * is not corrupted.
     *
     * @param opcode  the control opcode
     *                ({@link WebSocketFrameConstants#OPCODE_PING},
     *                {@link WebSocketFrameConstants#OPCODE_PONG} or
     *                {@link WebSocketFrameConstants#OPCODE_CLOSE})
     * @param payload the control payload bytes
     * @param length  the number of valid bytes in {@code payload},
     *                at most
     *                {@value WebSocketFrameConstants#CONTROL_PAYLOAD_MAX_LENGTH}
     * @throws IOException              if writing fails
     * @throws IllegalArgumentException if {@code length} is negative
     *                                  or exceeds the control
     *                                  payload maximum
     */
    public synchronized void writeControlFrame(byte opcode, byte[] payload, int length) throws IOException {
        if (length < 0 || length > CONTROL_PAYLOAD_MAX_LENGTH) {
            throw new IllegalArgumentException("Invalid control payload length: " + length);
        }
        if (streamingRemaining > 0) {
            if (pendingControlPayload == null) {
                pendingControlPayload = new byte[CONTROL_PAYLOAD_MAX_LENGTH];
            }
            if (length > 0) {
                System.arraycopy(payload, 0, pendingControlPayload, 0, length);
            }
            pendingControlOpcode = opcode;
            pendingControlLength = length;
            return;
        }
        writeFrame(opcode, payload, 0, length);
    }

    /**
     * Emits any control frame that was deferred during a
     * streaming-mode write.
     *
     * @apiNote
     * Called by the streaming-mode write paths once
     * {@link #streamingRemaining} reaches zero, under the same
     * {@code synchronized} block that mutated the streaming counter.
     *
     * @throws IOException if the deferred frame's emit fails
     */
    private void drainPendingControlFrame() throws IOException {
        if (pendingControlOpcode == 0) {
            return;
        }
        var opcode = pendingControlOpcode;
        var length = pendingControlLength;
        pendingControlOpcode = 0;
        pendingControlLength = 0;
        writeFrame(opcode, pendingControlPayload, 0, length);
    }

    /**
     * {@inheritDoc}
     *
     * @implNote
     * This implementation forwards the flush to the underlying
     * stream under the same monitor as
     * {@link #write(byte[], int, int)} and
     * {@link #writeControlFrame(byte, byte[], int)} so the input
     * thread's auto-PONG cannot race with the writer thread's flush.
     */
    @Override
    public synchronized void flush() throws IOException {
        out.flush();
    }

    /**
     * Builds the frame header, masks the payload in place, and
     * writes the header and payload to the underlying stream under a
     * single synchronized block so concurrent senders are
     * serialized.
     *
     * @param opcode the frame opcode
     * @param src    the payload byte array (mutated in place)
     * @param off    the offset within {@code src}
     * @param len    the number of payload bytes
     * @throws IOException if writing fails
     */
    private synchronized void writeFrame(byte opcode, byte[] src, int off, int len) throws IOException {
        var maskKey = ThreadLocalRandom.current().nextInt();
        var headerLen = buildHeader(opcode, len, maskKey);
        if (len > 0) {
            applyMaskToArray(src, off, len, maskKey, 0);
        }
        out.write(header, 0, headerLen);
        if (len > 0) {
            out.write(src, off, len);
        }
        out.flush();
    }

    /**
     * Builds the frame header into {@link #header} and returns its
     * total length in bytes.
     *
     * @apiNote
     * Encodes the FIN bit, the opcode, the masked payload length
     * (with optional 16-bit or 64-bit extension) and the four-byte
     * mask key, per RFC 6455 section 5.2.
     *
     * @param opcode        the frame opcode
     * @param payloadLength the payload length in bytes
     * @param maskKey       the four-byte masking key
     * @return the number of bytes written to {@link #header}
     */
    private int buildHeader(byte opcode, int payloadLength, int maskKey) {
        header[0] = (byte) (0x80 | (opcode & 0x0F));
        int pos;
        if (payloadLength <= SMALL_PAYLOAD_LIMIT) {
            header[1] = (byte) (0x80 | payloadLength);
            pos = 2;
        } else if (payloadLength <= 0xFFFF) {
            header[1] = (byte) (0x80 | EXTENDED_16_PAYLOAD_MARKER);
            header[2] = (byte) (payloadLength >>> 8);
            header[3] = (byte) payloadLength;
            pos = 4;
        } else {
            header[1] = (byte) (0x80 | EXTENDED_64_PAYLOAD_MARKER);
            // Big-endian 8-byte length; the high four bytes are
            // always zero because payloadLength is an int but they
            // are written for protocol conformance.
            header[2] = 0;
            header[3] = 0;
            header[4] = 0;
            header[5] = 0;
            header[6] = (byte) (payloadLength >>> 24);
            header[7] = (byte) (payloadLength >>> 16);
            header[8] = (byte) (payloadLength >>> 8);
            header[9] = (byte) payloadLength;
            pos = 10;
        }
        header[pos] = (byte) (maskKey >>> 24);
        header[pos + 1] = (byte) (maskKey >>> 16);
        header[pos + 2] = (byte) (maskKey >>> 8);
        header[pos + 3] = (byte) maskKey;
        return pos + 4;
    }

    /**
     * Applies the WebSocket XOR mask to a region of a {@code byte[]}
     * using a three-tier strategy: scalar lead-in to align the mask
     * offset to a four-byte boundary, SIMD bulk XOR via
     * {@link ByteVector}, int-wise tail via {@link VarHandle} and a
     * final byte-wise sub-int tail.
     *
     * @param array      the byte array to mask in place
     * @param offset     the index of the first byte to mask
     * @param length     the number of bytes to mask
     * @param maskKey    the four-byte masking key
     * @param maskOffset the starting position in the four-byte mask
     *                   cycle (always {@code 0} for the encoder
     *                   since each frame uses a fresh key)
     */
    private static void applyMaskToArray(byte[] array, int offset, int length, int maskKey, int maskOffset) {
        var i = 0;

        var align = maskOffset & 3;
        if (align != 0) {
            var leading = Math.min(4 - align, length);
            for (; i < leading; i++) {
                array[offset + i] ^= maskByte(maskKey, maskOffset + i);
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
            array[offset + i] ^= maskByte(maskKey, maskOffset + i);
        }
    }

    /**
     * Builds a SIMD vector containing the four-byte mask pattern
     * repeated to fill every lane.
     *
     * @implNote
     * RFC 6455 masks use {@code maskKey[0]} as the highest-order
     * byte (big-endian memory layout).
     * {@link IntVector#reinterpretAsBytes()} always uses the
     * platform's native byte order, so on little-endian hosts the
     * int is reversed before broadcasting so the reinterpreted bytes
     * end up in the RFC 6455 order.
     *
     * @param maskKey the four-byte masking key
     * @return a {@link ByteVector} filled with the repeating mask
     *         pattern
     */
    private static ByteVector buildAlignedMaskVector(int maskKey) {
        var nativeKey = ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN
                ? Integer.reverseBytes(maskKey)
                : maskKey;
        return IntVector.broadcast(INT_SPECIES, nativeKey)
                .reinterpretAsBytes();
    }
}
