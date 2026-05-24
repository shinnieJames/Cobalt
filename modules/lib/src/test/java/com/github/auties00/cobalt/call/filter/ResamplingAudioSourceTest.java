package com.github.auties00.cobalt.call.filter;

import com.github.auties00.cobalt.call.frame.audio.AudioFrame;
import com.github.auties00.cobalt.call.frame.audio.AudioSource;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests for {@link ResamplingAudioSource}.
 */
class ResamplingAudioSourceTest {
    /**
     * Pass-through when input rate equals output rate.
     */
    @Test
    void passThroughWhenRatesMatch() throws InterruptedException {
        var src = new ResamplingAudioSource(constantFrameSource(new short[160], 1), 16_000, 16_000, 160);
        var frame = src.next();
        assertNotNull(frame);
        assertEquals(160, frame.pcm().length);
    }

    /**
     * 48 kHz → 16 kHz produces frames of the right size and the
     * output pts increments by the configured frame duration.
     */
    @Test
    void downsample48to16() throws InterruptedException {
        var src = new ResamplingAudioSource(constantFrameSource(new short[480], 10), 48_000, 16_000, 160);
        var first = src.next();
        var second = src.next();
        assertNotNull(first);
        assertNotNull(second);
        assertEquals(160, first.pcm().length);
        assertEquals(160, second.pcm().length);
        // 160 samples / 16 kHz = 10 ms
        assertEquals(10L, second.ptsMs() - first.ptsMs());
    }

    /**
     * Returns null after the delegate exhausts and the buffer is
     * drained.
     */
    @Test
    void exhaustsAfterDelegate() throws InterruptedException {
        var calls = new AtomicInteger();
        AudioSource inner = () -> calls.getAndIncrement() < 1
                ? new AudioFrame(new short[160], 0L)
                : null;
        var src = new ResamplingAudioSource(inner, 16_000, 16_000, 160);
        assertNotNull(src.next());
        assertNull(src.next());
    }

    /**
     * Constructor rejects null and non-positive arguments.
     */
    @Test
    void rejectsBadArgs() {
        AudioSource any = () -> null;
        assertThrows(NullPointerException.class, () -> new ResamplingAudioSource(null, 16_000, 16_000, 160));
        assertThrows(IllegalArgumentException.class, () -> new ResamplingAudioSource(any, 0, 16_000, 160));
        assertThrows(IllegalArgumentException.class, () -> new ResamplingAudioSource(any, 16_000, 0, 160));
        assertThrows(IllegalArgumentException.class, () -> new ResamplingAudioSource(any, 16_000, 16_000, 0));
    }

    /**
     * Returns an audio source that emits the given pcm payload
     * for {@code count} calls then null.
     *
     * @param pcm   payload
     * @param count emit count
     * @return the source
     */
    private static AudioSource constantFrameSource(short[] pcm, int count) {
        var calls = new AtomicInteger();
        return () -> {
            if (calls.getAndIncrement() < count) {
                return new AudioFrame(pcm.clone(), 0L);
            }
            return null;
        };
    }
}
