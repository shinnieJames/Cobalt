package com.github.auties00.cobalt.call.rtp.srtp;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the RFC 3711 section 3.3.2 sliding-window semantics of {@link SrtpReplayWindow}:
 * a replayed index is rejected, an index older than the 64-wide window is rejected, an
 * in-order index is accepted, and an out-of-order-but-in-window index is accepted
 * exactly once.
 */
public class SrtpReplayWindowTest {

    @Test
    public void acceptsFirstIndex() {
        var w = new SrtpReplayWindow();
        assertTrue(w.check(0));
        w.update(0);
    }

    @Test
    public void rejectsExactReplay() {
        var w = new SrtpReplayWindow();
        w.update(100);
        assertFalse(w.check(100));
    }

    @Test
    public void acceptsMonotonicSequence() {
        var w = new SrtpReplayWindow();
        for (var i = 0; i < 200; i++) {
            assertTrue(w.check(i));
            w.update(i);
        }
    }

    @Test
    public void acceptsOutOfOrderInWindow() {
        var w = new SrtpReplayWindow();
        for (var i = 0; i < 100; i++) {
            w.update(i);
        }
        var w2 = new SrtpReplayWindow();
        w2.update(100);
        // 90 falls within 64 of top (100) and has not been seen.
        assertTrue(w2.check(90));
        w2.update(90);
        assertFalse(w2.check(90));
    }

    @Test
    public void rejectsTooOldIndex() {
        var w = new SrtpReplayWindow();
        w.update(100);
        assertFalse(w.check(35));
    }

    @Test
    public void acceptsLargeJumpForward() {
        var w = new SrtpReplayWindow();
        w.update(10);
        assertTrue(w.check(10_000));
        w.update(10_000);
        assertFalse(w.check(10));
        assertTrue(w.check(9_950));
        w.update(9_950);
        assertFalse(w.check(9_950));
    }
}
