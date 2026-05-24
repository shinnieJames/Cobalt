package com.github.auties00.cobalt.wam.privatestats.ed25519;

import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Validates {@link Ed25519HashToPoint} via algebraic properties:
 * every output must lie on the Ed25519 curve, lie in the
 * prime-order subgroup, and be deterministic in its input.
 *
 * @apiNote
 * Pins the math without a JS-bundle KAT;
 * {@code Ed25519LiveBundleKatTest} separately pins byte-identical
 * agreement against vectors captured from the live WhatsApp Web
 * bundle.
 */
class Ed25519HashToPointTest {
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
    private static final BigInteger D_BIG =
            BigInteger.valueOf(-121665).multiply(BigInteger.valueOf(121666).modInverse(P)).mod(P);

    /**
     * The number of random iterations per property-style test.
     */
    private static final int ITERATIONS = 16;

    /**
     * Asserts every {@link Ed25519HashToPoint#compute} output
     * satisfies the twisted-Edwards equation
     * {@code -x^2 + y^2 = 1 + d*x^2*y^2}.
     */
    @Test
    void outputIsOnCurve() {
        var rng = new Random(0xC0BA50L);
        for (var i = 0; i < ITERATIONS; i++) {
            var msg = new byte[32 + rng.nextInt(64)];
            rng.nextBytes(msg);
            var p = Ed25519HashToPoint.compute(msg);

            var x = affineX(p);
            var y = affineY(p);
            var lhs = y.multiply(y).subtract(x.multiply(x)).mod(P);
            var rhs = BigInteger.ONE.add(D_BIG.multiply(x).multiply(x).multiply(y).multiply(y)).mod(P);
            assertEquals(rhs, lhs, "hashToPoint output not on curve at iteration " + i);
        }
    }

    /**
     * Asserts every {@link Ed25519HashToPoint#compute} output lies
     * in the prime-order subgroup, i.e. {@code L * P == identity}.
     */
    @Test
    void outputIsInPrimeOrderSubgroup() {
        var rng = new Random(0xC0BA51L);
        var lBytes = bigIntegerToScalar(L);
        var identity = packIdentity();
        for (var i = 0; i < ITERATIONS; i++) {
            var msg = new byte[32 + rng.nextInt(64)];
            rng.nextBytes(msg);
            var p = Ed25519HashToPoint.compute(msg);

            var lp = Ed25519Point.p3();
            Ed25519Point.scalarMult(lp, p, lBytes);

            var packed = new byte[32];
            Ed25519Point.pack(packed, lp);
            assertArrayEquals(identity, packed,
                    "hashToPoint output not in prime-order subgroup at iteration " + i);
        }
    }

    /**
     * Asserts {@link Ed25519HashToPoint#compute} is deterministic:
     * the same input bytes always produce the same output point.
     */
    @Test
    void outputIsDeterministic() {
        var rng = new Random(0xC0BA52L);
        for (var i = 0; i < ITERATIONS; i++) {
            var msg = new byte[32 + rng.nextInt(64)];
            rng.nextBytes(msg);

            var first = Ed25519HashToPoint.compute(msg);
            var second = Ed25519HashToPoint.compute(msg);

            var firstPacked = new byte[32];
            var secondPacked = new byte[32];
            Ed25519Point.pack(firstPacked, first);
            Ed25519Point.pack(secondPacked, second);
            assertArrayEquals(firstPacked, secondPacked,
                    "hashToPoint not deterministic at iteration " + i);
        }
    }

    /**
     * Asserts the output exercises both Edwards-x parity branches
     * across randomised messages.
     *
     * @apiNote
     * The SHA-512 sign bit (the high bit of byte 31 of the digest)
     * flips roughly half the time, and so should the recovered
     * Edwards-x parity. A statistical sanity check, not a
     * byte-perfect pin.
     */
    @Test
    void outputExercisesBothParityBranches() {
        var rng = new Random(0xC0BA53L);
        var iters = 64;
        var parityZero = 0;
        var parityOne = 0;
        for (var i = 0; i < iters; i++) {
            var msg = new byte[16 + rng.nextInt(32)];
            rng.nextBytes(msg);
            var p = Ed25519HashToPoint.compute(msg);
            var packed = new byte[32];
            Ed25519Point.pack(packed, p);
            if ((packed[31] & 0x80) != 0) {
                parityOne++;
            } else {
                parityZero++;
            }
        }
        assertEquals(true, parityZero > 0, "hashToPoint never produced parity-0 outputs");
        assertEquals(true, parityOne > 0, "hashToPoint never produced parity-1 outputs");
    }

    /**
     * Returns the affine x-coordinate of an extended-Edwards point
     * as a {@link BigInteger}.
     *
     * @apiNote
     * Used by the curve-equation check; pulls {@code X} and
     * {@code Z} out of the extended form and computes
     * {@code X * Z^-1} in the BigInteger field.
     *
     * @param p the point
     * @return the affine x-coordinate
     */
    private static BigInteger affineX(long[][] p) {
        var inv = Ed25519Field.gf();
        Ed25519Field.inv25519(inv, p[2]);
        var x = Ed25519Field.gf();
        Ed25519Field.mul(x, p[0], inv);
        return packedToBigInteger(packField(x));
    }

    /**
     * Returns the affine y-coordinate of an extended-Edwards point
     * as a {@link BigInteger}.
     *
     * @param p the point
     * @return the affine y-coordinate
     */
    private static BigInteger affineY(long[][] p) {
        var inv = Ed25519Field.gf();
        Ed25519Field.inv25519(inv, p[2]);
        var y = Ed25519Field.gf();
        Ed25519Field.mul(y, p[1], inv);
        return packedToBigInteger(packField(y));
    }

    /**
     * Encodes a limb-form field element as 32 canonical
     * little-endian bytes.
     *
     * @param fe the field element
     * @return the canonical encoding
     */
    private static byte[] packField(long[] fe) {
        var out = new byte[32];
        Ed25519Field.pack25519(out, fe);
        return out;
    }

    /**
     * Decodes 32 little-endian bytes as a non-negative
     * {@link BigInteger}.
     *
     * @param le the little-endian bytes
     * @return the decoded value
     */
    private static BigInteger packedToBigInteger(byte[] le) {
        var be = new byte[le.length + 1];
        for (var i = 0; i < le.length; i++) {
            be[le.length - i] = le[i];
        }
        return new BigInteger(be);
    }

    /**
     * Encodes a {@link BigInteger} as a 32-byte little-endian
     * Ed25519 scalar.
     *
     * @param k the scalar value
     * @return the scalar bytes
     */
    private static byte[] bigIntegerToScalar(BigInteger k) {
        var be = k.mod(BigInteger.ONE.shiftLeft(256)).toByteArray();
        var out = new byte[32];
        for (var i = 0; i < be.length && i < 32; i++) {
            out[i] = be[be.length - 1 - i];
        }
        return out;
    }

    /**
     * Returns the canonical compressed encoding of the identity
     * point ({@code X=0, Y=1, Z=1}).
     *
     * @apiNote
     * Used as the assertion oracle for the prime-order-subgroup
     * test.
     *
     * @return the 32-byte canonical identity encoding
     */
    private static byte[] packIdentity() {
        var identity = Ed25519Point.p3();
        Ed25519Field.set25519(identity[0], Ed25519Field.gf());
        Ed25519Field.set25519(identity[1], Ed25519Field.gfFromSmall(1));
        Ed25519Field.set25519(identity[2], Ed25519Field.gfFromSmall(1));
        Ed25519Field.set25519(identity[3], Ed25519Field.gf());
        var out = new byte[32];
        Ed25519Point.pack(out, identity);
        return out;
    }

    /**
     * Asserts {@link Ed25519HashToPoint#compute} surfaces a real
     * SHA-512 of the input bytes by flipping a single bit and
     * observing a different output point.
     *
     * @throws NoSuchAlgorithmException if the JVM does not provide
     *                                  SHA-512, which is impossible
     *                                  per JCE conformance
     */
    @Test
    void usesSha512OfInput() throws NoSuchAlgorithmException {
        var rng = new Random(0xC0BA54L);
        for (var i = 0; i < ITERATIONS; i++) {
            var msg = new byte[16 + rng.nextInt(48)];
            rng.nextBytes(msg);
            var direct = MessageDigest.getInstance("SHA-512").digest(msg);
            var msgFlipped = msg.clone();
            msgFlipped[0] ^= 1;
            var directFlipped = MessageDigest.getInstance("SHA-512").digest(msgFlipped);

            assertEquals(false, Arrays.equals(direct, directFlipped),
                    "SHA-512 should differ for flipped input (sanity check)");

            var p1 = Ed25519HashToPoint.compute(msg);
            var p2 = Ed25519HashToPoint.compute(msgFlipped);
            var packed1 = new byte[32];
            var packed2 = new byte[32];
            Ed25519Point.pack(packed1, p1);
            Ed25519Point.pack(packed2, p2);
            assertEquals(false, Arrays.equals(packed1, packed2),
                    "hashToPoint must produce different outputs for flipped input at iteration " + i);
        }
    }
}
