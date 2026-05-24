package com.github.auties00.cobalt.util;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

/**
 * Static helpers for cryptographic random generation, hex encoding,
 * byte-array concatenation, and byte-order-aware read/write of
 * primitive values from {@code byte[]} and {@link ByteBuffer}.
 *
 * @apiNote
 * Use this as the centralised source for randomness inside Cobalt;
 * every helper draws from a shared {@link SecureRandom} obtained via
 * {@link SecureRandom#getInstanceStrong()} so that key generation,
 * salt generation, and analytics IDs all share the same
 * cryptographic-quality entropy source. The read/write helpers wrap
 * {@link VarHandle} byte-array and byte-buffer views so callers can
 * decode wire-format integers without allocating a temporary
 * {@link ByteBuffer} per call.
 */
public final class DataUtils {
    /**
     * Shared empty {@code byte[]} returned from helpers that would
     * otherwise allocate a zero-length array.
     *
     * @apiNote
     * Use this instead of {@code new byte[0]} for zero-length
     * sentinel arrays so callers can compare by identity when they
     * care.
     */
    public static final byte[] EMPTY_BYTE_ARRAY = new byte[0];

    /**
     * Shared empty heap {@link ByteBuffer} backed by
     * {@link #EMPTY_BYTE_ARRAY}.
     *
     * @apiNote
     * Use this for zero-length sentinel buffers where allocating
     * one per call would be wasteful (mostly EOF / no-payload
     * fast paths).
     */
    public static final ByteBuffer EMPTY_BYTE_BUFFER = ByteBuffer.allocate(0);

    /**
     * Shared {@link SecureRandom} drawn from
     * {@link SecureRandom#getInstanceStrong()} on class init.
     *
     * @implNote
     * This implementation holds a single instance for the JVM
     * lifetime so {@code getInstanceStrong()}'s expensive entropy
     * source seed is paid once. Concurrent callers share access;
     * {@link SecureRandom} is thread-safe.
     */
    private static final SecureRandom RANDOM;

    /**
     * Character table used by {@link #randomHex(int)} to encode
     * each nibble as an uppercase hexadecimal digit.
     */
    private static final char[] HEX_ALPHABET = new char[]{'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F'};

    /**
     * {@link VarHandle} that reads and writes {@code short} values
     * from a {@code byte[]} in {@link ByteOrder#BIG_ENDIAN} order.
     *
     * @apiNote
     * Used by {@link #getShort(byte[], int, ByteOrder)} and
     * {@link #putShort(byte[], int, short, ByteOrder)} to avoid a
     * per-call {@link ByteBuffer} allocation.
     */
    private static final VarHandle SHORT_ARRAY_BE = MethodHandles.byteArrayViewVarHandle(short[].class, ByteOrder.BIG_ENDIAN);

    /**
     * {@link VarHandle} that reads and writes {@code short} values
     * from a {@code byte[]} in {@link ByteOrder#LITTLE_ENDIAN}
     * order.
     */
    private static final VarHandle SHORT_ARRAY_LE = MethodHandles.byteArrayViewVarHandle(short[].class, ByteOrder.LITTLE_ENDIAN);

    /**
     * {@link VarHandle} that reads and writes {@code int} values
     * from a {@code byte[]} in {@link ByteOrder#BIG_ENDIAN} order.
     */
    private static final VarHandle INT_ARRAY_BE = MethodHandles.byteArrayViewVarHandle(int[].class, ByteOrder.BIG_ENDIAN);

    /**
     * {@link VarHandle} that reads and writes {@code int} values
     * from a {@code byte[]} in {@link ByteOrder#LITTLE_ENDIAN}
     * order.
     */
    private static final VarHandle INT_ARRAY_LE = MethodHandles.byteArrayViewVarHandle(int[].class, ByteOrder.LITTLE_ENDIAN);

    /**
     * {@link VarHandle} that reads and writes {@code long} values
     * from a {@code byte[]} in {@link ByteOrder#BIG_ENDIAN} order.
     */
    private static final VarHandle LONG_ARRAY_BE = MethodHandles.byteArrayViewVarHandle(long[].class, ByteOrder.BIG_ENDIAN);

    /**
     * {@link VarHandle} that reads and writes {@code long} values
     * from a {@code byte[]} in {@link ByteOrder#LITTLE_ENDIAN}
     * order.
     */
    private static final VarHandle LONG_ARRAY_LE = MethodHandles.byteArrayViewVarHandle(long[].class, ByteOrder.LITTLE_ENDIAN);

    /**
     * {@link VarHandle} that reads and writes {@code float} values
     * from a {@code byte[]} in {@link ByteOrder#BIG_ENDIAN} order.
     */
    private static final VarHandle FLOAT_ARRAY_BE = MethodHandles.byteArrayViewVarHandle(float[].class, ByteOrder.BIG_ENDIAN);

    /**
     * {@link VarHandle} that reads and writes {@code float} values
     * from a {@code byte[]} in {@link ByteOrder#LITTLE_ENDIAN}
     * order.
     */
    private static final VarHandle FLOAT_ARRAY_LE = MethodHandles.byteArrayViewVarHandle(float[].class, ByteOrder.LITTLE_ENDIAN);

    /**
     * {@link VarHandle} that reads and writes {@code double} values
     * from a {@code byte[]} in {@link ByteOrder#BIG_ENDIAN} order.
     */
    private static final VarHandle DOUBLE_ARRAY_BE = MethodHandles.byteArrayViewVarHandle(double[].class, ByteOrder.BIG_ENDIAN);

    /**
     * {@link VarHandle} that reads and writes {@code double} values
     * from a {@code byte[]} in {@link ByteOrder#LITTLE_ENDIAN}
     * order.
     */
    private static final VarHandle DOUBLE_ARRAY_LE = MethodHandles.byteArrayViewVarHandle(double[].class, ByteOrder.LITTLE_ENDIAN);

    /**
     * {@link VarHandle} that reads and writes {@code short} values
     * from a {@link ByteBuffer} in {@link ByteOrder#BIG_ENDIAN}
     * order.
     */
    private static final VarHandle SHORT_BUFFER_BE = MethodHandles.byteBufferViewVarHandle(short[].class, ByteOrder.BIG_ENDIAN);

    /**
     * {@link VarHandle} that reads and writes {@code short} values
     * from a {@link ByteBuffer} in {@link ByteOrder#LITTLE_ENDIAN}
     * order.
     */
    private static final VarHandle SHORT_BUFFER_LE = MethodHandles.byteBufferViewVarHandle(short[].class, ByteOrder.LITTLE_ENDIAN);

    /**
     * {@link VarHandle} that reads and writes {@code int} values
     * from a {@link ByteBuffer} in {@link ByteOrder#BIG_ENDIAN}
     * order.
     */
    private static final VarHandle INT_BUFFER_BE = MethodHandles.byteBufferViewVarHandle(int[].class, ByteOrder.BIG_ENDIAN);

    /**
     * {@link VarHandle} that reads and writes {@code int} values
     * from a {@link ByteBuffer} in {@link ByteOrder#LITTLE_ENDIAN}
     * order.
     */
    private static final VarHandle INT_BUFFER_LE = MethodHandles.byteBufferViewVarHandle(int[].class, ByteOrder.LITTLE_ENDIAN);

    /**
     * {@link VarHandle} that reads and writes {@code long} values
     * from a {@link ByteBuffer} in {@link ByteOrder#BIG_ENDIAN}
     * order.
     */
    private static final VarHandle LONG_BUFFER_BE = MethodHandles.byteBufferViewVarHandle(long[].class, ByteOrder.BIG_ENDIAN);

    /**
     * {@link VarHandle} that reads and writes {@code long} values
     * from a {@link ByteBuffer} in {@link ByteOrder#LITTLE_ENDIAN}
     * order.
     */
    private static final VarHandle LONG_BUFFER_LE = MethodHandles.byteBufferViewVarHandle(long[].class, ByteOrder.LITTLE_ENDIAN);

    /**
     * {@link VarHandle} that reads and writes {@code float} values
     * from a {@link ByteBuffer} in {@link ByteOrder#BIG_ENDIAN}
     * order.
     */
    private static final VarHandle FLOAT_BUFFER_BE = MethodHandles.byteBufferViewVarHandle(float[].class, ByteOrder.BIG_ENDIAN);

    /**
     * {@link VarHandle} that reads and writes {@code float} values
     * from a {@link ByteBuffer} in {@link ByteOrder#LITTLE_ENDIAN}
     * order.
     */
    private static final VarHandle FLOAT_BUFFER_LE = MethodHandles.byteBufferViewVarHandle(float[].class, ByteOrder.LITTLE_ENDIAN);

    /**
     * {@link VarHandle} that reads and writes {@code double} values
     * from a {@link ByteBuffer} in {@link ByteOrder#BIG_ENDIAN}
     * order.
     */
    private static final VarHandle DOUBLE_BUFFER_BE = MethodHandles.byteBufferViewVarHandle(double[].class, ByteOrder.BIG_ENDIAN);

    /**
     * {@link VarHandle} that reads and writes {@code double} values
     * from a {@link ByteBuffer} in {@link ByteOrder#LITTLE_ENDIAN}
     * order.
     */
    private static final VarHandle DOUBLE_BUFFER_LE = MethodHandles.byteBufferViewVarHandle(double[].class, ByteOrder.LITTLE_ENDIAN);

    static {
        try {
            RANDOM = SecureRandom.getInstanceStrong();
        } catch (NoSuchAlgorithmException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    /**
     * Prevents instantiation of this utility class.
     *
     * @throws UnsupportedOperationException always
     */
    private DataUtils() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    /**
     * Returns a newly allocated random byte array whose length is
     * sampled uniformly from {@code [minLength, maxLength)}.
     *
     * @apiNote
     * Use this when the caller wants both the length and the
     * contents to be random (variable-length nonces, fuzz inputs).
     *
     * @param minLength the minimum length, inclusive
     * @param maxLength the maximum length, exclusive
     * @return the random byte array
     * @throws IllegalArgumentException if {@code minLength} is
     *                                  negative or strictly greater
     *                                  than {@code maxLength}
     */
    public static byte[] randomByteArray(int minLength, int maxLength) {
        if(minLength < 0) {
            throw new IllegalArgumentException("From cannot be negative: " + minLength);
        }

        if(minLength > maxLength) {
            throw new IllegalArgumentException("From cannot be greater than to: " + minLength + " > " + maxLength);
        }

        var size = RANDOM.nextInt(minLength, maxLength);
        var bytes = new byte[size];
        RANDOM.nextBytes(bytes);
        return bytes;
    }

    /**
     * Returns a random byte array of the given length.
     *
     * @apiNote
     * Use this for fixed-length cryptographic material (Signal
     * pre-keys, salts, encryption keys).
     *
     * @implNote
     * This implementation fast-paths zero-length requests by
     * returning {@link #EMPTY_BYTE_ARRAY}.
     *
     * @param length the exact length
     * @return the newly allocated random byte array, or the shared
     *         empty array when {@code length} is {@code 0}
     * @throws IllegalArgumentException if {@code length} is negative
     */
    public static byte[] randomByteArray(int length) {
        if(length < 0) {
            throw new IllegalArgumentException("Length cannot be negative: " + length);
        }

        if(length == 0) {
            return EMPTY_BYTE_ARRAY;
        }

        var bytes = new byte[length];
        RANDOM.nextBytes(bytes);
        return bytes;
    }

    /**
     * Fills a slice of {@code bytes} with random bytes.
     *
     * @apiNote
     * Use this to seed a contiguous range inside a buffer the
     * caller already owns; avoids a separate allocation for the
     * random material.
     *
     * @implNote
     * This implementation samples into a scratch array first and
     * copies into the destination so callers do not depend on
     * {@link SecureRandom#nextBytes(byte[])} writing in place into
     * the supplied region.
     *
     * @param bytes  the destination array
     * @param offset the index in {@code bytes} at which to start
     *               writing
     * @param length the number of random bytes to generate
     * @throws IllegalArgumentException if {@code length} is negative
     */
    public static void randomByteArray(byte[] bytes, int offset, int length) {
        if(length < 0) {
            throw new IllegalArgumentException("Length cannot be negative: " + length);
        }

        var payload = new byte[length];
        RANDOM.nextBytes(payload);
        System.arraycopy(payload, 0, bytes, offset, length);
    }

    /**
     * Concatenates the given byte arrays into a single newly
     * allocated array, skipping {@code null} entries.
     *
     * @apiNote
     * Use this to build a payload from a handful of
     * fixed-component arrays without the overhead of a
     * {@link java.io.ByteArrayOutputStream}.
     *
     * @implNote
     * This implementation makes two passes: first to compute the
     * total length, second to copy each non-{@code null} entry. A
     * zero total length returns {@link #EMPTY_BYTE_ARRAY}.
     *
     * @param entries the arrays to join, may itself be
     *                {@code null} and may contain {@code null}
     *                entries
     * @return the concatenation, or {@link #EMPTY_BYTE_ARRAY} when
     *         {@code entries} is {@code null} or every entry is
     *         {@code null} or empty
     */
    public static byte[] concatByteArrays(byte[]... entries) {
        if(entries == null) {
            return EMPTY_BYTE_ARRAY;
        }

        var length = 0;
        for(var entry : entries) {
            if(entry != null) {
                length += entry.length;
            }
        }
        if(length == 0) {
            return EMPTY_BYTE_ARRAY;
        }

        var result = new byte[length];
        var offset = 0;
        for(var entry : entries) {
            if(entry != null) {
                System.arraycopy(entry, 0, result, offset, entry.length);
                offset += entry.length;
            }
        }
        return result;
    }

    /**
     * Returns a random uppercase hexadecimal string produced by
     * sampling {@code byteCount} cryptographically-random bytes and
     * encoding them with the alphabet {@code [0-9 A-F]}.
     *
     * @apiNote
     * Use this for stanza identifiers, WAM app-session IDs, and
     * persisted-job uniqueness keys; callers in WA Web include
     * {@code WAWebMsgKey.newId_DEPRECATED}, the messaging and
     * pre-call user-journey loggers, and the default-job-no-queue
     * scheduler. The output length in characters is
     * {@code 2 * byteCount}.
     *
     * @implNote
     * This implementation samples into a scratch buffer via
     * {@link SecureRandom#nextBytes(byte[])} and encodes via
     * {@link #HEX_ALPHABET}; the alphabet is uppercase to match WA
     * Web's {@code WAHex.toHex} output.
     *
     * @param byteCount the number of random bytes to sample; the
     *                  output length in characters is
     *                  {@code 2 * byteCount}
     * @return a new string containing the uppercase hex encoding of
     *         the freshly-sampled byte sequence
     * @throws IllegalArgumentException if {@code byteCount} is
     *                                  negative
     */
    @WhatsAppWebExport(moduleName = "WARandomHex", exports = "randomHex", adaptation = WhatsAppAdaptation.DIRECT)
    public static String randomHex(int byteCount) {
        if (byteCount < 0) {
            throw new IllegalArgumentException("Byte count cannot be negative: " + byteCount);
        }

        var bytes = new byte[byteCount];
        RANDOM.nextBytes(bytes);

        var result = new char[byteCount * 2];
        for (var i = 0; i < byteCount; i++) {
            var b = bytes[i] & 0xFF;
            result[i * 2] = HEX_ALPHABET[b >>> 4];
            result[i * 2 + 1] = HEX_ALPHABET[b & 0x0F];
        }
        return new String(result);
    }

    /**
     * Encodes {@code input} into a big-endian byte array of the
     * given length, treating the value as unsigned.
     *
     * @apiNote
     * Use this for wire-format integers whose length is fixed by
     * the protocol and may be smaller than the native four bytes
     * (the WhatsApp datagram header's int24, for example).
     *
     * @implNote
     * This implementation fills from the least-significant byte
     * upward; bits beyond {@code length * 8} are silently truncated.
     *
     * @param input  the value to encode
     * @param length the number of bytes to produce
     * @return a new byte array containing the big-endian encoding
     */
    public static byte[] intToBytes(int input, int length) {
        var result = new byte[length];
        for (var i = length - 1; i >= 0; i--) {
            result[i] = (byte) (255 & input);
            input >>>= 8;
        }
        return result;
    }

    /**
     * Decodes the first {@code length} bytes of {@code bytes} as a
     * big-endian unsigned integer.
     *
     * @apiNote
     * The inverse of {@link #intToBytes(int, int)}; use it for the
     * same variable-length wire-format integers.
     *
     * @param bytes  the source buffer
     * @param length the number of bytes to read
     * @return the decoded value
     */
    public static int bytesToInt(byte[] bytes, int length) {
        var result = 0;
        for (var i = 0; i < length; i++) {
            result = 256 * result + Byte.toUnsignedInt(bytes[i]);
        }
        return result;
    }

    /**
     * Reads a {@code short} from {@code bytes} at {@code offset}
     * using {@code order}.
     *
     * @apiNote
     * Use this for two-byte fields in wire formats where the byte
     * order varies per protocol.
     *
     * @param bytes  the source array
     * @param offset the byte offset at which to start reading
     * @param order  the byte order to interpret the two bytes with
     * @return the decoded value
     * @throws IndexOutOfBoundsException if {@code offset} is
     *                                   negative or
     *                                   {@code offset + 2 > bytes.length}
     */
    public static short getShort(byte[] bytes, int offset, ByteOrder order) {
        if (order == ByteOrder.BIG_ENDIAN) {
            return (short) SHORT_ARRAY_BE.get(bytes, offset);
        }
        return (short) SHORT_ARRAY_LE.get(bytes, offset);
    }

    /**
     * Writes {@code value} to {@code bytes} at {@code offset} using
     * {@code order}.
     *
     * @apiNote
     * Use this for two-byte fields in wire formats where the byte
     * order varies per protocol.
     *
     * @param bytes  the destination array
     * @param offset the byte offset at which to start writing
     * @param value  the value to encode
     * @param order  the byte order to encode the two bytes with
     * @throws IndexOutOfBoundsException if {@code offset} is
     *                                   negative or
     *                                   {@code offset + 2 > bytes.length}
     */
    public static void putShort(byte[] bytes, int offset, short value, ByteOrder order) {
        if (order == ByteOrder.BIG_ENDIAN) {
            SHORT_ARRAY_BE.set(bytes, offset, value);
        } else {
            SHORT_ARRAY_LE.set(bytes, offset, value);
        }
    }

    /**
     * Reads an {@code int} from {@code bytes} at {@code offset}
     * using {@code order}.
     *
     * @apiNote
     * Use this for four-byte fields in wire formats where the byte
     * order varies per protocol.
     *
     * @param bytes  the source array
     * @param offset the byte offset at which to start reading
     * @param order  the byte order to interpret the four bytes with
     * @return the decoded value
     * @throws IndexOutOfBoundsException if {@code offset} is
     *                                   negative or
     *                                   {@code offset + 4 > bytes.length}
     */
    public static int getInt(byte[] bytes, int offset, ByteOrder order) {
        if (order == ByteOrder.BIG_ENDIAN) {
            return (int) INT_ARRAY_BE.get(bytes, offset);
        }
        return (int) INT_ARRAY_LE.get(bytes, offset);
    }

    /**
     * Writes {@code value} to {@code bytes} at {@code offset} using
     * {@code order}.
     *
     * @apiNote
     * Use this for four-byte fields in wire formats where the byte
     * order varies per protocol.
     *
     * @param bytes  the destination array
     * @param offset the byte offset at which to start writing
     * @param value  the value to encode
     * @param order  the byte order to encode the four bytes with
     * @throws IndexOutOfBoundsException if {@code offset} is
     *                                   negative or
     *                                   {@code offset + 4 > bytes.length}
     */
    public static void putInt(byte[] bytes, int offset, int value, ByteOrder order) {
        if (order == ByteOrder.BIG_ENDIAN) {
            INT_ARRAY_BE.set(bytes, offset, value);
        } else {
            INT_ARRAY_LE.set(bytes, offset, value);
        }
    }

    /**
     * Reads a {@code long} from {@code bytes} at {@code offset}
     * using {@code order}.
     *
     * @apiNote
     * Use this for eight-byte fields in wire formats where the
     * byte order varies per protocol.
     *
     * @param bytes  the source array
     * @param offset the byte offset at which to start reading
     * @param order  the byte order to interpret the eight bytes with
     * @return the decoded value
     * @throws IndexOutOfBoundsException if {@code offset} is
     *                                   negative or
     *                                   {@code offset + 8 > bytes.length}
     */
    public static long getLong(byte[] bytes, int offset, ByteOrder order) {
        if (order == ByteOrder.BIG_ENDIAN) {
            return (long) LONG_ARRAY_BE.get(bytes, offset);
        }
        return (long) LONG_ARRAY_LE.get(bytes, offset);
    }

    /**
     * Writes {@code value} to {@code bytes} at {@code offset} using
     * {@code order}.
     *
     * @apiNote
     * Use this for eight-byte fields in wire formats where the
     * byte order varies per protocol;
     * {@link GcmUtils#createNonce(long)} is the canonical caller
     * for AES-GCM IV derivation.
     *
     * @param bytes  the destination array
     * @param offset the byte offset at which to start writing
     * @param value  the value to encode
     * @param order  the byte order to encode the eight bytes with
     * @throws IndexOutOfBoundsException if {@code offset} is
     *                                   negative or
     *                                   {@code offset + 8 > bytes.length}
     */
    public static void putLong(byte[] bytes, int offset, long value, ByteOrder order) {
        if (order == ByteOrder.BIG_ENDIAN) {
            LONG_ARRAY_BE.set(bytes, offset, value);
        } else {
            LONG_ARRAY_LE.set(bytes, offset, value);
        }
    }

    /**
     * Reads a {@code float} from {@code bytes} at {@code offset}
     * using {@code order}.
     *
     * @apiNote
     * Use this for IEEE-754 single-precision wire-format fields.
     *
     * @param bytes  the source array
     * @param offset the byte offset at which to start reading
     * @param order  the byte order to interpret the four bytes with
     * @return the decoded value
     * @throws IndexOutOfBoundsException if {@code offset} is
     *                                   negative or
     *                                   {@code offset + 4 > bytes.length}
     */
    public static float getFloat(byte[] bytes, int offset, ByteOrder order) {
        if (order == ByteOrder.BIG_ENDIAN) {
            return (float) FLOAT_ARRAY_BE.get(bytes, offset);
        }
        return (float) FLOAT_ARRAY_LE.get(bytes, offset);
    }

    /**
     * Writes {@code value} to {@code bytes} at {@code offset} using
     * {@code order}.
     *
     * @apiNote
     * Use this for IEEE-754 single-precision wire-format fields.
     *
     * @param bytes  the destination array
     * @param offset the byte offset at which to start writing
     * @param value  the value to encode
     * @param order  the byte order to encode the four bytes with
     * @throws IndexOutOfBoundsException if {@code offset} is
     *                                   negative or
     *                                   {@code offset + 4 > bytes.length}
     */
    public static void putFloat(byte[] bytes, int offset, float value, ByteOrder order) {
        if (order == ByteOrder.BIG_ENDIAN) {
            FLOAT_ARRAY_BE.set(bytes, offset, value);
        } else {
            FLOAT_ARRAY_LE.set(bytes, offset, value);
        }
    }

    /**
     * Reads a {@code double} from {@code bytes} at {@code offset}
     * using {@code order}.
     *
     * @apiNote
     * Use this for IEEE-754 double-precision wire-format fields.
     *
     * @param bytes  the source array
     * @param offset the byte offset at which to start reading
     * @param order  the byte order to interpret the eight bytes with
     * @return the decoded value
     * @throws IndexOutOfBoundsException if {@code offset} is
     *                                   negative or
     *                                   {@code offset + 8 > bytes.length}
     */
    public static double getDouble(byte[] bytes, int offset, ByteOrder order) {
        if (order == ByteOrder.BIG_ENDIAN) {
            return (double) DOUBLE_ARRAY_BE.get(bytes, offset);
        }
        return (double) DOUBLE_ARRAY_LE.get(bytes, offset);
    }

    /**
     * Writes {@code value} to {@code bytes} at {@code offset} using
     * {@code order}.
     *
     * @apiNote
     * Use this for IEEE-754 double-precision wire-format fields.
     *
     * @param bytes  the destination array
     * @param offset the byte offset at which to start writing
     * @param value  the value to encode
     * @param order  the byte order to encode the eight bytes with
     * @throws IndexOutOfBoundsException if {@code offset} is
     *                                   negative or
     *                                   {@code offset + 8 > bytes.length}
     */
    public static void putDouble(byte[] bytes, int offset, double value, ByteOrder order) {
        if (order == ByteOrder.BIG_ENDIAN) {
            DOUBLE_ARRAY_BE.set(bytes, offset, value);
        } else {
            DOUBLE_ARRAY_LE.set(bytes, offset, value);
        }
    }

    /**
     * Reads a {@code short} from {@code buffer} at {@code offset}
     * using {@code order}, without affecting the buffer's position.
     *
     * @apiNote
     * Use this for absolute-position reads from a
     * {@link ByteBuffer} when the caller wants byte-order control
     * separate from the buffer's own
     * {@link ByteBuffer#order()}.
     *
     * @param buffer the source buffer
     * @param offset the byte offset at which to start reading
     * @param order  the byte order to interpret the two bytes with
     * @return the decoded value
     * @throws IndexOutOfBoundsException if {@code offset} is
     *                                   negative or
     *                                   {@code offset + 2 > buffer.limit()}
     */
    public static short getShort(ByteBuffer buffer, int offset, ByteOrder order) {
        if (order == ByteOrder.BIG_ENDIAN) {
            return (short) SHORT_BUFFER_BE.get(buffer, offset);
        }
        return (short) SHORT_BUFFER_LE.get(buffer, offset);
    }

    /**
     * Writes {@code value} to {@code buffer} at {@code offset}
     * using {@code order}, without affecting the buffer's position.
     *
     * @apiNote
     * Use this for absolute-position writes from a
     * {@link ByteBuffer}; useful in NIO send paths that compose
     * a single buffer from several producers.
     *
     * @param buffer the destination buffer
     * @param offset the byte offset at which to start writing
     * @param value  the value to encode
     * @param order  the byte order to encode the two bytes with
     * @throws IndexOutOfBoundsException if {@code offset} is
     *                                   negative or
     *                                   {@code offset + 2 > buffer.limit()}
     */
    public static void putShort(ByteBuffer buffer, int offset, short value, ByteOrder order) {
        if (order == ByteOrder.BIG_ENDIAN) {
            SHORT_BUFFER_BE.set(buffer, offset, value);
        } else {
            SHORT_BUFFER_LE.set(buffer, offset, value);
        }
    }

    /**
     * Reads an {@code int} from {@code buffer} at {@code offset}
     * using {@code order}, without affecting the buffer's position.
     *
     * @apiNote
     * Use this for absolute-position reads from a
     * {@link ByteBuffer} when the caller wants byte-order control
     * separate from the buffer's own
     * {@link ByteBuffer#order()}.
     *
     * @param buffer the source buffer
     * @param offset the byte offset at which to start reading
     * @param order  the byte order to interpret the four bytes with
     * @return the decoded value
     * @throws IndexOutOfBoundsException if {@code offset} is
     *                                   negative or
     *                                   {@code offset + 4 > buffer.limit()}
     */
    public static int getInt(ByteBuffer buffer, int offset, ByteOrder order) {
        if (order == ByteOrder.BIG_ENDIAN) {
            return (int) INT_BUFFER_BE.get(buffer, offset);
        }
        return (int) INT_BUFFER_LE.get(buffer, offset);
    }

    /**
     * Writes {@code value} to {@code buffer} at {@code offset}
     * using {@code order}, without affecting the buffer's position.
     *
     * @apiNote
     * Use this for absolute-position writes from a
     * {@link ByteBuffer}; useful in NIO send paths that compose
     * a single buffer from several producers.
     *
     * @param buffer the destination buffer
     * @param offset the byte offset at which to start writing
     * @param value  the value to encode
     * @param order  the byte order to encode the four bytes with
     * @throws IndexOutOfBoundsException if {@code offset} is
     *                                   negative or
     *                                   {@code offset + 4 > buffer.limit()}
     */
    public static void putInt(ByteBuffer buffer, int offset, int value, ByteOrder order) {
        if (order == ByteOrder.BIG_ENDIAN) {
            INT_BUFFER_BE.set(buffer, offset, value);
        } else {
            INT_BUFFER_LE.set(buffer, offset, value);
        }
    }

    /**
     * Reads a {@code long} from {@code buffer} at {@code offset}
     * using {@code order}, without affecting the buffer's position.
     *
     * @apiNote
     * Use this for absolute-position reads from a
     * {@link ByteBuffer} when the caller wants byte-order control
     * separate from the buffer's own
     * {@link ByteBuffer#order()}.
     *
     * @param buffer the source buffer
     * @param offset the byte offset at which to start reading
     * @param order  the byte order to interpret the eight bytes with
     * @return the decoded value
     * @throws IndexOutOfBoundsException if {@code offset} is
     *                                   negative or
     *                                   {@code offset + 8 > buffer.limit()}
     */
    public static long getLong(ByteBuffer buffer, int offset, ByteOrder order) {
        if (order == ByteOrder.BIG_ENDIAN) {
            return (long) LONG_BUFFER_BE.get(buffer, offset);
        }
        return (long) LONG_BUFFER_LE.get(buffer, offset);
    }

    /**
     * Writes {@code value} to {@code buffer} at {@code offset}
     * using {@code order}, without affecting the buffer's position.
     *
     * @apiNote
     * Use this for absolute-position writes from a
     * {@link ByteBuffer}; useful in NIO send paths that compose
     * a single buffer from several producers.
     *
     * @param buffer the destination buffer
     * @param offset the byte offset at which to start writing
     * @param value  the value to encode
     * @param order  the byte order to encode the eight bytes with
     * @throws IndexOutOfBoundsException if {@code offset} is
     *                                   negative or
     *                                   {@code offset + 8 > buffer.limit()}
     */
    public static void putLong(ByteBuffer buffer, int offset, long value, ByteOrder order) {
        if (order == ByteOrder.BIG_ENDIAN) {
            LONG_BUFFER_BE.set(buffer, offset, value);
        } else {
            LONG_BUFFER_LE.set(buffer, offset, value);
        }
    }

    /**
     * Reads a {@code float} from {@code buffer} at {@code offset}
     * using {@code order}, without affecting the buffer's position.
     *
     * @apiNote
     * Use this for IEEE-754 single-precision absolute-position
     * reads from a {@link ByteBuffer}.
     *
     * @param buffer the source buffer
     * @param offset the byte offset at which to start reading
     * @param order  the byte order to interpret the four bytes with
     * @return the decoded value
     * @throws IndexOutOfBoundsException if {@code offset} is
     *                                   negative or
     *                                   {@code offset + 4 > buffer.limit()}
     */
    public static float getFloat(ByteBuffer buffer, int offset, ByteOrder order) {
        if (order == ByteOrder.BIG_ENDIAN) {
            return (float) FLOAT_BUFFER_BE.get(buffer, offset);
        }
        return (float) FLOAT_BUFFER_LE.get(buffer, offset);
    }

    /**
     * Writes {@code value} to {@code buffer} at {@code offset}
     * using {@code order}, without affecting the buffer's position.
     *
     * @apiNote
     * Use this for IEEE-754 single-precision absolute-position
     * writes from a {@link ByteBuffer}.
     *
     * @param buffer the destination buffer
     * @param offset the byte offset at which to start writing
     * @param value  the value to encode
     * @param order  the byte order to encode the four bytes with
     * @throws IndexOutOfBoundsException if {@code offset} is
     *                                   negative or
     *                                   {@code offset + 4 > buffer.limit()}
     */
    public static void putFloat(ByteBuffer buffer, int offset, float value, ByteOrder order) {
        if (order == ByteOrder.BIG_ENDIAN) {
            FLOAT_BUFFER_BE.set(buffer, offset, value);
        } else {
            FLOAT_BUFFER_LE.set(buffer, offset, value);
        }
    }

    /**
     * Reads a {@code double} from {@code buffer} at {@code offset}
     * using {@code order}, without affecting the buffer's position.
     *
     * @apiNote
     * Use this for IEEE-754 double-precision absolute-position
     * reads from a {@link ByteBuffer}.
     *
     * @param buffer the source buffer
     * @param offset the byte offset at which to start reading
     * @param order  the byte order to interpret the eight bytes with
     * @return the decoded value
     * @throws IndexOutOfBoundsException if {@code offset} is
     *                                   negative or
     *                                   {@code offset + 8 > buffer.limit()}
     */
    public static double getDouble(ByteBuffer buffer, int offset, ByteOrder order) {
        if (order == ByteOrder.BIG_ENDIAN) {
            return (double) DOUBLE_BUFFER_BE.get(buffer, offset);
        }
        return (double) DOUBLE_BUFFER_LE.get(buffer, offset);
    }

    /**
     * Writes {@code value} to {@code buffer} at {@code offset}
     * using {@code order}, without affecting the buffer's position.
     *
     * @apiNote
     * Use this for IEEE-754 double-precision absolute-position
     * writes from a {@link ByteBuffer}.
     *
     * @param buffer the destination buffer
     * @param offset the byte offset at which to start writing
     * @param value  the value to encode
     * @param order  the byte order to encode the eight bytes with
     * @throws IndexOutOfBoundsException if {@code offset} is
     *                                   negative or
     *                                   {@code offset + 8 > buffer.limit()}
     */
    public static void putDouble(ByteBuffer buffer, int offset, double value, ByteOrder order) {
        if (order == ByteOrder.BIG_ENDIAN) {
            DOUBLE_BUFFER_BE.set(buffer, offset, value);
        } else {
            DOUBLE_BUFFER_LE.set(buffer, offset, value);
        }
    }

    /**
     * Returns a random integer in {@code [0, bound)}.
     *
     * @apiNote
     * Use this for uniform-random integers within an exclusive
     * upper bound.
     *
     * @param bound the exclusive upper bound
     * @return the random value
     */
    public static int randomInt(int bound) {
        return RANDOM.nextInt(bound);
    }

    /**
     * Returns a random integer in {@code [min, max)}.
     *
     * @apiNote
     * Use this for uniform-random integers within an arbitrary
     * half-open range. The {@link RandomIdUtils} constructor draws
     * its prefix nibbles from this helper.
     *
     * @param min the inclusive lower bound
     * @param max the exclusive upper bound
     * @return the random value
     */
    public static int randomInt(int min, int max) {
        return RANDOM.nextInt(min, max);
    }

    /**
     * Returns a random long in {@code [0, bound)}.
     *
     * @apiNote
     * Use this for uniform-random longs within an exclusive upper
     * bound.
     *
     * @param bound the exclusive upper bound
     * @return the random value
     */
    public static long randomLong(long bound) {
        return RANDOM.nextLong(bound);
    }

    /**
     * Returns a random long in {@code [min, max)}.
     *
     * @apiNote
     * Use this for uniform-random longs within an arbitrary
     * half-open range; {@link RandomIdUtils#generateSid()} draws
     * its mid-token from this helper.
     *
     * @param min the inclusive lower bound
     * @param max the exclusive upper bound
     * @return the random value
     */
    public static long randomLong(long min, long max) {
        return RANDOM.nextLong(min, max);
    }

    /**
     * Returns a random double in {@code [0.0, 1.0)}.
     *
     * @apiNote
     * Use this for sampling-weight rolls and jittered backoff
     * percentages.
     *
     * @return the random value
     */
    public static double randomDouble() {
        return RANDOM.nextDouble();
    }

    /**
     * Returns a random double in {@code [min, max)}.
     *
     * @apiNote
     * Use this for uniform-random doubles within an arbitrary
     * half-open range.
     *
     * @param min the inclusive lower bound
     * @param max the exclusive upper bound
     * @return the random value
     */
    public static double randomDouble(double min, double max) {
        return RANDOM.nextDouble(min, max);
    }

    /**
     * Returns a random double in {@code [0.0, bound)}.
     *
     * @apiNote
     * Use this for uniform-random doubles within an exclusive
     * upper bound.
     *
     * @param bound the exclusive upper bound
     * @return the random value
     */
    public static double randomDouble(long bound) {
        return RANDOM.nextDouble(bound);
    }
}
