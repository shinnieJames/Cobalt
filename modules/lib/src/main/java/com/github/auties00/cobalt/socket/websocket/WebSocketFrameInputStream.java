package com.github.auties00.cobalt.socket.websocket;

import jdk.incubator.vector.ByteVector;
import jdk.incubator.vector.IntVector;
import jdk.incubator.vector.VectorOperators;
import jdk.incubator.vector.VectorSpecies;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Objects;

import static com.github.auties00.cobalt.socket.websocket.WebSocketFrameConstants.*;

/**
 * Parses RFC 6455 frames from an underlying {@link InputStream} and
 * exposes the unmasked payload bytes as a continuous byte stream.
 *
 * <p>Frame payloads are read directly into the caller's {@code dst}
 * array and unmasked <strong>in place</strong> via SIMD; no
 * intermediate buffer or extra copy. Control frames (PING, CLOSE,
 * PONG) are handled inline: PING triggers a matching PONG through the
 * paired {@link WebSocketFrameOutputStream}, CLOSE marks the stream
 * as closed (subsequent reads return {@code -1}), PONG is ignored.
 *
 * <p>Leftover bytes from the WebSocket upgrade response (when the
 * server piggybacks the first frame on the same TCP segment) are
 * supplied at construction time and drained transparently before
 * touching the underlying stream.
 *
 * <p>Instances are <strong>not</strong> thread-safe: one decoder is
 * intended to be owned by a single reader thread. The paired output
 * stream is itself synchronized so the input thread's auto-PONG
 * cannot race the writer thread's data frame.
 *
 * @implNote
 * This implementation reuses the mask SIMD strategy from the
 * pre-refactor {@code WebSocketFrameDecoder}: scalar alignment
 * lead-in, {@link ByteVector#lanewise(VectorOperators.Binary, byte)}
 * bulk, {@link VarHandle} int tail, byte tail. Below
 * {@link #VECTORIZE_THRESHOLD} the SIMD path is skipped so small
 * payloads pay no vector-setup cost.
 */
public final class WebSocketFrameInputStream extends FilterInputStream {

    /**
     * The preferred hardware vector species used for SIMD bulk
     * unmasking.
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
     * The paired output stream used to send PONG replies to PING
     * frames and CLOSE acknowledgements.
     */
    private final WebSocketFrameOutputStream pairedOutput;

    /**
     * The reusable buffer for control-frame payloads (at most 125
     * bytes per RFC 6455 section 5.5).
     */
    private final byte[] controlPayload = new byte[CONTROL_PAYLOAD_MAX_LENGTH];

    /**
     * The reusable single-byte buffer used by {@link #read()} to
     * delegate to the bulk-read code path.
     */
    private final byte[] oneByteBuf = new byte[1];

    /**
     * The bytes carried over from the WebSocket upgrade response
     * (the first frame's bytes that arrived in the same TCP segment);
     * {@code null} once fully drained.
     */
    private ByteBuffer leftover;

    /**
     * The payload bytes still to be consumed from the current frame;
     * zero when no frame is in flight, in which case the next read
     * triggers a new header parse.
     */
    private long frameRemaining;

    /**
     * The opcode of the current frame, set by
     * {@link #parseFrameHeader()}.
     */
    private byte currentOpcode;

    /**
     * The four-byte masking key from the current frame's header.
     */
    private int maskKey;

    /**
     * The running offset into the mask cycle.
     *
     * @apiNote
     * Incremented as payload bytes are consumed so the mask is
     * applied continuously across multiple {@code read} calls inside
     * a single frame.
     */
    private int maskOffset;

    /**
     * Whether the current frame's MASK bit is set.
     *
     * @apiNote
     * WhatsApp's server never masks its frames, but the bit is
     * honoured for RFC conformance so a strict intermediary that
     * masks server-to-client frames would still decode correctly.
     */
    private boolean masked;

    /**
     * Whether a CLOSE frame has been received; once set, subsequent
     * reads return {@code -1}.
     */
    private boolean closed;

    /**
     * Wraps an underlying input stream with the WebSocket frame
     * parser.
     *
     * @apiNote
     * Constructed by
     * {@link com.github.auties00.cobalt.socket.WhatsAppSocketClient}
     * directly above the TLS-decrypted byte stream, with the
     * {@code leftover} buffer carrying any bytes the server
     * piggybacked on the upgrade response.
     *
     * @param in           the underlying stream of TLS-decrypted
     *                     bytes
     * @param leftover     any bytes already read past the upgrade
     *                     response's {@code CRLF CRLF}, or
     *                     {@code null} if the upgrade response was
     *                     consumed exactly
     * @param pairedOutput the paired output stream that PONG replies
     *                     and CLOSE acknowledgements are sent
     *                     through
     * @throws NullPointerException if {@code in} or
     *                              {@code pairedOutput} is
     *                              {@code null}
     */
    public WebSocketFrameInputStream(InputStream in, ByteBuffer leftover, WebSocketFrameOutputStream pairedOutput) {
        super(Objects.requireNonNull(in, "in"));
        this.leftover = leftover != null && leftover.hasRemaining() ? leftover : null;
        this.pairedOutput = Objects.requireNonNull(pairedOutput, "pairedOutput");
    }

    /**
     * {@inheritDoc}
     *
     * @implNote
     * This implementation delegates to {@link #read(byte[], int, int)}
     * through {@link #oneByteBuf} so the frame parser sees a single
     * code path.
     */
    @Override
    public int read() throws IOException {
        var n = read(oneByteBuf, 0, 1);
        return n < 0 ? -1 : oneByteBuf[0] & 0xFF;
    }

    /**
     * {@inheritDoc}
     *
     * @implNote
     * This implementation parses any pending frame header inline
     * (recursing into control-frame handling as needed) and then
     * reads payload bytes directly into {@code dst} with SIMD
     * unmasking applied in place; no intermediate copy. Returns
     * {@code -1} when the stream is closed or after a CLOSE frame is
     * received.
     */
    @Override
    public int read(byte[] dst, int off, int len) throws IOException {
        if (closed) {
            return -1;
        }
        if (len == 0) {
            return 0;
        }

        while (frameRemaining == 0) {
            if (!parseFrameHeader()) {
                return -1;
            }
            if (isControlOpcode()) {
                handleControlFrame();
                if (closed) {
                    return -1;
                }
            }
        }

        var toRead = (int) Math.min(frameRemaining, len);
        var n = readFromSource(dst, off, toRead);
        if (n < 0) {
            throw new IOException("Unexpected end of stream mid-frame");
        }
        if (masked) {
            applyMaskToArray(dst, off, n, maskKey, maskOffset);
            maskOffset += n;
        }
        frameRemaining -= n;
        return n;
    }

    /**
     * Reads bytes from {@link #leftover} (if any) before falling
     * through to the underlying stream.
     *
     * @param dst the destination byte array
     * @param off the offset within {@code dst}
     * @param len the maximum number of bytes to read
     * @return the number of bytes read, or {@code -1} on
     *         end-of-stream
     * @throws IOException if the underlying read fails
     */
    private int readFromSource(byte[] dst, int off, int len) throws IOException {
        if (leftover != null) {
            var n = Math.min(leftover.remaining(), len);
            leftover.get(dst, off, n);
            if (!leftover.hasRemaining()) {
                leftover = null;
            }
            return n;
        }
        return in.read(dst, off, len);
    }

    /**
     * Reads exactly one byte from {@link #leftover} or the
     * underlying stream, used by the header parser.
     *
     * @return the byte in {@code 0..255}
     * @throws IOException on end-of-stream or read failure
     */
    private int readByte() throws IOException {
        if (leftover != null) {
            var b = leftover.get() & 0xFF;
            if (!leftover.hasRemaining()) {
                leftover = null;
            }
            return b;
        }
        var b = in.read();
        if (b < 0) {
            throw new IOException("Unexpected end of stream while reading WebSocket frame header");
        }
        return b;
    }

    /**
     * Reads exactly {@code count} bytes from {@link #leftover} or
     * the underlying stream into {@code dst} at {@code off}.
     *
     * @param dst   the destination byte array
     * @param off   the offset within {@code dst}
     * @param count the exact number of bytes to read
     * @throws IOException on end-of-stream or read failure
     */
    private void readFully(byte[] dst, int off, int count) throws IOException {
        var pos = 0;
        while (pos < count) {
            var n = readFromSource(dst, off + pos, count - pos);
            if (n < 0) {
                throw new IOException("Unexpected end of stream while reading WebSocket frame");
            }
            pos += n;
        }
    }

    /**
     * Reads the next frame header bytes (1 + 1 + 0/2/8 + 0/4) and
     * populates {@link #frameRemaining}, {@link #masked},
     * {@link #maskKey}, {@link #maskOffset} and the current opcode.
     *
     * @apiNote
     * Returns {@code false} only when end-of-stream is reached
     * cleanly at the boundary between frames; a partial header
     * mid-read raises {@link IOException} via {@link #readByte()}.
     *
     * @return {@code true} if a frame header was parsed,
     *         {@code false} on clean end-of-stream
     * @throws IOException if the header is malformed or truncated
     */
    private boolean parseFrameHeader() throws IOException {
        var first = readByteOrEof();
        if (first < 0) {
            return false;
        }
        var second = readByte();

        if ((first & 0x70) != 0) {
            throw new IOException("WebSocket frame has reserved bits set: 0x"
                    + Integer.toHexString(first));
        }

        currentOpcode = (byte) (first & 0x0F);
        var maskedBit = (second & 0x80) != 0;
        var lengthField = second & 0x7F;

        long length;
        if (lengthField <= SMALL_PAYLOAD_LIMIT) {
            length = lengthField;
        } else if (lengthField == EXTENDED_16_PAYLOAD_MARKER) {
            length = ((long) readByte() << 8) | readByte();
        } else {
            length = 0;
            for (var i = 0; i < 8; i++) {
                length = (length << 8) | readByte();
            }
        }

        if (length < 0 || length > MAX_FRAME_LENGTH) {
            throw new IOException("WebSocket frame length out of bounds: " + length);
        }

        if (isControlOpcode() && length > CONTROL_PAYLOAD_MAX_LENGTH) {
            throw new IOException("WebSocket control frame too large: " + length);
        }

        masked = maskedBit;
        maskOffset = 0;
        if (maskedBit) {
            maskKey = (readByte() << 24)
                    | (readByte() << 16)
                    | (readByte() << 8)
                    | readByte();
        } else {
            maskKey = 0;
        }

        frameRemaining = length;
        return true;
    }

    /**
     * Returns one byte or {@code -1} on clean end-of-stream.
     *
     * @apiNote
     * Used when reading the first byte of a new frame header where a
     * peer disconnect is a normal close rather than an error; the
     * mid-frame {@link #readByte()} variant raises
     * {@link IOException} instead.
     *
     * @return the byte in {@code 0..255}, or {@code -1} on
     *         end-of-stream
     * @throws IOException on read failure
     */
    private int readByteOrEof() throws IOException {
        if (leftover != null) {
            var b = leftover.get() & 0xFF;
            if (!leftover.hasRemaining()) {
                leftover = null;
            }
            return b;
        }
        return in.read();
    }

    /**
     * Returns whether the current frame's opcode identifies a
     * control frame (CLOSE, PING, PONG).
     *
     * @return {@code true} if the current opcode is a control opcode
     */
    private boolean isControlOpcode() {
        return currentOpcode == OPCODE_CLOSE
                || currentOpcode == OPCODE_PING
                || currentOpcode == OPCODE_PONG;
    }

    /**
     * Drains the current control frame's payload into
     * {@link #controlPayload} (unmasking if necessary) and
     * dispatches: {@link WebSocketFrameConstants#OPCODE_PING} replies
     * with a matching PONG through the paired output stream;
     * {@link WebSocketFrameConstants#OPCODE_CLOSE} replies with a
     * CLOSE acknowledgement (best effort) and marks the stream
     * closed; {@link WebSocketFrameConstants#OPCODE_PONG} is silently
     * dropped since outstanding pings are not tracked.
     *
     * @throws IOException if reading or replying fails
     */
    private void handleControlFrame() throws IOException {
        var length = (int) frameRemaining;
        if (length > 0) {
            readFully(controlPayload, 0, length);
            if (masked) {
                applyMaskToArray(controlPayload, 0, length, maskKey, maskOffset);
                maskOffset += length;
            }
        }
        frameRemaining = 0;

        switch (currentOpcode) {
            case OPCODE_PING -> pairedOutput.writeControlFrame(OPCODE_PONG, controlPayload, length);
            case OPCODE_CLOSE -> {
                try {
                    pairedOutput.writeControlFrame(OPCODE_CLOSE, controlPayload, length);
                } catch (IOException _) {
                    // Peer may have torn down the socket before the
                    // acknowledgement could land; CLOSE is best effort.
                }
                closed = true;
            }
            case OPCODE_PONG -> { /* ignore */ }
            default -> throw new IOException("Unexpected control opcode: 0x"
                    + Integer.toHexString(currentOpcode));
        }
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
     *                   cycle
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
