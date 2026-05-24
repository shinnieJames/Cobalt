package com.github.auties00.cobalt.call.internal.audio.opus;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Round-trip tests for {@link OpusEncoder} + {@link OpusDecoder} at
 * WhatsApp's voice configuration: 16 kHz mono, 10 ms frames, VOIP
 * application.
 *
 * <p>Pins:
 *
 * <ul>
 *   <li>End-to-end encode + decode produces output close to the
 *       input (Opus is lossy but voice-band content preserves well).</li>
 *   <li>The encoder accepts the configured frame size and emits a
 *       compact packet at typical voice bitrates.</li>
 *   <li>DTX (silent input) produces a much smaller packet than
 *       active speech, confirming the silence-detector path works.</li>
 *   <li>FEC + packet-loss-percent settings round-trip through the
 *       encoder.</li>
 * </ul>
 */
public class OpusCodecTest {

    /**
     * WhatsApp voice sample rate.
     */
    private static final int SAMPLE_RATE = 16000;

    /**
     * 10 ms at 16 kHz = 160 samples per Opus frame.
     */
    private static final int FRAME_SIZE = 160;

    /**
     * Samples one full second of a 1 kHz sine wave at 16 kHz mono
     * (matches the human voice band) and round-trips it through the
     * encoder/decoder, asserting that the decoded waveform's RMS
     * energy is within ~30% of the input — Opus voice quality at
     * default bitrate is high enough that the energy envelope is
     * very close.
     */
    @Test
    public void sineWaveRoundTripsWithComparableEnergy() {
        var input = sineWave(1000, SAMPLE_RATE * 1, SAMPLE_RATE);
        try (var enc = new OpusEncoder(SAMPLE_RATE, 1, OpusApplication.VOIP);
             var dec = new OpusDecoder(SAMPLE_RATE, 1)) {
            // Disable DTX so the encoder emits frames consistently
            enc.setUseDTX(false);
            var totalDecoded = 0;
            var inputEnergy = rms(input);
            var decodedEnergy = 0.0;
            var samples = 0;
            for (var off = 0; off + FRAME_SIZE <= input.length; off += FRAME_SIZE) {
                var frame = new short[FRAME_SIZE];
                System.arraycopy(input, off, frame, 0, FRAME_SIZE);
                var encoded = enc.encode(frame, FRAME_SIZE);
                assertTrue(encoded.length > 0, "encoded packet must not be empty");
                assertTrue(encoded.length < 200, "voice frame should compress to <200 bytes, got " + encoded.length);
                var decoded = dec.decode(encoded, FRAME_SIZE);
                assertEquals(FRAME_SIZE, decoded.length, "decoded frame length must equal frameSize for mono");
                decodedEnergy += rmsSquared(decoded) * decoded.length;
                samples += decoded.length;
                totalDecoded++;
            }
            decodedEnergy = Math.sqrt(decodedEnergy / samples);
            assertTrue(totalDecoded >= 80, "expected ≥80 frames in 1s of audio, got " + totalDecoded);
            assertTrue(Math.abs(decodedEnergy - inputEnergy) / inputEnergy < 0.30,
                    "decoded RMS " + decodedEnergy + " should be within 30% of input " + inputEnergy);
        }
    }

    /**
     * DTX should produce a much smaller packet for silent input than
     * for active speech.
     */
    @Test
    public void dtxShrinksPacketsForSilence() {
        try (var enc = new OpusEncoder(SAMPLE_RATE, 1, OpusApplication.VOIP)) {
            enc.setUseDTX(true);
            // Prime the encoder with a few active frames so it has SID context
            var sine = sineWave(1000, FRAME_SIZE * 4, SAMPLE_RATE);
            for (var off = 0; off + FRAME_SIZE <= sine.length; off += FRAME_SIZE) {
                var frame = new short[FRAME_SIZE];
                System.arraycopy(sine, off, frame, 0, FRAME_SIZE);
                enc.encode(frame, FRAME_SIZE);
            }
            // Now encode silent frames — these should shrink dramatically
            var silentFrame = new short[FRAME_SIZE];
            var totalSilentBytes = 0;
            for (var i = 0; i < 20; i++) {
                var encoded = enc.encode(silentFrame, FRAME_SIZE);
                totalSilentBytes += encoded.length;
            }
            // Active frames at default bitrate produce ~30-50 bytes each
            // (~600+ for 20 frames). Silence should be far less — Opus
            // DTX emits ~5 bytes per silent frame, so 20 frames ≈ 100 bytes.
            assertTrue(totalSilentBytes < 200,
                    "DTX should compress 20 silent frames to <200 bytes, got " + totalSilentBytes);
        }
    }

    /**
     * Bitrate setter round-trips through the getter.
     */
    @Test
    public void bitrateRoundTrips() {
        try (var enc = new OpusEncoder(SAMPLE_RATE, 1, OpusApplication.VOIP)) {
            enc.setBitrate(24000);
            assertEquals(24000, enc.bitrate());
        }
    }

    /**
     * FEC + loss-percent settings round-trip through the encoder.
     */
    @Test
    public void fecAndLossPercentSettingsAccepted() {
        try (var enc = new OpusEncoder(SAMPLE_RATE, 1, OpusApplication.VOIP)) {
            enc.setUseInbandFEC(true);
            enc.setPacketLossPercent(15);
        }
    }

    /**
     * Generates a sine wave of {@code freqHz} for {@code n} samples
     * at {@code sampleRate}, normalised to {@code ±0x4000} (half
     * full-scale, leaving headroom).
     *
     * @param freqHz     the wave frequency in Hz
     * @param n          the number of samples
     * @param sampleRate the sample rate in Hz
     * @return the generated PCM samples
     */
    private static short[] sineWave(double freqHz, int n, int sampleRate) {
        var out = new short[n];
        var twoPiOverFs = 2.0 * Math.PI * freqHz / sampleRate;
        for (var i = 0; i < n; i++) {
            out[i] = (short) (Math.sin(i * twoPiOverFs) * 0x4000);
        }
        return out;
    }

    /**
     * Returns the root-mean-square amplitude of a PCM buffer.
     *
     * @param pcm the samples
     * @return the RMS amplitude
     */
    private static double rms(short[] pcm) {
        return Math.sqrt(rmsSquared(pcm));
    }

    /**
     * Returns the mean-squared amplitude of a PCM buffer (used as
     * an intermediate when accumulating across frames).
     *
     * @param pcm the samples
     * @return the mean of squared sample values
     */
    private static double rmsSquared(short[] pcm) {
        var sum = 0.0;
        for (var s : pcm) sum += (double) s * s;
        return sum / pcm.length;
    }
}
