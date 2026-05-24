package com.github.auties00.cobalt.call.filter;

import com.github.auties00.cobalt.call.frame.audio.AudioFrame;
import com.github.auties00.cobalt.call.frame.audio.AudioSink;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests for {@link ResamplingAudioSink}.
 */
class ResamplingAudioSinkTest {
    /**
     * Pass-through when input rate equals output rate.
     */
    @Test
    void passThroughWhenRatesMatch() throws InterruptedException {
        var captured = new ArrayList<AudioFrame>();
        AudioSink downstream = captured::add;
        var sink = new ResamplingAudioSink(downstream, 16_000, 16_000);
        var pcm = new short[]{1, 2, 3};
        sink.write(new AudioFrame(pcm, 7L));
        assertEquals(1, captured.size());
        assertArrayEquals(pcm, captured.getFirst().pcm());
        assertEquals(7L, captured.getFirst().ptsMs());
    }

    /**
     * 16 → 8 kHz halves the output sample count.
     */
    @Test
    void downsample16To8() throws InterruptedException {
        var captured = new ArrayList<AudioFrame>();
        AudioSink downstream = captured::add;
        var sink = new ResamplingAudioSink(downstream, 16_000, 8_000);
        sink.write(new AudioFrame(new short[160], 0L));
        assertEquals(1, captured.size());
        assertEquals(80, captured.getFirst().pcm().length);
    }

    /**
     * Constructor rejects null + non-positive arguments.
     */
    @Test
    void rejectsBadArgs() {
        AudioSink any = f -> {
        };
        assertThrows(NullPointerException.class, () -> new ResamplingAudioSink(null, 16_000, 8_000));
        assertThrows(IllegalArgumentException.class, () -> new ResamplingAudioSink(any, 0, 8_000));
        assertThrows(IllegalArgumentException.class, () -> new ResamplingAudioSink(any, 16_000, 0));
    }
}
