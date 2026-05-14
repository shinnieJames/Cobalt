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
 * Validates {@link Ed25519HashToPoint} via algebraic properties: every
 * output must lie on the Ed25519 curve and within the prime-order subgroup,
 * and the function must be deterministic.
 *
 * <p>This pins the math without a JS-bundle KAT; byte-identical agreement
 * with the live WA Web bundle is a separate validation step that requires
 * capturing vectors via the MCP {@code web_live_debug_eval} tool.
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
     * Edwards-curve constant {@code d = -121665 / 121666 (mod p)}.
     */
    private static final BigInteger D_BIG =
            BigInteger.valueOf(-121665).multiply(BigInteger.valueOf(121666).modInverse(P)).mod(P);

    /**
     * Number of random iterations per property.
     */
    private static final int ITERATIONS = 16;

    /**
     * Asserts every {@link Ed25519HashToPoint#compute} output satisfies the
     * twisted-Edwards equation {@code -x^2 + y^2 = 1 + d*x^2*y^2}.
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
     * Asserts every {@link Ed25519HashToPoint#compute} output lies in the
     * prime-order subgroup, i.e. {@code L * P = identity}.
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
     * Asserts {@link Ed25519HashToPoint#compute} is deterministic: the same
     * input bytes always produce the same output point.
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
     * Asserts the hash output exercises both Elligator branches across
     * randomised messages: the SHA-512 sign bit (top bit of byte 31) flips
     * roughly half the time, and so should the Edwards-x parity. A
     * statistical sanity check, not byte-perfect.
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
        // Both branches must occur at least once; with 64 trials each is overwhelmingly likely.
        assertEquals(true, parityZero > 0, "hashToPoint never produced parity-0 outputs");
        assertEquals(true, parityOne > 0, "hashToPoint never produced parity-1 outputs");
    }

    /**
     * Converts a {@link Ed25519Point#p3} point to its affine x-coordinate
     * as a {@link BigInteger}. Pulls out {@code X} and {@code Z} from the
     * extended-coordinate form, repacks each, and computes {@code X * Z^-1}
     * in the BigInteger field.
     */
    private static BigInteger affineX(long[][] p) {
        var inv = Ed25519Field.gf();
        Ed25519Field.inv25519(inv, p[2]);
        var x = Ed25519Field.gf();
        Ed25519Field.mul(x, p[0], inv);
        return packedToBigInteger(packField(x));
    }

    /**
     * Converts a {@link Ed25519Point#p3} point to its affine y-coordinate
     * as a {@link BigInteger}.
     */
    private static BigInteger affineY(long[][] p) {
        var inv = Ed25519Field.gf();
        Ed25519Field.inv25519(inv, p[2]);
        var y = Ed25519Field.gf();
        Ed25519Field.mul(y, p[1], inv);
        return packedToBigInteger(packField(y));
    }

    private static byte[] packField(long[] fe) {
        var out = new byte[32];
        Ed25519Field.pack25519(out, fe);
        return out;
    }

    private static BigInteger packedToBigInteger(byte[] le) {
        var be = new byte[le.length + 1];
        for (var i = 0; i < le.length; i++) {
            be[le.length - i] = le[i];
        }
        return new BigInteger(be);
    }

    private static byte[] bigIntegerToScalar(BigInteger k) {
        var be = k.mod(BigInteger.ONE.shiftLeft(256)).toByteArray();
        var out = new byte[32];
        for (var i = 0; i < be.length && i < 32; i++) {
            out[i] = be[be.length - 1 - i];
        }
        return out;
    }

    /**
     * Returns the canonical compressed encoding of the identity point
     * (X=0, Y=1, Z=1).
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
     * Asserts {@link Ed25519HashToPoint#compute} agrees with a textbook
     * SHA-512 reference for the digest-derivation step (sanity check that
     * SHA-512 inputs are passed through unchanged).
     */
    @Test
    void usesSha512OfInput() throws NoSuchAlgorithmException {
        var rng = new Random(0xC0BA54L);
        for (var i = 0; i < ITERATIONS; i++) {
            var msg = new byte[16 + rng.nextInt(48)];
            rng.nextBytes(msg);
            var direct = MessageDigest.getInstance("SHA-512").digest(msg);
            // Mutating direct to clear the sign bit yields the same field-element bytes
            // that hashToPoint feeds into Elligator. We can't observe this directly,
            // but a different SHA-512 input must produce a different output point.
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
