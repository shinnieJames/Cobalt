package com.github.auties00.cobalt.call.stream.capture;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Covers {@link SilenceAudioOutputStream}: the default 160-sample, 10-ms frame profile, monotonically
 * increasing presentation timestamps, and constructor validation of frame size and duration.
 */
class SilenceAudioOutputStreamTest {
    @Test
    void defaultProfile() throws InterruptedException {
        var src = new SilenceAudioOutputStream();
        var first = src.take();
        var second = src.take();
        assertNotNull(first);
        assertEquals(160, first.pcm().length);
        for (var s : first.pcm()) {
            assertEquals((short) 0, s);
        }
        assertEquals(0L, first.ptsMs());
        assertEquals(10L, second.ptsMs());
    }

    @Test
    void ptsIncrements() throws InterruptedException {
        var src = new SilenceAudioOutputStream(80, 5L);
        var a = src.take();
        var b = src.take();
        assertEquals(0L, a.ptsMs());
        assertEquals(5L, b.ptsMs());
        assertEquals(80, a.pcm().length);
    }

    @Test
    void rejectsBadArgs() {
        assertThrows(IllegalArgumentException.class, () -> new SilenceAudioOutputStream(0, 10));
        assertThrows(IllegalArgumentException.class, () -> new SilenceAudioOutputStream(160, 0));
    }
}
