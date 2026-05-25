package com.github.auties00.cobalt.call.filter;

import com.github.auties00.cobalt.call.frame.audio.AudioFrame;
import com.github.auties00.cobalt.call.frame.audio.AudioSink;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Covers {@link ResamplingAudioSink} forwarding to a downstream {@link AudioSink}: equal in and out
 * rates pass frames through unchanged, a 16 kHz to 8 kHz drop halves the sample count, and the
 * constructor enforces a non-null downstream and positive rates.
 */
class ResamplingAudioSinkTest {
    @Test
    @DisplayName("Pass-through when input rate equals output rate")
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

    @Test
    @DisplayName("16 -> 8 kHz halves the output sample count")
    void downsample16To8() throws InterruptedException {
        var captured = new ArrayList<AudioFrame>();
        AudioSink downstream = captured::add;
        var sink = new ResamplingAudioSink(downstream, 16_000, 8_000);
        sink.write(new AudioFrame(new short[160], 0L));
        assertEquals(1, captured.size());
        assertEquals(80, captured.getFirst().pcm().length);
    }

    @Test
    @DisplayName("Constructor rejects null and non-positive arguments")
    void rejectsBadArgs() {
        AudioSink any = f -> {
        };
        assertThrows(NullPointerException.class, () -> new ResamplingAudioSink(null, 16_000, 8_000));
        assertThrows(IllegalArgumentException.class, () -> new ResamplingAudioSink(any, 0, 8_000));
        assertThrows(IllegalArgumentException.class, () -> new ResamplingAudioSink(any, 16_000, 0));
    }
}
