package com.github.auties00.cobalt.util;

import java.nio.ByteBuffer;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

/**
 * Utility methods for random byte/number generation, hex encoding and
 * byte-array concatenation.
 *
 * <p>All random values are produced by a single {@link SecureRandom}
 * instance obtained via {@link SecureRandom#getInstanceStrong()} to ensure
 * cryptographic quality.
 *
 * @implNote Pure Cobalt utility class; no direct WhatsApp Web counterpart.
 *     The APIs it provides are the common toolkit used by callers that
 *     historically imported small helpers from several scattered WA Web
 *     modules (e.g. {@code WABase64}, {@code WACryptoRandom}).
 */
public final class DataUtils {
    /**
     * Shared strong {@link SecureRandom} instance used by every helper.
     */
    private static final SecureRandom RANDOM;

    /**
     * Shared empty byte array, safe to use as a sentinel for zero-length
     * payloads.
     */
    public static final byte[] EMPTY_BYTE_ARRAY = new byte[0];

    /**
     * Shared empty heap {@link ByteBuffer}, safe to use as a sentinel for
     * zero-length buffer payloads.
     *
     * @implNote A zero-capacity buffer exposes no mutable state (position
     *     and limit are fixed at {@code 0}), so a single shared instance is
     *     safe to reuse across threads.
     */
    public static final ByteBuffer EMPTY_BYTE_BUFFER = ByteBuffer.allocate(0);

    /**
     * Character table used by {@link #randomHex(int)} to produce uppercase
     * hexadecimal digits.
     */
    private static final char[] HEX_ALPHABET = new char[]{'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F'};

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
     * Returns a random byte array whose length is sampled uniformly from
     * {@code [minLength, maxLength)}.
     *
     * @param minLength the minimum length inclusive
     * @param maxLength the maximum length exclusive
     * @return the newly-allocated random byte array
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
     * @return the newly-allocated random byte array, or the shared empty
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
     * Concatenates the given byte arrays into a single newly-allocated
     * array, skipping {@code null} entries.
     *
     * @param entries the arrays to join, may be {@code null}
     * @return the concatenation, or the shared empty array if {@code entries}
     *         is {@code null} or every entry is {@code null}/empty
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
     * Returns a random uppercase hexadecimal string of the given length.
     *
     * @param i the number of characters to return
     * @return a new string containing {@code i} random hex digits
     */
    public static String randomHex(int i) {
        var result = new char[i];
        while (i-- > 0) {
            var index = RANDOM.nextInt(0, HEX_ALPHABET.length);
            result[i] = HEX_ALPHABET[index];
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
