package com.github.auties00.cobalt.call.filter;

import com.github.auties00.cobalt.call.frame.audio.AudioFrame;
import com.github.auties00.cobalt.call.frame.audio.AudioSink;

import java.util.Objects;

/**
 * Resamples frames written to this sink from one PCM rate to another before forwarding them to a
 * delegate sink.
 *
 * <p>This is the inverse of {@link ResamplingAudioSource}: it accepts frames at an input rate and
 * writes them to the wrapped sink at the output rate, for example when the call decoder emits 16 kHz
 * mono but the OS speaker wants 48 kHz. Each {@link #write(AudioFrame)} resamples one frame
 * independently and forwards a new {@link AudioFrame} carrying the original
 * {@link AudioFrame#ptsMs()}. When the input and output rates are equal, the frame is forwarded
 * unchanged. The sink keeps no state between frames; each call resamples in isolation.
 *
 * @implNote This implementation resamples by per-sample linear interpolation, the pure-Java
 * fallback used when no native resampler is available. The output sample count is rounded from
 * {@code inputSamples * outSampleRate / inSampleRate}, samples beyond the input are treated as
 * silence, and each interpolated sample is clamped to the signed 16-bit range.
 */
public final class ResamplingAudioSink implements AudioSink {
    /**
     * Wrapped sink that receives the resampled frames.
     */
    private final AudioSink delegate;

    /**
     * Sample rate, in hertz, at which frames are written to this sink.
     */
    private final int inSampleRate;

    /**
     * Sample rate, in hertz, at which frames are forwarded to the delegate.
     */
    private final int outSampleRate;

    /**
     * Constructs a resampling sink forwarding to {@code delegate}.
     *
     * @param delegate      the sink that receives resampled frames
     * @param inSampleRate  the sample rate, in hertz, of incoming frames
     * @param outSampleRate the sample rate, in hertz, expected by the delegate
     * @throws NullPointerException     if {@code delegate} is {@code null}
     * @throws IllegalArgumentException if {@code inSampleRate} or {@code outSampleRate} is not
     *                                  positive
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

    /**
     * {@inheritDoc}
     *
     * @implNote This implementation forwards the frame unchanged when the input and output rates are
     * equal. Otherwise it allocates an output buffer of
     * {@code round(in.length * outSampleRate / inSampleRate)} samples, fills each output sample by
     * linear interpolation between the two nearest input samples (reading silence past the input
     * end), clamps to the signed 16-bit range, and forwards a new {@link AudioFrame} carrying the
     * original {@link AudioFrame#ptsMs()}.
     * @throws NullPointerException if {@code frame} is {@code null}
     */
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
