package com.github.auties00.cobalt.call.source;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers {@link ToneGenerator}: continuous-sine output, phase continuity across frames, the
 * Western European ringback on/off cadence, the 16-digit DTMF keypad, and constructor validation.
 */
class ToneGeneratorTest {
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

    @Test
    void phaseAdvances() {
        var src = ToneGenerator.sine(440);
        var a = src.next();
        var b = src.next();
        // 160-sample frames of a 440 Hz tone at 16 kHz: the 36.36-sample period never aligns to
        // the frame boundary, so consecutive frames differ unless phase resets between them.
        assertNotEqualsArrays(a.pcm(), b.pcm());
    }

    @Test
    void ringbackCadence() {
        var src = ToneGenerator.ringback();
        // Western European cadence: 1 s on (100 frames at 10 ms), then 4 s off.
        for (var i = 0; i < 100; i++) {
            src.next();
        }
        // Frame 101 has crossed into the off phase, so its samples must be silent.
        var off = src.next();
        for (var s : off.pcm()) {
            assertEquals((short) 0, s);
        }
    }

    @Test
    void dtmfDigits() {
        for (var d : "0123456789ABCD*#".toCharArray()) {
            var src = ToneGenerator.dtmf(d);
            assertNotNull(src.next());
        }
        assertThrows(IllegalArgumentException.class, () -> ToneGenerator.dtmf('Z'));
    }

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

    private static void assertNotEqualsArrays(short[] a, short[] b) {
        if (a.length != b.length) return;
        for (var i = 0; i < a.length; i++) {
            if (a[i] != b[i]) return;
        }
        throw new AssertionError("expected arrays to differ");
    }
}
