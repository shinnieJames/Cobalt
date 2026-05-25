package com.github.auties00.cobalt.call.filter;

import com.github.auties00.cobalt.call.frame.audio.AudioFrame;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Covers {@link AudioPipe} hand-off between its sink and source: frames written to the sink are
 * delivered untouched to the source, the buffered-frame count is reported, and the capacity bound
 * is enforced.
 */
class AudioPipeTest {
    @Test
    @DisplayName("Frame written to the sink reaches the source untouched")
    void writeThenRead() throws InterruptedException {
        var pipe = new AudioPipe();
        var pcm = new short[]{1, 2, 3, 4};
        pipe.sink().write(new AudioFrame(pcm, 100L));
        var got = pipe.source().next();
        assertArrayEquals(pcm, got.pcm());
        assertEquals(100L, got.ptsMs());
    }

    @Test
    @DisplayName("Reports the queue size after a write")
    void sizeReflectsBufferedFrames() throws InterruptedException {
        var pipe = new AudioPipe();
        assertEquals(0, pipe.size());
        pipe.sink().write(new AudioFrame(new short[]{0}, 0L));
        assertEquals(1, pipe.size());
    }

    @Test
    @DisplayName("Constructor rejects a non-positive capacity")
    void rejectsNonPositiveCapacity() {
        assertThrows(IllegalArgumentException.class, () -> new AudioPipe(0));
        assertThrows(IllegalArgumentException.class, () -> new AudioPipe(-1));
    }
}
