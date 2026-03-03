package com.github.auties00.cobalt.util;

import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

public final class FastRandomUtils {
    private static final SecureRandom RANDOM;
    private static final byte[] EMPTY_BYTE_ARRAY = new byte[0];
    private static final char[] HEX_ALPHABET = new char[]{'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F'};

    static {
        try {
            RANDOM = SecureRandom.getInstanceStrong();
        } catch (NoSuchAlgorithmException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private FastRandomUtils() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

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

    public static void randomByteArray(byte[] bytes, int offset, int length) {
        if(length < 0) {
            throw new IllegalArgumentException("Length cannot be negative: " + length);
        }

        var payload = new byte[length];
        RANDOM.nextBytes(payload);
        System.arraycopy(payload, 0, bytes, offset, length);
    }

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

    public static String randomHex(int i) {
        var result = new char[i];
        while (i-- > 0) {
            var index = RANDOM.nextInt(0, HEX_ALPHABET.length);
            result[i] = HEX_ALPHABET[index];
        }
        return new String(result);
    }

    public static byte[] intToBytes(int input, int length) {
        var result = new byte[length];
        for (var i = length - 1; i >= 0; i--) {
            result[i] = (byte) (255 & input);
            input >>>= 8;
        }
        return result;
    }

    public static int bytesToInt(byte[] bytes, int length) {
        var result = 0;
        for (var i = 0; i < length; i++) {
            result = 256 * result + Byte.toUnsignedInt(bytes[i]);
        }
        return result;
    }

    public static int randomInt(int bound) {
        return RANDOM.nextInt(bound);
    }

    public static int randomInt(int min, int max) {
        return RANDOM.nextInt(min, max);
    }

    public static long randomLong(long bound) {
        return RANDOM.nextLong(bound);
    }

    public static long randomLong(long min, long max) {
        return RANDOM.nextLong(min, max);
    }

    public static double randomDouble() {
        return RANDOM.nextDouble();
    }

    public static double randomDouble(double min, double max) {
        return RANDOM.nextDouble(min, max);
    }

    public static double randomDouble(long bound) {
        return RANDOM.nextDouble(bound);
    }
}
