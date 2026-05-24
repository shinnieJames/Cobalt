package com.github.auties00.cobalt.media.transcode.audio;

/**
 * Builds the bucketed-amplitude waveform attached to voice-note messages.
 *
 * @apiNote
 * Called by the voice-note transcoder pipeline after decoding the source
 * audio into 16-bit signed PCM at 16 kHz mono and before encoding the Opus
 * OGG payload that ships to the WhatsApp CDN. The resulting bytes go on
 * {@link com.github.auties00.cobalt.model.message.media.AudioMessage#waveform()}.
 * Each output byte is a value in {@code [0, 100]} representing the
 * percentage of the loudest bucket's RMS amplitude that the corresponding
 * input slice reaches; the WhatsApp clients use these to render the static
 * pre-playback waveform under the play button.
 *
 * @implNote
 * This implementation buckets the samples into {@link #DEFAULT_BUCKETS}
 * equally-sized slices (the convention iOS and Android use for voice
 * notes), computes the RMS amplitude per bucket, divides by the maximum
 * bucket RMS, scales to {@code [0, 100]}, and rounds to the nearest integer.
 * Empty or silent inputs yield an all-zero waveform. The WA Web
 * {@code WAPttWaveformGenerateHeightJitterValues} helpers are a separate
 * concern (playback-time visual dithering for rendering), not the bytes
 * shipped on the wire, so they are not invoked here.
 *
 * @see #generate(short[])
 * @see #generate(short[], int)
 */
final class WaveformGenerator {
    /**
     * Number of buckets in the default waveform. Matches the convention
     * used by WhatsApp's mobile clients when generating voice-note
     * waveforms.
     */
    public static final int DEFAULT_BUCKETS = 64;

    /**
     * Upper bound on the quantised amplitude of a single bucket. Mirrors
     * the {@code 0..100} percentage range the WhatsApp client UI renders.
     */
    private static final int MAX_QUANTISED_VALUE = 100;

    /**
     * Hidden constructor; the type exposes only static entry points.
     */
    private WaveformGenerator() {
        throw new UnsupportedOperationException();
    }

    /**
     * Returns a {@link #DEFAULT_BUCKETS}-byte waveform built from the given
     * 16-bit signed PCM samples.
     *
     * @apiNote
     * The voice-note pipeline calls this after resampling the source audio
     * down to 16 kHz mono and converting to {@code s16le}; the resulting
     * byte array is what
     * {@link com.github.auties00.cobalt.model.message.media.AudioMessage#waveform()}
     * exposes on the outgoing protobuf.
     *
     * @param samples the 16-bit signed PCM samples; may be empty
     * @return the {@link #DEFAULT_BUCKETS}-byte waveform; every entry is in
     *         {@code [0, 100]}
     */
    public static byte[] generate(short[] samples) {
        return generate(samples, DEFAULT_BUCKETS);
    }

    /**
     * Returns an {@code N}-byte waveform built from the given 16-bit signed
     * PCM samples.
     *
     * @apiNote
     * Most callers want {@link #DEFAULT_BUCKETS} buckets; this overload is
     * available for testing or for embedders that explicitly need a
     * different waveform length.
     *
     * @param samples the 16-bit signed PCM samples; may be empty
     * @param buckets the number of buckets to emit; must be positive
     * @return a fresh byte array of length {@code buckets}; every entry is
     *         in {@code [0, 100]}
     * @throws IllegalArgumentException if {@code buckets} is not positive
     */
    public static byte[] generate(short[] samples, int buckets) {
        if (buckets <= 0) {
            throw new IllegalArgumentException("buckets must be positive");
        }
        var waveform = new byte[buckets];
        if (samples == null || samples.length == 0) {
            return waveform;
        }
        var rmsPerBucket = new double[buckets];
        double maxRms = 0;
        for (var i = 0; i < buckets; i++) {
            var start = (int) ((long) i * samples.length / buckets);
            var end = (int) ((long) (i + 1) * samples.length / buckets);
            if (end <= start) {
                end = Math.min(start + 1, samples.length);
            }
            double sumSquares = 0;
            var count = end - start;
            for (var j = start; j < end; j++) {
                double v = samples[j];
                sumSquares += v * v;
            }
            var rms = Math.sqrt(sumSquares / count);
            rmsPerBucket[i] = rms;
            if (rms > maxRms) {
                maxRms = rms;
            }
        }
        if (maxRms == 0) {
            return waveform;
        }
        for (var i = 0; i < buckets; i++) {
            var quantised = (int) Math.round(rmsPerBucket[i] / maxRms * MAX_QUANTISED_VALUE);
            if (quantised < 0) {
                quantised = 0;
            } else if (quantised > MAX_QUANTISED_VALUE) {
                quantised = MAX_QUANTISED_VALUE;
            }
            waveform[i] = (byte) quantised;
        }
        return waveform;
    }
}
