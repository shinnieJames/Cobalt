package com.github.auties00.cobalt.call.filter;

import com.github.auties00.cobalt.call.frame.audio.AudioFrame;
import com.github.auties00.cobalt.call.frame.audio.AudioSink;

import java.util.Objects;

/**
 * Resamples frames written into this sink (at one rate) and
 * forwards them to a delegate sink at another rate — the inverse
 * of {@link ResamplingAudioSource}, used when the call decoder emits
 * 16 kHz mono but the OS speaker wants 48 kHz.
 *
 * <p>Pure-Java linear-interpolation fallback.
 */
public final class ResamplingAudioSink implements AudioSink {
    /**
     * Wrapped sink the resampled frames are written into.
     */
    private final AudioSink delegate;

    /**
     * Source-side sample rate frames arrive at.
     */
    private final int inSampleRate;

    /**
     * Target sample rate the wrapped sink expects.
     */
    private final int outSampleRate;

    /**
     * Constructs a resampling sink.
     *
     * @param delegate      sink to forward to
     * @param inSampleRate  source-side rate
     * @param outSampleRate target rate
     */
    public ResamplingAudioSink(AudioSink delegate, int inSampleRate, int outSampleRate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate cannot be null");
        if (inSampleRate <= 0) {
            throw new IllegalArgumentException("inSampleRate must be > 0");
        }
        if (outSampleRate <= 0) {
            throw new IllegalArgumentException("outSampleRate must be > 0");
        }
        this.inSampleRate = inSampleRate;
        this.outSampleRate = outSampleRate;
    }

    @Override
    public void write(AudioFrame frame) throws InterruptedException {
        Objects.requireNonNull(frame, "frame cannot be null");
        if (inSampleRate == outSampleRate) {
            delegate.write(frame);
            return;
        }
        var in = frame.pcm();
        var outLen = (int) Math.round((long) in.length * outSampleRate / inSampleRate);
        var out = new short[outLen];
        for (var i = 0; i < outLen; i++) {
            var inIndex = (double) i * inSampleRate / outSampleRate;
            var floor = (int) Math.floor(inIndex);
            var frac = inIndex - floor;
            int s0 = floor < in.length ? in[floor] : 0;
            var s1 = floor + 1 < in.length ? in[floor + 1] : s0;
            var sample = s0 + frac * (s1 - s0);
            var rounded = (int) Math.round(sample);
            if (rounded > Short.MAX_VALUE) rounded = Short.MAX_VALUE;
            else if (rounded < Short.MIN_VALUE) rounded = Short.MIN_VALUE;
            out[i] = (short) rounded;
        }
        delegate.write(new AudioFrame(out, frame.ptsMs()));
    }
}
