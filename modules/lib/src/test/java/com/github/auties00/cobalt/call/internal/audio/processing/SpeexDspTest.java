package com.github.auties00.cobalt.call.internal.audio.processing;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Smoke tests for the speexdsp FFM bindings covering the echo canceller and
 * audio preprocessor. The tests deliberately do not gate on native availability:
 * if libspeexdsp is not loadable on the running platform the resulting linker
 * error propagates and fails the build rather than skipping green.
 */
public class SpeexDspTest {

    @Test
    public void initAndDestroyRoundTrip() {
        try (var aec = new EchoCanceller(160, 1600, 16000);
             var pp = new AudioPreprocessor(160, 16000)) {
            assertEquals(160, aec.frameSize());
            assertEquals(160, pp.frameSize());
            pp.linkEchoState(aec);
        }
    }

    @Test
    public void cancelReturnsFrameSizedOutput() {
        try (var aec = new EchoCanceller(160, 1600, 16000)) {
            var mic = new short[160];
            var far = new short[160];
            for (var i = 0; i < 160; i++) {
                mic[i] = (short) (Math.sin(i * 2 * Math.PI * 1000.0 / 16000) * 0x2000);
                far[i] = (short) (Math.sin(i * 2 * Math.PI * 800.0 / 16000) * 0x1000);
            }
            // Several frames let the adaptive filter start converging; no SNR
            // assertion since Speex AEC adapts slowly and the exact dB gain is
            // environment-dependent
            short[] out = null;
            for (var i = 0; i < 10; i++) {
                out = aec.cancel(mic, far);
            }
            assertEquals(160, out.length);
        }
    }

    @Test
    public void preprocessRunsWithAllFeaturesEnabled() {
        try (var pp = new AudioPreprocessor(160, 16000)) {
            pp.setDenoise(true);
            pp.setAgc(true);
            pp.setAgcTarget(8000);
            pp.setVad(true);
            pp.setNoiseSuppressDb(-20);
            var pcm = new short[160];
            for (var i = 0; i < 160; i++) {
                pcm[i] = (short) (Math.sin(i * 2 * Math.PI * 1000.0 / 16000) * 0x4000);
            }
            var voiceFrames = 0;
            for (var i = 0; i < 50; i++) {
                if (pp.process(pcm.clone())) voiceFrames++;
            }
            assertTrue(voiceFrames > 25,
                    "VAD should fire on sine input most of the time, got " + voiceFrames + "/50");
        }
    }

    @Test
    public void rejectsNegativeArgs() {
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> new EchoCanceller(0, 1600, 16000));
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> new EchoCanceller(160, 0, 16000));
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> new AudioPreprocessor(0, 16000));
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> new AudioPreprocessor(160, 0));
    }
}
