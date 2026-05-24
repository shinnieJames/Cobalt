package com.github.auties00.cobalt.call.source;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link ToneGenerator}.
 */
class ToneGeneratorTest {
    /**
     * A continuous sine produces non-zero samples in every frame.
     */
    @Test
    void sineEmitsNonZero() {
        var src = ToneGenerator.sine(440);
        var frame = src.next();
        assertNotNull(frame);
        assertEquals(160, frame.pcm().length);
        var anyNonZero = false;
        for (var s : frame.pcm()) {
            if (s != 0) {
                anyNonZero = true;
                break;
            }
        }
        assertTrue(anyNonZero);
    }

    /**
     * Phase advances continuously across consecutive frames —
     * the second frame's first sample is not equal to the first
     * frame's first sample (otherwise the wave would be looping).
     */
    @Test
    void phaseAdvances() {
        var src = ToneGenerator.sine(440);
        var a = src.next();
        var b = src.next();
        // Frame size is 160, frequency 440 Hz, sample rate 16 kHz —
        // 440 Hz period = 36.36 samples, so frames don't align.
        assertNotEqualsArrays(a.pcm(), b.pcm());
    }

    /**
     * Ringback's first second is on, then it goes silent for
     * the next four seconds (Western European cadence).
     */
    @Test
    void ringbackCadence() {
        var src = ToneGenerator.ringback();
        // First 100 frames (1 second at 10 ms each) are "on".
        for (var i = 0; i < 100; i++) {
            src.next();
        }
        // Next frame (101st) is in the off phase.
        var off = src.next();
        for (var s : off.pcm()) {
            assertEquals((short) 0, s);
        }
    }

    /**
     * DTMF accepts all 16 valid digits and rejects the rest.
     */
    @Test
    void dtmfDigits() {
        for (var d : "0123456789ABCD*#".toCharArray()) {
            var src = ToneGenerator.dtmf(d);
            assertNotNull(src.next());
        }
        assertThrows(IllegalArgumentException.class, () -> ToneGenerator.dtmf('Z'));
    }

    /**
     * Constructor rejects bad arguments.
     */
    @Test
    void rejectsBadArgs() {
        assertThrows(IllegalArgumentException.class,
                () -> new ToneGenerator(0, 0, 1, 1, 160, 10));
        assertThrows(IllegalArgumentException.class,
                () -> new ToneGenerator(440, -1, 1, 1, 160, 10));
        assertThrows(IllegalArgumentException.class,
                () -> new ToneGenerator(440, 0, 0, 1, 160, 10));
        assertThrows(IllegalArgumentException.class,
                () -> new ToneGenerator(440, 0, 5, 4, 160, 10));
        assertThrows(IllegalArgumentException.class,
                () -> new ToneGenerator(440, 0, 1, 1, 0, 10));
        assertThrows(IllegalArgumentException.class,
                () -> new ToneGenerator(440, 0, 1, 1, 160, 0));
    }

    /**
     * Asserts that two short arrays are not byte-for-byte equal.
     *
     * @param a left
     * @param b right
     */
    private static void assertNotEqualsArrays(short[] a, short[] b) {
        if (a.length != b.length) return;
        for (var i = 0; i < a.length; i++) {
            if (a[i] != b[i]) return;
        }
        throw new AssertionError("expected arrays to differ");
    }
}
