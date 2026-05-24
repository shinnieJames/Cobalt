package com.github.auties00.cobalt.wam.privatestats.ed25519;

import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Validates {@link Ed25519Point} against an independent
 * {@link BigInteger}-based reference implementation of affine
 * Edwards-curve arithmetic.
 *
 * @apiNote
 * The reference uses the affine formulas directly with
 * {@link BigInteger#modPow} for field inversion. It shares zero code
 * with the radix-{@code 2^16} port, so agreement on randomised
 * inputs is strong evidence the extended-coordinate code computes
 * the same group operation.
 */
class Ed25519PointTest {
    /**
     * The Ed25519 field prime {@code p = 2^255 - 19}.
     */
    private static final BigInteger P = BigInteger.ONE.shiftLeft(255).subtract(BigInteger.valueOf(19));

    /**
     * The Ed25519 group order
     * {@code L = 2^252 + 27742317777372353535851937790883648493}.
     */
    private static final BigInteger L =
            BigInteger.ONE.shiftLeft(252).add(new BigInteger("27742317777372353535851937790883648493"));

    /**
     * The Edwards-curve constant {@code d = -121665 / 121666 (mod p)}.
     */
    private static final BigInteger D =
            BigInteger.valueOf(-121665).multiply(BigInteger.valueOf(121666).modInverse(P)).mod(P);

    /**
     * The affine x-coordinate of the Ed25519 base point.
     */
    private static final BigInteger BX =
            new BigInteger("15112221349535400772501151409588531511454012693041857206046113283949847762202");

    /**
     * The affine y-coordinate of the Ed25519 base point
     * ({@code 4/5 mod p}).
     */
    private static final BigInteger BY = BigInteger.valueOf(4).multiply(BigInteger.valueOf(5).modInverse(P)).mod(P);

    /**
     * The point at infinity in affine Edwards form.
     */
    private static final BigInteger[] IDENTITY = {BigInteger.ZERO, BigInteger.ONE};

    /**
     * The number of random iterations per property-style test.
     */
    private static final int ITERATIONS = 16;

    /**
     * Asserts {@code 0 * B == identity}.
     */
    @Test
    void scalarMultBaseByZeroYieldsIdentity() {
        var s = new byte[32];
        var p = Ed25519Point.p3();
        Ed25519Point.scalarMultBase(p, s);
        var packed = new byte[32];
        Ed25519Point.pack(packed, p);
        assertArrayEquals(packAffine(IDENTITY), packed);
    }

    /**
     * Asserts {@code 1 * B == B}.
     */
    @Test
    void scalarMultBaseByOneYieldsBase() {
        var s = new byte[32];
        s[0] = 1;
        var p = Ed25519Point.p3();
        Ed25519Point.scalarMultBase(p, s);
        var packed = new byte[32];
        Ed25519Point.pack(packed, p);
        assertArrayEquals(packAffine(new BigInteger[]{BX, BY}), packed);
    }

    /**
     * Asserts {@code L * B == identity}, exercising the full
     * 253-bit ladder.
     */
    @Test
    void scalarMultBaseByOrderYieldsIdentity() {
        var s = bigIntegerToScalar(L);
        var p = Ed25519Point.p3();
        Ed25519Point.scalarMultBase(p, s);
        var packed = new byte[32];
        Ed25519Point.pack(packed, p);
        assertArrayEquals(packAffine(IDENTITY), packed);
    }

    /**
     * Asserts {@link Ed25519Point#scalarMultBase} matches the
     * affine reference for randomised scalars.
     */
    @Test
    void scalarMultBaseMatchesAffineReference() {
        var rng = new Random(0xC0BA40L);
        for (var i = 0; i < ITERATIONS; i++) {
            var k = new BigInteger(252, rng);
            var s = bigIntegerToScalar(k);

            var actual = new byte[32];
            var p = Ed25519Point.p3();
            Ed25519Point.scalarMultBase(p, s);
            Ed25519Point.pack(actual, p);

            var expected = packAffine(scalarMultAffine(k, new BigInteger[]{BX, BY}));
            assertArrayEquals(expected, actual, "scalarMultBase disagreed at iteration " + i + " with k=" + k);
        }
    }

    /**
     * Asserts the group-homomorphism property
     * {@code (s + t) * B == s * B + t * B}.
     */
    @Test
    void scalarMultBaseIsHomomorphic() {
        var rng = new Random(0xC0BA41L);
        for (var i = 0; i < ITERATIONS; i++) {
            var s = new BigInteger(252, rng).mod(L);
            var t = new BigInteger(252, rng).mod(L);
            var sum = s.add(t).mod(L);

            var sB = pointTimesBase(s);
            var tB = pointTimesBase(t);
            Ed25519Point.add(sB, tB);
            var lhs = new byte[32];
            Ed25519Point.pack(lhs, sB);

            var sumB = pointTimesBase(sum);
            var rhs = new byte[32];
            Ed25519Point.pack(rhs, sumB);

            assertArrayEquals(rhs, lhs, "homomorphism failed at iteration " + i);
        }
    }

    /**
     * Asserts {@link Ed25519Point#unpackNeg} on a packed point
     * returns the negation: re-packing the result must yield the
     * same y-coordinate bytes as the original with the parity bit
     * flipped, since Edwards negation maps {@code (x, y) -> (-x, y)}.
     */
    @Test
    void unpackNegRoundTripsToNegatedPoint() {
        var rng = new Random(0xC0BA42L);
        for (var i = 0; i < ITERATIONS; i++) {
            var k = new BigInteger(252, rng).mod(L);
            if (k.signum() == 0) {
                continue;
            }
            var p = pointTimesBase(k);
            var encoded = new byte[32];
            Ed25519Point.pack(encoded, p);

            var decoded = Ed25519Point.p3();
            assertEquals(0, Ed25519Point.unpackNeg(decoded, encoded),
                    "unpackNeg rejected a valid encoding at iteration " + i);

            var reEncoded = new byte[32];
            Ed25519Point.pack(reEncoded, decoded);

            for (var j = 0; j < 31; j++) {
                assertEquals(encoded[j], reEncoded[j],
                        "y bytes diverged at byte " + j + " in iteration " + i);
            }
            assertEquals((encoded[31] & 0x7f), (reEncoded[31] & 0x7f),
                    "y high byte (low 7 bits) diverged at iteration " + i);
            assertEquals((encoded[31] & 0x80) ^ 0x80, (reEncoded[31] & 0x80),
                    "x parity must be flipped by unpackNeg at iteration " + i);
        }
    }

    /**
     * Asserts {@link Ed25519Point#unpackNeg} returns {@code -1} on
     * an encoding whose y-coordinate is not a curve point.
     *
     * @apiNote
     * Sweeps small y values from {@code 2} upward until a non-curve
     * one is found; the loop is bounded to keep the test fast.
     */
    @Test
    void unpackNegRejectsInvalidEncoding() {
        var encoded = new byte[32];
        encoded[0] = 2;
        var rejected = false;
        for (var y = 2; y < 64; y++) {
            encoded[0] = (byte) y;
            var p = Ed25519Point.p3();
            if (Ed25519Point.unpackNeg(p, encoded) == -1) {
                rejected = true;
                break;
            }
        }
        assertEquals(true, rejected, "expected at least one non-curve y in [2, 64)");
    }

    /**
     * Asserts {@link Ed25519Point#par25519} equals the parity of
     * the canonical {@link Ed25519Field#pack25519} encoding.
     */
    @Test
    void par25519MatchesPackedLowBit() {
        var rng = new Random(0xC0BA43L);
        for (var i = 0; i < ITERATIONS; i++) {
            BigInteger x;
            do {
                x = new BigInteger(255, rng);
            } while (x.compareTo(P) >= 0);
            var fe = new long[Ed25519Field.LIMBS];
            Ed25519Field.unpack25519(fe, bigIntegerToFieldBytes(x));
            assertEquals(x.testBit(0) ? 1 : 0, Ed25519Point.par25519(fe),
                    "par25519 disagreed at iteration " + i);
        }
    }

    /**
     * Computes {@code k * B} and returns the result as an
     * extended-Edwards point.
     *
     * @param k the scalar
     * @return the extended-Edwards point
     */
    private static long[][] pointTimesBase(BigInteger k) {
        var s = bigIntegerToScalar(k);
        var p = Ed25519Point.p3();
        Ed25519Point.scalarMultBase(p, s);
        return p;
    }

    /**
     * Affine reference: {@code k * P} via simple double-and-add.
     *
     * @implNote
     * This implementation is not constant time; that is acceptable
     * because the reference exists only to oracle the constant-time
     * port.
     *
     * @param k     the scalar
     * @param point the affine input point
     * @return the affine result
     */
    private static BigInteger[] scalarMultAffine(BigInteger k, BigInteger[] point) {
        var result = IDENTITY;
        var addend = point;
        for (var bit = 0; bit < k.bitLength(); bit++) {
            if (k.testBit(bit)) {
                result = addAffine(result, addend);
            }
            addend = addAffine(addend, addend);
        }
        return result;
    }

    /**
     * Affine Edwards addition reference.
     *
     * @param p the left operand
     * @param q the right operand
     * @return {@code p + q} in affine form
     */
    private static BigInteger[] addAffine(BigInteger[] p, BigInteger[] q) {
        var x1y2 = p[0].multiply(q[1]).mod(P);
        var x2y1 = q[0].multiply(p[1]).mod(P);
        var y1y2 = p[1].multiply(q[1]).mod(P);
        var x1x2 = p[0].multiply(q[0]).mod(P);
        var dxxyy = D.multiply(x1x2).multiply(y1y2).mod(P);
        var x3 = x1y2.add(x2y1).mod(P).multiply(BigInteger.ONE.add(dxxyy).modInverse(P)).mod(P);
        var y3 = y1y2.add(x1x2).mod(P).multiply(BigInteger.ONE.subtract(dxxyy).mod(P).modInverse(P)).mod(P);
        return new BigInteger[]{x3, y3};
    }

    /**
     * Encodes an affine point in compressed Ed25519 form.
     *
     * @param point the affine point
     * @return the 32-byte compressed encoding
     */
    private static byte[] packAffine(BigInteger[] point) {
        var out = bigIntegerToFieldBytes(point[1]);
        if (point[0].testBit(0)) {
            out[31] |= (byte) 0x80;
        }
        return out;
    }

    /**
     * Encodes a {@link BigInteger} as a 32-byte little-endian
     * Ed25519 scalar.
     *
     * @param k the scalar value
     * @return the scalar bytes
     */
    private static byte[] bigIntegerToScalar(BigInteger k) {
        return bigIntegerToFieldBytes(k.mod(BigInteger.ONE.shiftLeft(256)));
    }

    /**
     * Encodes a non-negative {@link BigInteger} as 32 little-endian
     * bytes.
     *
     * @param x the value
     * @return the little-endian encoding
     */
    private static byte[] bigIntegerToFieldBytes(BigInteger x) {
        var be = x.toByteArray();
        var out = new byte[32];
        for (var i = 0; i < be.length && i < 32; i++) {
            out[i] = be[be.length - 1 - i];
        }
        return out;
    }
}
