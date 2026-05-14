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
 * Utility methods for random byte and number generation, hex encoding, and
 * byte-array concatenation.
 *
 * <p>All random values are produced by a single {@link SecureRandom}
 * instance obtained via {@link SecureRandom#getInstanceStrong()} to ensure
 * cryptographic quality.
 */
public final class DataUtils {
    /**
     * Shared empty byte array, safe to use as a sentinel for zero-length
     * payloads.
     */
    public static final byte[] EMPTY_BYTE_ARRAY = new byte[0];

    /**
     * Shared empty heap {@link ByteBuffer}, safe to use as a sentinel for
     * zero-length buffer payloads.
     */
    public static final ByteBuffer EMPTY_BYTE_BUFFER = ByteBuffer.allocate(0);

    /**
     * Shared strong {@link SecureRandom} instance used by every helper.
     */
    private static final SecureRandom RANDOM;

    /**
     * Character table used by {@link #randomHex(int)} to produce uppercase
     * hexadecimal digits.
     */
    private static final char[] HEX_ALPHABET = new char[]{'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F'};

    /**
     * {@link VarHandle} that reads and writes {@code short} values from a
     * {@code byte[]} in {@link ByteOrder#BIG_ENDIAN} order.
     */
    private static final VarHandle SHORT_ARRAY_BE = MethodHandles.byteArrayViewVarHandle(short[].class, ByteOrder.BIG_ENDIAN);

    /**
     * {@link VarHandle} that reads and writes {@code short} values from a
     * {@code byte[]} in {@link ByteOrder#LITTLE_ENDIAN} order.
     */
    private static final VarHandle SHORT_ARRAY_LE = MethodHandles.byteArrayViewVarHandle(short[].class, ByteOrder.LITTLE_ENDIAN);

    /**
     * {@link VarHandle} that reads and writes {@code int} values from a
     * {@code byte[]} in {@link ByteOrder#BIG_ENDIAN} order.
     */
    private static final VarHandle INT_ARRAY_BE = MethodHandles.byteArrayViewVarHandle(int[].class, ByteOrder.BIG_ENDIAN);

    /**
     * {@link VarHandle} that reads and writes {@code int} values from a
     * {@code byte[]} in {@link ByteOrder#LITTLE_ENDIAN} order.
     */
    private static final VarHandle INT_ARRAY_LE = MethodHandles.byteArrayViewVarHandle(int[].class, ByteOrder.LITTLE_ENDIAN);

    /**
     * {@link VarHandle} that reads and writes {@code long} values from a
     * {@code byte[]} in {@link ByteOrder#BIG_ENDIAN} order.
     */
    private static final VarHandle LONG_ARRAY_BE = MethodHandles.byteArrayViewVarHandle(long[].class, ByteOrder.BIG_ENDIAN);

    /**
     * {@link VarHandle} that reads and writes {@code long} values from a
     * {@code byte[]} in {@link ByteOrder#LITTLE_ENDIAN} order.
     */
    private static final VarHandle LONG_ARRAY_LE = MethodHandles.byteArrayViewVarHandle(long[].class, ByteOrder.LITTLE_ENDIAN);

    /**
     * {@link VarHandle} that reads and writes {@code float} values from a
     * {@code byte[]} in {@link ByteOrder#BIG_ENDIAN} order.
     */
    private static final VarHandle FLOAT_ARRAY_BE = MethodHandles.byteArrayViewVarHandle(float[].class, ByteOrder.BIG_ENDIAN);

    /**
     * {@link VarHandle} that reads and writes {@code float} values from a
     * {@code byte[]} in {@link ByteOrder#LITTLE_ENDIAN} order.
     */
    private static final VarHandle FLOAT_ARRAY_LE = MethodHandles.byteArrayViewVarHandle(float[].class, ByteOrder.LITTLE_ENDIAN);

    /**
     * {@link VarHandle} that reads and writes {@code double} values from a
     * {@code byte[]} in {@link ByteOrder#BIG_ENDIAN} order.
     */
    private static final VarHandle DOUBLE_ARRAY_BE = MethodHandles.byteArrayViewVarHandle(double[].class, ByteOrder.BIG_ENDIAN);

    /**
     * {@link VarHandle} that reads and writes {@code double} values from a
     * {@code byte[]} in {@link ByteOrder#LITTLE_ENDIAN} order.
     */
    private static final VarHandle DOUBLE_ARRAY_LE = MethodHandles.byteArrayViewVarHandle(double[].class, ByteOrder.LITTLE_ENDIAN);

    /**
     * {@link VarHandle} that reads and writes {@code short} values from a
     * {@link ByteBuffer} in {@link ByteOrder#BIG_ENDIAN} order.
     */
    private static final VarHandle SHORT_BUFFER_BE = MethodHandles.byteBufferViewVarHandle(short[].class, ByteOrder.BIG_ENDIAN);

    /**
     * {@link VarHandle} that reads and writes {@code short} values from a
     * {@link ByteBuffer} in {@link ByteOrder#LITTLE_ENDIAN} order.
     */
    private static final VarHandle SHORT_BUFFER_LE = MethodHandles.byteBufferViewVarHandle(short[].class, ByteOrder.LITTLE_ENDIAN);

    /**
     * {@link VarHandle} that reads and writes {@code int} values from a
     * {@link ByteBuffer} in {@link ByteOrder#BIG_ENDIAN} order.
     */
    private static final VarHandle INT_BUFFER_BE = MethodHandles.byteBufferViewVarHandle(int[].class, ByteOrder.BIG_ENDIAN);

    /**
     * {@link VarHandle} that reads and writes {@code int} values from a
     * {@link ByteBuffer} in {@link ByteOrder#LITTLE_ENDIAN} order.
     */
    private static final VarHandle INT_BUFFER_LE = MethodHandles.byteBufferViewVarHandle(int[].class, ByteOrder.LITTLE_ENDIAN);

    /**
     * {@link VarHandle} that reads and writes {@code long} values from a
     * {@link ByteBuffer} in {@link ByteOrder#BIG_ENDIAN} order.
     */
    private static final VarHandle LONG_BUFFER_BE = MethodHandles.byteBufferViewVarHandle(long[].class, ByteOrder.BIG_ENDIAN);

    /**
     * {@link VarHandle} that reads and writes {@code long} values from a
     * {@link ByteBuffer} in {@link ByteOrder#LITTLE_ENDIAN} order.
     */
    private static final VarHandle LONG_BUFFER_LE = MethodHandles.byteBufferViewVarHandle(long[].class, ByteOrder.LITTLE_ENDIAN);

    /**
     * {@link VarHandle} that reads and writes {@code float} values from a
     * {@link ByteBuffer} in {@link ByteOrder#BIG_ENDIAN} order.
     */
    private static final VarHandle FLOAT_BUFFER_BE = MethodHandles.byteBufferViewVarHandle(float[].class, ByteOrder.BIG_ENDIAN);

    /**
     * {@link VarHandle} that reads and writes {@code float} values from a
     * {@link ByteBuffer} in {@link ByteOrder#LITTLE_ENDIAN} order.
     */
    private static final VarHandle FLOAT_BUFFER_LE = MethodHandles.byteBufferViewVarHandle(float[].class, ByteOrder.LITTLE_ENDIAN);

    /**
     * {@link VarHandle} that reads and writes {@code double} values from a
     * {@link ByteBuffer} in {@link ByteOrder#BIG_ENDIAN} order.
     */
    private static final VarHandle DOUBLE_BUFFER_BE = MethodHandles.byteBufferViewVarHandle(double[].class, ByteOrder.BIG_ENDIAN);

    /**
     * {@link VarHandle} that reads and writes {@code double} values from a
     * {@link ByteBuffer} in {@link ByteOrder#LITTLE_ENDIAN} order.
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
     * Returns a newly allocated random byte array whose length is sampled
     * uniformly from {@code [minLength, maxLength)}.
     *
     * @param minLength the minimum length, inclusive
     * @param maxLength the maximum length, exclusive
     * @return the random byte array
     * @throws IllegalArgumentException if {@code minLength} is negative or
     *                                  greater than {@code maxLength}
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
     * @param length the exact length
     * @return the newly allocated random byte array, or the shared empty
     *         array when {@code length} is {@code 0}
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
     * Fills a slice of the given byte array with random bytes.
     *
     * @param bytes  the destination array
     * @param offset the index in {@code bytes} at which to start writing
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
     * Concatenates the given byte arrays into a single newly allocated
     * array, skipping {@code null} entries.
     *
     * @param entries the arrays to join, may be {@code null}
     * @return the concatenation, or the shared empty array if {@code entries}
     *         is {@code null} or every entry is {@code null} or empty
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
     * Returns a random uppercase hexadecimal string produced by sampling
     * {@code byteCount} cryptographically-random bytes and encoding them
     * with the alphabet {@code [0-9 A-F]}.
     *
     * <p>The returned string therefore has length {@code 2 * byteCount}.
     *
     * @param byteCount the number of random bytes to sample; the output
     *                  length in characters is {@code 2 * byteCount}
     * @return a new string containing the uppercase hex encoding of the
     *         freshly-sampled byte sequence
     * @throws IllegalArgumentException if {@code byteCount} is negative
     */
    @WhatsAppWebExport(moduleName = "WARandomHex", exports = "randomHex", adaptation = WhatsAppAdaptation.DIRECT)
    public static String randomHex(int byteCount) {
        if (byteCount < 0) {
            throw new IllegalArgumentException("Byte count cannot be negative: " + byteCount);
        }

        // WARandomHex.randomHex: var t = new Uint8Array(e); getCrypto().getRandomValues(t);
        var bytes = new byte[byteCount];
        RANDOM.nextBytes(bytes);

        // WAHex.toHex: pushes HEX_ALPHABET[b >> 4] then HEX_ALPHABET[b & 15] for each byte.
        var result = new char[byteCount * 2];
        for (var i = 0; i < byteCount; i++) {
            var b = bytes[i] & 0xFF;
            result[i * 2] = HEX_ALPHABET[b >>> 4];
            result[i * 2 + 1] = HEX_ALPHABET[b & 0x0F];
        }
        return new String(result);
    }

    /**
     * Encodes an integer value into a big-endian byte array of the given
     * length, using unsigned semantics.
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
     * Decodes the first {@code length} bytes of the given array as a
     * big-endian unsigned integer.
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
     * Reads a {@code short} from the given byte array at the given offset
     * using the given byte order.
     *
     * @param bytes  the source array
     * @param offset the byte offset at which to start reading
     * @param order  the byte order to interpret the two bytes with
     * @return the decoded value
     * @throws IndexOutOfBoundsException if {@code offset} is negative or
     *                                   {@code offset + 2 > bytes.length}
     */
    public static short getShort(byte[] bytes, int offset, ByteOrder order) {
        if (order == ByteOrder.BIG_ENDIAN) {
            return (short) SHORT_ARRAY_BE.get(bytes, offset);
        }
        return (short) SHORT_ARRAY_LE.get(bytes, offset);
    }

    /**
     * Writes a {@code short} to the given byte array at the given offset
     * using the given byte order.
     *
     * @param bytes  the destination array
     * @param offset the byte offset at which to start writing
     * @param value  the value to encode
     * @param order  the byte order to encode the two bytes with
     * @throws IndexOutOfBoundsException if {@code offset} is negative or
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
     * Reads an {@code int} from the given byte array at the given offset
     * using the given byte order.
     *
     * @param bytes  the source array
     * @param offset the byte offset at which to start reading
     * @param order  the byte order to interpret the four bytes with
     * @return the decoded value
     * @throws IndexOutOfBoundsException if {@code offset} is negative or
     *                                   {@code offset + 4 > bytes.length}
     */
    public static int getInt(byte[] bytes, int offset, ByteOrder order) {
        if (order == ByteOrder.BIG_ENDIAN) {
            return (int) INT_ARRAY_BE.get(bytes, offset);
        }
        return (int) INT_ARRAY_LE.get(bytes, offset);
    }

    /**
     * Writes an {@code int} to the given byte array at the given offset
     * using the given byte order.
     *
     * @param bytes  the destination array
     * @param offset the byte offset at which to start writing
     * @param value  the value to encode
     * @param order  the byte order to encode the four bytes with
     * @throws IndexOutOfBoundsException if {@code offset} is negative or
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
     * Reads a {@code long} from the given byte array at the given offset
     * using the given byte order.
     *
     * @param bytes  the source array
     * @param offset the byte offset at which to start reading
     * @param order  the byte order to interpret the eight bytes with
     * @return the decoded value
     * @throws IndexOutOfBoundsException if {@code offset} is negative or
     *                                   {@code offset + 8 > bytes.length}
     */
    public static long getLong(byte[] bytes, int offset, ByteOrder order) {
        if (order == ByteOrder.BIG_ENDIAN) {
            return (long) LONG_ARRAY_BE.get(bytes, offset);
        }
        return (long) LONG_ARRAY_LE.get(bytes, offset);
    }

    /**
     * Writes a {@code long} to the given byte array at the given offset
     * using the given byte order.
     *
     * @param bytes  the destination array
     * @param offset the byte offset at which to start writing
     * @param value  the value to encode
     * @param order  the byte order to encode the eight bytes with
     * @throws IndexOutOfBoundsException if {@code offset} is negative or
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
     * Reads a {@code float} from the given byte array at the given offset
     * using the given byte order.
     *
     * @param bytes  the source array
     * @param offset the byte offset at which to start reading
     * @param order  the byte order to interpret the four bytes with
     * @return the decoded value
     * @throws IndexOutOfBoundsException if {@code offset} is negative or
     *                                   {@code offset + 4 > bytes.length}
     */
    public static float getFloat(byte[] bytes, int offset, ByteOrder order) {
        if (order == ByteOrder.BIG_ENDIAN) {
            return (float) FLOAT_ARRAY_BE.get(bytes, offset);
        }
        return (float) FLOAT_ARRAY_LE.get(bytes, offset);
    }

    /**
     * Writes a {@code float} to the given byte array at the given offset
     * using the given byte order.
     *
     * @param bytes  the destination array
     * @param offset the byte offset at which to start writing
     * @param value  the value to encode
     * @param order  the byte order to encode the four bytes with
     * @throws IndexOutOfBoundsException if {@code offset} is negative or
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
     * Reads a {@code double} from the given byte array at the given offset
     * using the given byte order.
     *
     * @param bytes  the source array
     * @param offset the byte offset at which to start reading
     * @param order  the byte order to interpret the eight bytes with
     * @return the decoded value
     * @throws IndexOutOfBoundsException if {@code offset} is negative or
     *                                   {@code offset + 8 > bytes.length}
     */
    public static double getDouble(byte[] bytes, int offset, ByteOrder order) {
        if (order == ByteOrder.BIG_ENDIAN) {
            return (double) DOUBLE_ARRAY_BE.get(bytes, offset);
        }
        return (double) DOUBLE_ARRAY_LE.get(bytes, offset);
    }

    /**
     * Writes a {@code double} to the given byte array at the given offset
     * using the given byte order.
     *
     * @param bytes  the destination array
     * @param offset the byte offset at which to start writing
     * @param value  the value to encode
     * @param order  the byte order to encode the eight bytes with
     * @throws IndexOutOfBoundsException if {@code offset} is negative or
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
     * Reads a {@code short} from the given {@link ByteBuffer} at the given
     * offset using the given byte order, without affecting the buffer's
     * position.
     *
     * @param buffer the source buffer
     * @param offset the byte offset at which to start reading
     * @param order  the byte order to interpret the two bytes with
     * @return the decoded value
     * @throws IndexOutOfBoundsException if {@code offset} is negative or
     *                                   {@code offset + 2 > buffer.limit()}
     */
    public static short getShort(ByteBuffer buffer, int offset, ByteOrder order) {
        if (order == ByteOrder.BIG_ENDIAN) {
            return (short) SHORT_BUFFER_BE.get(buffer, offset);
        }
        return (short) SHORT_BUFFER_LE.get(buffer, offset);
    }

    /**
     * Writes a {@code short} to the given {@link ByteBuffer} at the given
     * offset using the given byte order, without affecting the buffer's
     * position.
     *
     * @param buffer the destination buffer
     * @param offset the byte offset at which to start writing
     * @param value  the value to encode
     * @param order  the byte order to encode the two bytes with
     * @throws IndexOutOfBoundsException if {@code offset} is negative or
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
     * Reads an {@code int} from the given {@link ByteBuffer} at the given
     * offset using the given byte order, without affecting the buffer's
     * position.
     *
     * @param buffer the source buffer
     * @param offset the byte offset at which to start reading
     * @param order  the byte order to interpret the four bytes with
     * @return the decoded value
     * @throws IndexOutOfBoundsException if {@code offset} is negative or
     *                                   {@code offset + 4 > buffer.limit()}
     */
    public static int getInt(ByteBuffer buffer, int offset, ByteOrder order) {
        if (order == ByteOrder.BIG_ENDIAN) {
            return (int) INT_BUFFER_BE.get(buffer, offset);
        }
        return (int) INT_BUFFER_LE.get(buffer, offset);
    }

    /**
     * Writes an {@code int} to the given {@link ByteBuffer} at the given
     * offset using the given byte order, without affecting the buffer's
     * position.
     *
     * @param buffer the destination buffer
     * @param offset the byte offset at which to start writing
     * @param value  the value to encode
     * @param order  the byte order to encode the four bytes with
     * @throws IndexOutOfBoundsException if {@code offset} is negative or
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
     * Reads a {@code long} from the given {@link ByteBuffer} at the given
     * offset using the given byte order, without affecting the buffer's
     * position.
     *
     * @param buffer the source buffer
     * @param offset the byte offset at which to start reading
     * @param order  the byte order to interpret the eight bytes with
     * @return the decoded value
     * @throws IndexOutOfBoundsException if {@code offset} is negative or
     *                                   {@code offset + 8 > buffer.limit()}
     */
    public static long getLong(ByteBuffer buffer, int offset, ByteOrder order) {
        if (order == ByteOrder.BIG_ENDIAN) {
            return (long) LONG_BUFFER_BE.get(buffer, offset);
        }
        return (long) LONG_BUFFER_LE.get(buffer, offset);
    }

    /**
     * Writes a {@code long} to the given {@link ByteBuffer} at the given
     * offset using the given byte order, without affecting the buffer's
     * position.
     *
     * @param buffer the destination buffer
     * @param offset the byte offset at which to start writing
     * @param value  the value to encode
     * @param order  the byte order to encode the eight bytes with
     * @throws IndexOutOfBoundsException if {@code offset} is negative or
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
     * Reads a {@code float} from the given {@link ByteBuffer} at the given
     * offset using the given byte order, without affecting the buffer's
     * position.
     *
     * @param buffer the source buffer
     * @param offset the byte offset at which to start reading
     * @param order  the byte order to interpret the four bytes with
     * @return the decoded value
     * @throws IndexOutOfBoundsException if {@code offset} is negative or
     *                                   {@code offset + 4 > buffer.limit()}
     */
    public static float getFloat(ByteBuffer buffer, int offset, ByteOrder order) {
        if (order == ByteOrder.BIG_ENDIAN) {
            return (float) FLOAT_BUFFER_BE.get(buffer, offset);
        }
        return (float) FLOAT_BUFFER_LE.get(buffer, offset);
    }

    /**
     * Writes a {@code float} to the given {@link ByteBuffer} at the given
     * offset using the given byte order, without affecting the buffer's
     * position.
     *
     * @param buffer the destination buffer
     * @param offset the byte offset at which to start writing
     * @param value  the value to encode
     * @param order  the byte order to encode the four bytes with
     * @throws IndexOutOfBoundsException if {@code offset} is negative or
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
     * Reads a {@code double} from the given {@link ByteBuffer} at the given
     * offset using the given byte order, without affecting the buffer's
     * position.
     *
     * @param buffer the source buffer
     * @param offset the byte offset at which to start reading
     * @param order  the byte order to interpret the eight bytes with
     * @return the decoded value
     * @throws IndexOutOfBoundsException if {@code offset} is negative or
     *                                   {@code offset + 8 > buffer.limit()}
     */
    public static double getDouble(ByteBuffer buffer, int offset, ByteOrder order) {
        if (order == ByteOrder.BIG_ENDIAN) {
            return (double) DOUBLE_BUFFER_BE.get(buffer, offset);
        }
        return (double) DOUBLE_BUFFER_LE.get(buffer, offset);
    }

    /**
     * Writes a {@code double} to the given {@link ByteBuffer} at the given
     * offset using the given byte order, without affecting the buffer's
     * position.
     *
     * @param buffer the destination buffer
     * @param offset the byte offset at which to start writing
     * @param value  the value to encode
     * @param order  the byte order to encode the eight bytes with
     * @throws IndexOutOfBoundsException if {@code offset} is negative or
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
     * @param bound the exclusive upper bound
     * @return the random value
     */
    public static int randomInt(int bound) {
        return RANDOM.nextInt(bound);
    }

    /**
     * Returns a random integer in {@code [min, max)}.
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
     * @param bound the exclusive upper bound
     * @return the random value
     */
    public static long randomLong(long bound) {
        return RANDOM.nextLong(bound);
    }

    /**
     * Returns a random long in {@code [min, max)}.
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
     * @return the random value
     */
    public static double randomDouble() {
        return RANDOM.nextDouble();
    }

    /**
     * Returns a random double in {@code [min, max)}.
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
     * @param bound the exclusive upper bound
     * @return the random value
     */
    public static double randomDouble(long bound) {
        return RANDOM.nextDouble(bound);
    }
}
