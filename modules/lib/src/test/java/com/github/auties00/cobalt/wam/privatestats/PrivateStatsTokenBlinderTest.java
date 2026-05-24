package com.github.auties00.cobalt.wam.privatestats;

import com.github.auties00.cobalt.wam.privatestats.ed25519.Ed25519HashToPoint;
import com.github.auties00.cobalt.wam.privatestats.ed25519.Ed25519Point;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises {@link WamPrivateStatsTokenBlinder} through the
 * VOPRF round-trip identity
 * {@code unblind(server.sign(blind(m, k)), k, server.pk) == server.sign(H(m))}.
 *
 * @apiNote
 * Validates the blind and unblind composition that the WhatsApp Web
 * private-stats token issuance protocol relies on. A simulated server
 * runs the same Ed25519 primitives with a known secret scalar
 * {@code sk}; the expected unblinded value is computed independently
 * as {@code sk * H(m)} so any blinding or unblinding regression
 * surfaces as a mismatch.
 *
 * @implNote
 * This implementation cannot certify byte-identical agreement with
 * the live WhatsApp Web JS bundle by itself; that is the role of
 * {@code Ed25519LiveBundleKatTest}, which compares against captured
 * vectors.
 */
class PrivateStatsTokenBlinderTest {
    /**
     * The number of round-trip iterations exercised by each
     * property-style test.
     */
    private static final int ITERATIONS = 16;

    /**
     * Asserts the full client blind/unblind pipeline recovers
     * {@code sk * H(m)} for a server with a known {@code sk}.
     */
    @Test
    void blindUnblindRoundTripRecoversSkTimesHashPoint() {
        var rng = new Random(0xC0BA60L);
        for (var i = 0; i < ITERATIONS; i++) {
            var sk = freshScalar(rng);
            var pk = derivePublicKey(sk);

            var k = freshScalar(rng);
            var msg = new byte[16 + rng.nextInt(48)];
            rng.nextBytes(msg);

            var blinded = WamPrivateStatsTokenBlinder.blind(msg, k);
            var signed = serverSign(blinded, sk);
            var unblinded = WamPrivateStatsTokenBlinder.unblind(signed, k, pk);

            var expected = scalarTimesHashPoint(sk, msg);
            assertArrayEquals(expected, unblinded,
                    "round-trip output != sk * H(m) at iteration " + i);
        }
    }

    /**
     * Asserts that unblinding with the wrong scalar does not
     * recover {@code sk * H(m)}, guarding against a no-op
     * {@code unblind}.
     */
    @Test
    void unblindWithWrongScalarYieldsDifferentValue() {
        var rng = new Random(0xC0BA61L);
        var sk = freshScalar(rng);
        var pk = derivePublicKey(sk);

        var k = freshScalar(rng);
        var wrongK = freshScalar(rng);
        var msg = "the answer is 42".getBytes();

        var blinded = WamPrivateStatsTokenBlinder.blind(msg, k);
        var signed = serverSign(blinded, sk);
        var correct = WamPrivateStatsTokenBlinder.unblind(signed, k, pk);
        var wrong = WamPrivateStatsTokenBlinder.unblind(signed, wrongK, pk);

        var expected = scalarTimesHashPoint(sk, msg);
        assertArrayEquals(expected, correct);
        assertEquals(false, Arrays.equals(expected, wrong),
                "unblind with wrong scalar must not recover sk*H(m)");
    }

    /**
     * Asserts that repeated blinds of the same message with
     * different scalars produce different outputs, guarding against
     * a deterministic blinding bug.
     */
    @Test
    void differentScalarsProduceDifferentBlindedOutputs() {
        var rng = new Random(0xC0BA62L);
        var msg = "constant message".getBytes();
        var k1 = freshScalar(rng);
        var k2 = freshScalar(rng);

        var blinded1 = WamPrivateStatsTokenBlinder.blind(msg, k1);
        var blinded2 = WamPrivateStatsTokenBlinder.blind(msg, k2);
        assertEquals(false, Arrays.equals(blinded1, blinded2),
                "different scalars must produce different blinded outputs");
    }

    /**
     * Asserts {@link WamPrivateStatsTokenBlinder#blind} is
     * deterministic for identical inputs.
     */
    @Test
    void blindIsDeterministic() {
        var rng = new Random(0xC0BA63L);
        var msg = "the quick brown fox".getBytes();
        var k = freshScalar(rng);

        var first = WamPrivateStatsTokenBlinder.blind(msg, k);
        var second = WamPrivateStatsTokenBlinder.blind(msg, k.clone());
        assertArrayEquals(first, second, "blind must be deterministic");
    }

    /**
     * Asserts {@link WamPrivateStatsTokenBlinder#blind} does not
     * mutate the caller's scalar buffer even though it is clamped
     * internally.
     */
    @Test
    void blindDoesNotMutateCallerScalar() {
        var rng = new Random(0xC0BA64L);
        var msg = new byte[64];
        rng.nextBytes(msg);
        var k = freshScalar(rng);
        var kCopy = k.clone();
        WamPrivateStatsTokenBlinder.blind(msg, k);
        assertArrayEquals(kCopy, k, "blind must not mutate the caller's scalar");
    }

    /**
     * Asserts the documented input-validation behaviour of
     * {@link WamPrivateStatsTokenBlinder#blind} and
     * {@link WamPrivateStatsTokenBlinder#unblind}.
     */
    @Test
    void rejectsInvalidInputs() {
        var goodScalar = new byte[32];
        var goodPoint = new byte[32];
        var goodPoint2 = new byte[32];
        var msg = new byte[8];

        assertThrows(NullPointerException.class,
                () -> WamPrivateStatsTokenBlinder.blind(null, goodScalar));
        assertThrows(NullPointerException.class,
                () -> WamPrivateStatsTokenBlinder.blind(msg, null));
        assertThrows(IllegalArgumentException.class,
                () -> WamPrivateStatsTokenBlinder.blind(msg, new byte[31]));

        assertThrows(NullPointerException.class,
                () -> WamPrivateStatsTokenBlinder.unblind(null, goodScalar, goodPoint));
        assertThrows(IllegalArgumentException.class,
                () -> WamPrivateStatsTokenBlinder.unblind(new byte[31], goodScalar, goodPoint));

        var badPoint = new byte[32];
        for (var i = 0; i < 32; i++) {
            badPoint[i] = (byte) 0xff;
        }
        badPoint[31] = 0x7f;
        try {
            WamPrivateStatsTokenBlinder.unblind(goodPoint, goodScalar, badPoint);
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("Edwards point"),
                    "expected an Edwards-point error message, got: " + expected.getMessage());
        }
    }

    /**
     * Returns a fresh unclamped 32-byte scalar.
     *
     * @apiNote
     * Returned without clamping; {@link WamPrivateStatsTokenBlinder}
     * clamps the scalar internally before any scalar multiplication.
     *
     * @param rng the random source
     * @return the freshly generated scalar bytes
     */
    private static byte[] freshScalar(Random rng) {
        var s = new byte[WamPrivateStatsTokenBlinder.TOKEN_BYTES];
        rng.nextBytes(s);
        return s;
    }

    /**
     * Derives a simulated server public key
     * {@code pk = sk * B} as a 32-byte compressed Ed25519 point.
     *
     * @apiNote
     * Uses the same {@link Ed25519Point} primitives as
     * {@link WamPrivateStatsTokenBlinder}; the helper exists so the
     * test can simulate a server with a known secret scalar.
     *
     * @implNote
     * This implementation clamps {@code sk} the same way
     * {@link WamPrivateStatsTokenBlinder#blind} clamps its scalar so
     * the simulated server agrees with the production code on what
     * "the scalar" means.
     *
     * @param sk the secret scalar
     * @return the 32-byte compressed encoding of {@code sk * B}
     */
    private static byte[] derivePublicKey(byte[] sk) {
        var clamped = sk.clone();
        clamped[0] &= (byte) 0xF8;
        clamped[31] &= (byte) 0x7F;
        clamped[31] |= (byte) 0x40;
        var p = Ed25519Point.p3();
        Ed25519Point.scalarMultBase(p, clamped);
        var out = new byte[32];
        Ed25519Point.pack(out, p);
        return out;
    }

    /**
     * Simulates a server signature on a blinded point.
     *
     * @apiNote
     * Used by the round-trip identity tests; the test does not call
     * the real WhatsApp server.
     *
     * @implNote
     * This implementation decodes the blinded point, multiplies it
     * by the clamped server scalar, and re-encodes it; mirrors the
     * arithmetic the WhatsApp ACS server performs in production.
     *
     * @param blinded the 32-byte compressed blinded point
     * @param sk      the server secret scalar
     * @return the 32-byte compressed signed point
     */
    private static byte[] serverSign(byte[] blinded, byte[] sk) {
        var clamped = sk.clone();
        clamped[0] &= (byte) 0xF8;
        clamped[31] &= (byte) 0x7F;
        clamped[31] |= (byte) 0x40;
        var blindedPoint = Ed25519Point.p3();
        if (Ed25519Point.unpack(blindedPoint, blinded) != 0) {
            throw new IllegalStateException("blinded point did not decode");
        }
        var signed = Ed25519Point.p3();
        Ed25519Point.scalarMult(signed, blindedPoint, clamped);
        var out = new byte[32];
        Ed25519Point.pack(out, signed);
        return out;
    }

    /**
     * Computes the expected unblinded value {@code sk * H(m)} as a
     * 32-byte compressed point.
     *
     * @apiNote
     * Used as the oracle against which the blind/unblind round-trip
     * result is compared.
     *
     * @param sk  the server secret scalar
     * @param msg the message
     * @return the 32-byte compressed encoding of {@code sk * H(m)}
     */
    private static byte[] scalarTimesHashPoint(byte[] sk, byte[] msg) {
        var clamped = sk.clone();
        clamped[0] &= (byte) 0xF8;
        clamped[31] &= (byte) 0x7F;
        clamped[31] |= (byte) 0x40;
        var hashPoint = Ed25519HashToPoint.compute(msg);
        var product = Ed25519Point.p3();
        Ed25519Point.scalarMult(product, hashPoint, clamped);
        var out = new byte[32];
        Ed25519Point.pack(out, product);
        return out;
    }
}
