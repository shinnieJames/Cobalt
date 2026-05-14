package com.github.auties00.cobalt.wam.privatestats.ed25519;

import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

/**
 * Validates {@link Ed25519Scalar} against {@link BigInteger} as the oracle.
 *
 * <p>For any input bytes the canonical reduction is just
 * {@code BigInteger(bytes).mod(L)}; the test compares 32-byte little-endian
 * encodings of both sides.
 *
 * <p>Tests are seeded for reproducibility.
 */
class Ed25519ScalarTest {
    /**
     * The Ed25519 group order
     * {@code L = 2^252 + 27742317777372353535851937790883648493}.
     */
    private static final BigInteger L =
            BigInteger.ONE.shiftLeft(252).add(new BigInteger("27742317777372353535851937790883648493"));

    /**
     * Number of random iterations per property.
     */
    private static final int ITERATIONS = 256;

    /**
     * Asserts {@link Ed25519Scalar#reduce} on a random 64-byte buffer matches
     * the {@link BigInteger#mod} oracle.
     */
    @Test
    void reduceMatchesOracle() {
        var rng = new Random(0xC0BA30L);
        for (var i = 0; i < ITERATIONS; i++) {
            var input = new byte[Ed25519Scalar.WIDE_BYTES];
            rng.nextBytes(input);
            var expected = bigIntegerToScalarBytes(littleEndianToBigInteger(input).mod(L));

            var buffer = new byte[Ed25519Scalar.WIDE_BYTES];
            System.arraycopy(input, 0, buffer, 0, Ed25519Scalar.WIDE_BYTES);
            Ed25519Scalar.reduce(buffer);

            var actual = new byte[Ed25519Scalar.SCALAR_BYTES];
            System.arraycopy(buffer, 0, actual, 0, Ed25519Scalar.SCALAR_BYTES);
            assertArrayEquals(expected, actual, "reduce disagreed at iteration " + i);
        }
    }

    /**
     * Asserts {@link Ed25519Scalar#reduce} zeroes the high 32 bytes of the
     * 64-byte buffer (the spec contract for this in-place operation).
     */
    @Test
    void reduceZeroesHighHalf() {
        var rng = new Random(0xC0BA31L);
        for (var i = 0; i < ITERATIONS; i++) {
            var buffer = new byte[Ed25519Scalar.WIDE_BYTES];
            rng.nextBytes(buffer);
            Ed25519Scalar.reduce(buffer);
            var highHalf = new byte[Ed25519Scalar.SCALAR_BYTES];
            System.arraycopy(buffer, Ed25519Scalar.SCALAR_BYTES, highHalf, 0,
                    Ed25519Scalar.SCALAR_BYTES);
            assertArrayEquals(new byte[Ed25519Scalar.SCALAR_BYTES], highHalf,
                    "reduce must zero the high 32 bytes at iteration " + i);
        }
    }

    /**
     * Asserts boundary inputs reduce correctly: {@code 0}, {@code L-1},
     * {@code L} (must reduce to {@code 0}), {@code L+1} (must reduce to
     * {@code 1}), and the maximum 512-bit value.
     */
    @Test
    void reduceHandlesBoundaryInputs() {
        assertReducesTo(BigInteger.ZERO, BigInteger.ZERO);
        assertReducesTo(L.subtract(BigInteger.ONE), L.subtract(BigInteger.ONE));
        assertReducesTo(L, BigInteger.ZERO);
        assertReducesTo(L.add(BigInteger.ONE), BigInteger.ONE);

        var maxWide = BigInteger.ONE.shiftLeft(512).subtract(BigInteger.ONE);
        assertReducesTo(maxWide, maxWide.mod(L));
    }

    /**
     * Asserts a scalar already in the canonical {@code [0, L)} range is
     * preserved when zero-padded to 64 bytes and run through reduce.
     */
    @Test
    void reduceIsIdentityOnCanonicalScalars() {
        var rng = new Random(0xC0BA32L);
        for (var i = 0; i < ITERATIONS; i++) {
            BigInteger s;
            do {
                s = new BigInteger(252, rng);
            } while (s.compareTo(L) >= 0);

            var expected = bigIntegerToScalarBytes(s);
            var buffer = new byte[Ed25519Scalar.WIDE_BYTES];
            System.arraycopy(expected, 0, buffer, 0, Ed25519Scalar.SCALAR_BYTES);
            Ed25519Scalar.reduce(buffer);

            var actual = new byte[Ed25519Scalar.SCALAR_BYTES];
            System.arraycopy(buffer, 0, actual, 0, Ed25519Scalar.SCALAR_BYTES);
            assertArrayEquals(expected, actual, "reduce should be identity on canonical s = " + s);
        }
    }

    private static void assertReducesTo(BigInteger input, BigInteger expected) {
        var buffer = new byte[Ed25519Scalar.WIDE_BYTES];
        var bytes = bigIntegerToWideBytes(input);
        System.arraycopy(bytes, 0, buffer, 0, Ed25519Scalar.WIDE_BYTES);
        Ed25519Scalar.reduce(buffer);

        var actual = new byte[Ed25519Scalar.SCALAR_BYTES];
        System.arraycopy(buffer, 0, actual, 0, Ed25519Scalar.SCALAR_BYTES);
        assertArrayEquals(bigIntegerToScalarBytes(expected), actual,
                "reduce(" + input + ") should equal " + expected);
    }

    private static BigInteger littleEndianToBigInteger(byte[] le) {
        var be = new byte[le.length + 1];
        for (var i = 0; i < le.length; i++) {
            be[le.length - i] = le[i];
        }
        return new BigInteger(be);
    }

    private static byte[] bigIntegerToScalarBytes(BigInteger x) {
        return bigIntegerToBytes(x, Ed25519Scalar.SCALAR_BYTES);
    }

    private static byte[] bigIntegerToWideBytes(BigInteger x) {
        return bigIntegerToBytes(x, Ed25519Scalar.WIDE_BYTES);
    }

    private static byte[] bigIntegerToBytes(BigInteger x, int width) {
        var be = x.toByteArray();
        var out = new byte[width];
        for (var i = 0; i < be.length && i < width; i++) {
            out[i] = be[be.length - 1 - i];
        }
        return out;
    }
}
