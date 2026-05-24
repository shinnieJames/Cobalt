package com.github.auties00.cobalt.call.source;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests for {@link SilenceSource}.
 */
class SilenceSourceTest {
    /**
     * Default silence emits 160-sample, 10-ms frames.
     */
    @Test
    void defaultProfile() throws InterruptedException {
        var src = new SilenceSource();
        var first = src.next();
        var second = src.next();
        assertNotNull(first);
        assertEquals(160, first.pcm().length);
        for (var s : first.pcm()) {
            assertEquals((short) 0, s);
        }
        assertEquals(0L, first.ptsMs());
        assertEquals(10L, second.ptsMs());
    }

    /**
     * The pts increments by the configured frame duration.
     */
    @Test
    void ptsIncrements() throws InterruptedException {
        var src = new SilenceSource(80, 5L);
        var a = src.next();
        var b = src.next();
        assertEquals(0L, a.ptsMs());
        assertEquals(5L, b.ptsMs());
        assertEquals(80, a.pcm().length);
    }

    /**
     * Constructor rejects non-positive arguments.
     */
    @Test
    void rejectsBadArgs() {
        assertThrows(IllegalArgumentException.class, () -> new SilenceSource(0, 10));
        assertThrows(IllegalArgumentException.class, () -> new SilenceSource(160, 0));
    }
}
