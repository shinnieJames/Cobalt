package com.github.auties00.cobalt.wam.privatestats.ed25519;

import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Validates {@link Ed25519Field} against {@link BigInteger} as the
 * oracle.
 *
 * @apiNote
 * Every field operation has a one-line {@link BigInteger} equivalent
 * ({@code modPow}, {@code multiply}, {@code add}, {@code subtract},
 * {@code modInverse}); the test compares the canonical 32-byte
 * little-endian encoding of both results. The field has no branches
 * on data, so a passing randomised vector is strong evidence the
 * algebra is correct.
 *
 * @implNote
 * This implementation seeds every {@link Random} for reproducibility.
 */
class Ed25519FieldTest {
    /**
     * The Ed25519 field prime {@code p = 2^255 - 19}.
     */
    private static final BigInteger P = BigInteger.ONE.shiftLeft(255).subtract(BigInteger.valueOf(19));

    /**
     * The number of random iterations per property-style test.
     */
    private static final int ITERATIONS = 256;

    /**
     * Asserts {@link Ed25519Field#unpack25519} followed by
     * {@link Ed25519Field#pack25519} is the identity on canonical
     * 32-byte encodings of values in {@code [0, p)}.
     */
    @Test
    void packUnpackRoundTrip() {
        var rng = new Random(0xC0BA17L);
        for (var i = 0; i < ITERATIONS; i++) {
            var x = randomFieldElement(rng);
            var bytes = toLittleEndian(x);
            var fe = new long[Ed25519Field.LIMBS];
            Ed25519Field.unpack25519(fe, bytes);
            var packed = new byte[Ed25519Field.BYTES];
            Ed25519Field.pack25519(packed, fe);
            assertArrayEquals(bytes, packed, "pack(unpack(x)) must equal x for x in [0, p)");
        }
    }

    /**
     * Asserts that {@code x + p} packs to the same canonical
     * encoding as {@code x}.
     */
    @Test
    void packReducesNonCanonicalInputs() {
        var rng = new Random(0xC0BA18L);
        for (var i = 0; i < ITERATIONS; i++) {
            var x = randomFieldElement(rng);
            var fe = fromBigInteger(x);
            fe[0] += 0xffedL;
            for (var k = 1; k < 15; k++) {
                fe[k] += 0xffffL;
            }
            fe[15] += 0x7fffL;
            var packed = new byte[Ed25519Field.BYTES];
            Ed25519Field.pack25519(packed, fe);
            assertArrayEquals(toLittleEndian(x), packed,
                    "pack must reduce x + p to the canonical x for x in [0, p)");
        }
    }

    /**
     * Asserts {@link Ed25519Field#add} matches
     * {@link BigInteger#add} modulo {@code p}.
     */
    @Test
    void addMatchesOracle() {
        var rng = new Random(0xC0BA19L);
        for (var i = 0; i < ITERATIONS; i++) {
            var x = randomFieldElement(rng);
            var y = randomFieldElement(rng);
            var expected = toLittleEndian(x.add(y).mod(P));
            var fx = fromBigInteger(x);
            var fy = fromBigInteger(y);
            var fr = Ed25519Field.gf();
            Ed25519Field.add(fr, fx, fy);
            assertArrayEquals(expected, packCanonical(fr), "add disagreed at iteration " + i);
        }
    }

    /**
     * Asserts {@link Ed25519Field#sub} matches
     * {@link BigInteger#subtract} modulo {@code p}, including the
     * negative-result branch.
     */
    @Test
    void subMatchesOracle() {
        var rng = new Random(0xC0BA1AL);
        for (var i = 0; i < ITERATIONS; i++) {
            var x = randomFieldElement(rng);
            var y = randomFieldElement(rng);
            var expected = toLittleEndian(x.subtract(y).mod(P));
            var fx = fromBigInteger(x);
            var fy = fromBigInteger(y);
            var fr = Ed25519Field.gf();
            Ed25519Field.sub(fr, fx, fy);
            assertArrayEquals(expected, packCanonical(fr), "sub disagreed at iteration " + i);
        }
    }

    /**
     * Asserts {@link Ed25519Field#mul} matches
     * {@link BigInteger#multiply} modulo {@code p}.
     */
    @Test
    void mulMatchesOracle() {
        var rng = new Random(0xC0BA1BL);
        for (var i = 0; i < ITERATIONS; i++) {
            var x = randomFieldElement(rng);
            var y = randomFieldElement(rng);
            var expected = toLittleEndian(x.multiply(y).mod(P));
            var fx = fromBigInteger(x);
            var fy = fromBigInteger(y);
            var fr = Ed25519Field.gf();
            Ed25519Field.mul(fr, fx, fy);
            assertArrayEquals(expected, packCanonical(fr), "mul disagreed at iteration " + i);
        }
    }

    /**
     * Asserts {@link Ed25519Field#square} matches
     * {@link BigInteger#modPow modPow(2, p)}.
     */
    @Test
    void squareMatchesOracle() {
        var rng = new Random(0xC0BA1CL);
        for (var i = 0; i < ITERATIONS; i++) {
            var x = randomFieldElement(rng);
            var expected = toLittleEndian(x.modPow(BigInteger.TWO, P));
            var fx = fromBigInteger(x);
            var fr = Ed25519Field.gf();
            Ed25519Field.square(fr, fx);
            assertArrayEquals(expected, packCanonical(fr), "square disagreed at iteration " + i);
        }
    }

    /**
     * Asserts {@link Ed25519Field#mul} is safe under output-input
     * aliasing in every direction.
     *
     * @apiNote
     * Tweetnacl's {@code M} writes the result to local accumulators
     * before storing into {@code o}, so aliasing must be safe; this
     * pins the same property for the Java port.
     */
    @Test
    void mulHandlesAliasing() {
        var rng = new Random(0xC0BA1DL);
        for (var i = 0; i < ITERATIONS; i++) {
            var x = randomFieldElement(rng);
            var y = randomFieldElement(rng);
            var expected = toLittleEndian(x.multiply(y).mod(P));

            var fx = fromBigInteger(x);
            var fy = fromBigInteger(y);
            Ed25519Field.mul(fx, fx, fy);
            assertArrayEquals(expected, packCanonical(fx), "mul(x,x,y) aliasing disagreed at " + i);

            fx = fromBigInteger(x);
            fy = fromBigInteger(y);
            Ed25519Field.mul(fy, fx, fy);
            assertArrayEquals(expected, packCanonical(fy), "mul(y,x,y) aliasing disagreed at " + i);

            fx = fromBigInteger(x);
            Ed25519Field.mul(fx, fx, fx);
            assertArrayEquals(toLittleEndian(x.modPow(BigInteger.TWO, P)),
                    packCanonical(fx), "mul(x,x,x) self-aliasing disagreed at " + i);
        }
    }

    /**
     * Asserts {@link Ed25519Field#inv25519} matches
     * {@link BigInteger#modInverse} on non-zero input.
     */
    @Test
    void invMatchesOracle() {
        var rng = new Random(0xC0BA1EL);
        for (var i = 0; i < ITERATIONS; i++) {
            var x = randomNonZeroFieldElement(rng);
            var expected = toLittleEndian(x.modInverse(P));
            var fx = fromBigInteger(x);
            var fr = Ed25519Field.gf();
            Ed25519Field.inv25519(fr, fx);
            assertArrayEquals(expected, packCanonical(fr), "inv disagreed at iteration " + i);
        }
    }

    /**
     * Asserts {@code inv(x) * x == 1} for non-zero {@code x}.
     */
    @Test
    void invIsLeftInverseUnderMul() {
        var rng = new Random(0xC0BA1FL);
        var one = toLittleEndian(BigInteger.ONE);
        for (var i = 0; i < ITERATIONS; i++) {
            var x = randomNonZeroFieldElement(rng);
            var fx = fromBigInteger(x);
            var fr = Ed25519Field.gf();
            Ed25519Field.inv25519(fr, fx);
            Ed25519Field.mul(fr, fr, fx);
            assertArrayEquals(one, packCanonical(fr), "inv(x) * x != 1 at iteration " + i);
        }
    }

    /**
     * Asserts {@link Ed25519Field#pow2523} computes
     * {@code x ^ ((p-5)/8) mod p}.
     */
    @Test
    void pow2523MatchesOracle() {
        var exponent = P.subtract(BigInteger.valueOf(5)).shiftRight(3);
        var rng = new Random(0xC0BA20L);
        for (var i = 0; i < ITERATIONS; i++) {
            var x = randomNonZeroFieldElement(rng);
            var expected = toLittleEndian(x.modPow(exponent, P));
            var fx = fromBigInteger(x);
            var fr = Ed25519Field.gf();
            Ed25519Field.pow2523(fr, fx);
            assertArrayEquals(expected, packCanonical(fr), "pow2523 disagreed at iteration " + i);
        }
    }

    /**
     * Asserts {@link Ed25519Field#sel25519} swaps when {@code b == 1}
     * and is the identity when {@code b == 0}.
     */
    @Test
    void sel25519SwapsWhenBitOne() {
        var rng = new Random(0xC0BA21L);
        for (var i = 0; i < ITERATIONS; i++) {
            var p = fromBigInteger(randomFieldElement(rng));
            var q = fromBigInteger(randomFieldElement(rng));
            var pCopy = p.clone();
            var qCopy = q.clone();

            Ed25519Field.sel25519(p, q, 0);
            assertArrayEquals(pCopy, p, "sel25519 with b=0 mutated p");
            assertArrayEquals(qCopy, q, "sel25519 with b=0 mutated q");

            Ed25519Field.sel25519(p, q, 1);
            assertArrayEquals(qCopy, p, "sel25519 with b=1 must place q into p");
            assertArrayEquals(pCopy, q, "sel25519 with b=1 must place original p into q");
        }
    }

    /**
     * Asserts {@link Ed25519Field#unpack25519} masks the high bit of
     * byte 31, matching the {@code o[15] &= 0x7fff} clause of
     * tweetnacl's {@code lowlevel.unpack25519}.
     */
    @Test
    void unpackMasksTopBit() {
        var rng = new Random(0xC0BA22L);
        for (var i = 0; i < ITERATIONS; i++) {
            var bytes = new byte[Ed25519Field.BYTES];
            rng.nextBytes(bytes);
            var withTop = bytes.clone();
            withTop[31] |= (byte) 0x80;
            var withoutTop = bytes.clone();
            withoutTop[31] &= (byte) 0x7f;

            var fe1 = new long[Ed25519Field.LIMBS];
            var fe2 = new long[Ed25519Field.LIMBS];
            Ed25519Field.unpack25519(fe1, withTop);
            Ed25519Field.unpack25519(fe2, withoutTop);
            assertArrayEquals(fe2, fe1, "unpack25519 must mask the top bit");
        }
    }

    /**
     * Asserts boundary inputs pack to the canonical encoding:
     * {@code 0}, {@code 1}, {@code p-1}, {@code p} (must reduce to
     * {@code 0}), and {@code 2p-1} (must reduce to {@code p-1}).
     */
    @Test
    void packHandlesBoundaryInputs() {
        assertEquals(BigInteger.ZERO, fromPacked(packCanonical(fromBigInteger(BigInteger.ZERO))));
        assertEquals(BigInteger.ONE, fromPacked(packCanonical(fromBigInteger(BigInteger.ONE))));
        assertEquals(P.subtract(BigInteger.ONE),
                fromPacked(packCanonical(fromBigInteger(P.subtract(BigInteger.ONE)))));

        var fePMinusOne = fromBigInteger(P.subtract(BigInteger.ONE));
        fePMinusOne[0] += 1;
        assertEquals(BigInteger.ZERO, fromPacked(packCanonical(fePMinusOne)));

        var feTwoPMinusOne = fromBigInteger(P.subtract(BigInteger.ONE));
        feTwoPMinusOne[0] += 0xffedL;
        for (var i = 1; i < 15; i++) {
            feTwoPMinusOne[i] += 0xffffL;
        }
        feTwoPMinusOne[15] += 0x7fffL;
        assertEquals(P.subtract(BigInteger.ONE), fromPacked(packCanonical(feTwoPMinusOne)));
    }

    /**
     * Returns a random {@link BigInteger} in {@code [0, p)}.
     *
     * @param rng the random source
     * @return the random field element
     */
    private static BigInteger randomFieldElement(Random rng) {
        BigInteger x;
        do {
            x = new BigInteger(255, rng);
        } while (x.compareTo(P) >= 0);
        return x;
    }

    /**
     * Returns a random {@link BigInteger} in {@code (0, p)}.
     *
     * @param rng the random source
     * @return the random non-zero field element
     */
    private static BigInteger randomNonZeroFieldElement(Random rng) {
        BigInteger x;
        do {
            x = randomFieldElement(rng);
        } while (x.signum() == 0);
        return x;
    }

    /**
     * Encodes a non-negative {@link BigInteger} as 32 little-endian
     * bytes.
     *
     * @param x the value
     * @return the little-endian encoding
     */
    private static byte[] toLittleEndian(BigInteger x) {
        var be = x.toByteArray();
        var out = new byte[Ed25519Field.BYTES];
        for (var i = 0; i < be.length && i < Ed25519Field.BYTES; i++) {
            out[i] = be[be.length - 1 - i];
        }
        return out;
    }

    /**
     * Decodes a little-endian byte array as an unsigned
     * {@link BigInteger}.
     *
     * @param le the little-endian bytes
     * @return the decoded value
     */
    private static BigInteger fromLittleEndian(byte[] le) {
        var be = new byte[le.length];
        for (var i = 0; i < le.length; i++) {
            be[i] = le[le.length - 1 - i];
        }
        return new BigInteger(1, be);
    }

    /**
     * Decodes a {@link BigInteger} into the radix-{@code 2^16} limb
     * form consumed by {@link Ed25519Field}.
     *
     * @param x the value
     * @return the limb form
     */
    private static long[] fromBigInteger(BigInteger x) {
        var fe = new long[Ed25519Field.LIMBS];
        Ed25519Field.unpack25519(fe, toLittleEndian(x));
        return fe;
    }

    /**
     * Encodes a limb-form field element as 32 canonical little-endian
     * bytes.
     *
     * @param fe the field element
     * @return the canonical encoding
     */
    private static byte[] packCanonical(long[] fe) {
        var out = new byte[Ed25519Field.BYTES];
        Ed25519Field.pack25519(out, fe);
        return out;
    }

    /**
     * Decodes 32 little-endian bytes as an unsigned
     * {@link BigInteger}.
     *
     * @param le the little-endian bytes
     * @return the decoded value
     */
    private static BigInteger fromPacked(byte[] le) {
        return fromLittleEndian(le);
    }
}
