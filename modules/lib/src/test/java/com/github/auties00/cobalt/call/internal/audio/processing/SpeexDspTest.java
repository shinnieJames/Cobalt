package com.github.auties00.cobalt.call.internal.audio.processing;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Smoke tests for the speexdsp FFM bindings. The tests fail loudly
 * if libspeexdsp is not loadable on the running platform —
 * silently skipping would let a broken native bundle ship green.
 */
public class SpeexDspTest {

    /**
     * Constructs and tears down both the canceller and preprocessor
     * — verifies the FFM linker resolves all the expected symbols
     * and the C state allocators succeed.
     */
    @Test
    public void initAndDestroyRoundTrip() {
        try (var aec = new EchoCanceller(160, 1600, 16000);
             var pp = new AudioPreprocessor(160, 16000)) {
            assertEquals(160, aec.frameSize());
            assertEquals(160, pp.frameSize());
            pp.linkEchoState(aec);
        }
    }

    /**
     * Feeds a noisy 1 kHz sine through the canceller with a known
     * far-end signal — the AEC's adaptive filter should converge
     * over a few seconds. We don't assert a specific SNR
     * improvement here (Speex AEC takes time to adapt, and exact
     * dB is environment-dependent), only that the call returns the
     * expected frame size and doesn't crash.
     */
    @Test
    public void cancelReturnsFrameSizedOutput() {
        try (var aec = new EchoCanceller(160, 1600, 16000)) {
            var mic = new short[160];
            var far = new short[160];
            // Some non-zero content
            for (var i = 0; i < 160; i++) {
                mic[i] = (short) (Math.sin(i * 2 * Math.PI * 1000.0 / 16000) * 0x2000);
                far[i] = (short) (Math.sin(i * 2 * Math.PI * 800.0 / 16000) * 0x1000);
            }
            // Run several frames so the filter starts adapting
            short[] out = null;
            for (var i = 0; i < 10; i++) {
                out = aec.cancel(mic, far);
            }
            assertEquals(160, out.length);
        }
    }

    /**
     * Runs the preprocessor with denoise, AGC, and VAD all enabled
     * — exercises the full ctl path.
     */
    @Test
    public void preprocessRunsWithAllFeaturesEnabled() {
        try (var pp = new AudioPreprocessor(160, 16000)) {
            pp.setDenoise(true);
            pp.setAgc(true);
            pp.setAgcTarget(8000);
            pp.setVad(true);
            pp.setNoiseSuppressDb(-20);
            // Some non-zero PCM
            var pcm = new short[160];
            for (var i = 0; i < 160; i++) {
                pcm[i] = (short) (Math.sin(i * 2 * Math.PI * 1000.0 / 16000) * 0x4000);
            }
            // Run several frames; voice should be detected
            var voiceFrames = 0;
            for (var i = 0; i < 50; i++) {
                if (pp.process(pcm.clone())) voiceFrames++;
            }
            assertTrue(voiceFrames > 25,
                    "VAD should fire on sine input most of the time, got " + voiceFrames + "/50");
        }
    }

    /**
     * Constructor argument validation — illegal values should throw
     * before any native call.
     */
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
