package com.github.auties00.cobalt.call.filter;

import com.github.auties00.cobalt.call.frame.audio.AudioFrame;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests for {@link AudioPipe}.
 */
class AudioPipeTest {
    /**
     * Frame written to the sink reaches the source untouched.
     */
    @Test
    void writeThenRead() throws InterruptedException {
        var pipe = new AudioPipe();
        var pcm = new short[]{1, 2, 3, 4};
        pipe.sink().write(new AudioFrame(pcm, 100L));
        var got = pipe.source().next();
        assertArrayEquals(pcm, got.pcm());
        assertEquals(100L, got.ptsMs());
    }

    /**
     * The pipe reports the queue size after a write.
     */
    @Test
    void sizeReflectsBufferedFrames() throws InterruptedException {
        var pipe = new AudioPipe();
        assertEquals(0, pipe.size());
        pipe.sink().write(new AudioFrame(new short[]{0}, 0L));
        assertEquals(1, pipe.size());
    }

    /**
     * Constructor rejects a non-positive capacity.
     */
    @Test
    void rejectsNonPositiveCapacity() {
        assertThrows(IllegalArgumentException.class, () -> new AudioPipe(0));
        assertThrows(IllegalArgumentException.class, () -> new AudioPipe(-1));
    }
}
