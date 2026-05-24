package com.github.auties00.cobalt.call.internal.rtp.srtp;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link SrtpReplayWindow}: pin the RFC 3711 §3.3.2
 * sliding-window semantics — a replayed index is rejected, an old
 * index outside the window is rejected, an in-order index is
 * accepted, and an out-of-order-but-in-window index is accepted
 * exactly once.
 */
public class SrtpReplayWindowTest {

    /**
     * A fresh window accepts the very first index regardless of its
     * value.
     */
    @Test
    public void acceptsFirstIndex() {
        var w = new SrtpReplayWindow();
        assertTrue(w.check(0));
        w.update(0);
    }

    /**
     * Once an index has been accepted, the same index is rejected.
     */
    @Test
    public void rejectsExactReplay() {
        var w = new SrtpReplayWindow();
        w.update(100);
        assertFalse(w.check(100));
    }

    /**
     * Indices arriving in monotonic order are always accepted.
     */
    @Test
    public void acceptsMonotonicSequence() {
        var w = new SrtpReplayWindow();
        for (var i = 0; i < 200; i++) {
            assertTrue(w.check(i));
            w.update(i);
        }
    }

    /**
     * An out-of-order index that still falls inside the 64-wide
     * window is accepted exactly once.
     */
    @Test
    public void acceptsOutOfOrderInWindow() {
        var w = new SrtpReplayWindow();
        for (var i = 0; i < 100; i++) {
            w.update(i);
        }
        // index 50 is within 64 of top (99); never seen explicitly
        // outside the monotonic loop, but the monotonic loop did mark
        // it. Let's pick an out-of-order index we're sure was missed.
        var w2 = new SrtpReplayWindow();
        w2.update(100);
        // 90 is within window of 100, and unseen.
        assertTrue(w2.check(90));
        w2.update(90);
        // Replay of 90 must be rejected.
        assertFalse(w2.check(90));
    }

    /**
     * An index more than 64 behind {@code top} is treated as too old
     * and rejected.
     */
    @Test
    public void rejectsTooOldIndex() {
        var w = new SrtpReplayWindow();
        w.update(100);
        assertFalse(w.check(35));
    }

    /**
     * A large jump forward is accepted; subsequent indices within
     * the new window are still discriminated correctly.
     */
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
