package com.github.auties00.cobalt.call.filter;

import com.github.auties00.cobalt.call.frame.audio.AudioFrame;
import com.github.auties00.cobalt.call.frame.audio.AudioSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Covers {@link ResamplingAudioSource} pulling from a delegate {@link AudioSource}: equal in and out
 * rates pass through, a 48 kHz to 16 kHz drop yields fixed-size frames with a pts that advances by
 * the configured frame duration, the source exhausts once the delegate ends and its buffer drains,
 * and the constructor enforces a non-null delegate with positive rates and frame size.
 * {@code constantFrameSource} emits a fixed frame for a bounded number of pulls then ends the stream.
 */
class ResamplingAudioSourceTest {
    @Test
    @DisplayName("Pass-through when input rate equals output rate")
    void passThroughWhenRatesMatch() throws InterruptedException {
        var src = new ResamplingAudioSource(constantFrameSource(new short[160], 1), 16_000, 16_000, 160);
        var frame = src.next();
        assertNotNull(frame);
        assertEquals(160, frame.pcm().length);
    }

    @Test
    @DisplayName("48 kHz -> 16 kHz: fixed-size frames with pts advancing by the frame duration")
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

    @Test
    @DisplayName("Returns null after the delegate exhausts and the buffer is drained")
    void exhaustsAfterDelegate() throws InterruptedException {
        var calls = new AtomicInteger();
        AudioSource inner = () -> calls.getAndIncrement() < 1
                ? new AudioFrame(new short[160], 0L)
                : null;
        var src = new ResamplingAudioSource(inner, 16_000, 16_000, 160);
        assertNotNull(src.next());
        assertNull(src.next());
    }

    @Test
    @DisplayName("Constructor rejects null and non-positive arguments")
    void rejectsBadArgs() {
        AudioSource any = () -> null;
        assertThrows(NullPointerException.class, () -> new ResamplingAudioSource(null, 16_000, 16_000, 160));
        assertThrows(IllegalArgumentException.class, () -> new ResamplingAudioSource(any, 0, 16_000, 160));
        assertThrows(IllegalArgumentException.class, () -> new ResamplingAudioSource(any, 16_000, 0, 160));
        assertThrows(IllegalArgumentException.class, () -> new ResamplingAudioSource(any, 16_000, 16_000, 0));
    }

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
