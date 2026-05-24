package com.github.auties00.cobalt.call.filter;

import com.github.auties00.cobalt.call.frame.audio.AudioFrame;
import com.github.auties00.cobalt.call.frame.audio.AudioSource;

import java.util.Objects;

/**
 * Resamples a wrapped {@link AudioSource} from one PCM rate to
 * another — typically 48 kHz mic input → 16 kHz call profile —
 * using a pure-Java linear-interpolation fallback. The pure-Java
 * path is correct for the integer-ratio cases Cobalt actually hits
 * in practice (48000/16000 = 3, 44100/22050 = 2,
 * 48000/8000 = 6).
 *
 * <p>Frame size on the output side is constant
 * ({@link #outFrameSize}); the wrapper buffers across input
 * frames as needed so the output cadence matches the call's
 * encoder.
 */
public final class ResamplingAudioSource implements AudioSource {
    /**
     * Wrapped source emitting at {@link #inSampleRate}.
     */
    private final AudioSource delegate;

    /**
     * Source-side sample rate.
     */
    private final int inSampleRate;

    /**
     * Target sample rate emitted by this wrapper.
     */
    private final int outSampleRate;

    /**
     * Samples per emitted frame.
     */
    private final int outFrameSize;

    /**
     * Frame duration emitted by this wrapper, ms.
     */
    private final long outFrameDurationMs;

    /**
     * Buffered samples from the delegate that haven't been
     * resampled yet.
     */
    private short[] inBuffer = new short[0];

    /**
     * Index into {@link #inBuffer} of the next unread sample.
     */
    private int inBufferPos;

    /**
     * Monotonic output-side pts in milliseconds.
     */
    private long ptsMs;

    /**
     * Flag set when the delegate returns null — propagated on
     * the next call so the wrapper exhausts after one final
     * partial frame.
     */
    private boolean delegateExhausted;

    /**
     * Constructs a resampling wrapper.
     *
     * @param delegate      source to resample
     * @param inSampleRate  source-side sample rate
     * @param outSampleRate target-side sample rate
     * @param outFrameSize  samples per emitted frame
     * @throws NullPointerException     if {@code delegate} is null
     * @throws IllegalArgumentException if any rate or frame size
     *                                  is not positive
     */
    public ResamplingAudioSource(AudioSource delegate, int inSampleRate, int outSampleRate, int outFrameSize) {
        this.delegate = Objects.requireNonNull(delegate, "delegate cannot be null");
        if (inSampleRate <= 0) {
            throw new IllegalArgumentException("inSampleRate must be > 0");
        }
        if (outSampleRate <= 0) {
            throw new IllegalArgumentException("outSampleRate must be > 0");
        }
        if (outFrameSize <= 0) {
            throw new IllegalArgumentException("outFrameSize must be > 0");
        }
        this.inSampleRate = inSampleRate;
        this.outSampleRate = outSampleRate;
        this.outFrameSize = outFrameSize;
        this.outFrameDurationMs = 1000L * outFrameSize / outSampleRate;
    }

    @Override
    public AudioFrame next() throws InterruptedException {
        if (delegateExhausted && inBufferPos >= inBuffer.length) {
            return null;
        }
        var neededInputSamples = (int) Math.ceil((double) outFrameSize * inSampleRate / outSampleRate);
        while (inBuffer.length - inBufferPos < neededInputSamples && !delegateExhausted) {
            var more = delegate.next();
            if (more == null) {
                delegateExhausted = true;
                break;
            }
            appendInput(more.pcm());
        }
        var available = inBuffer.length - inBufferPos;
        if (available <= 0) {
            return null;
        }
        var out = new short[outFrameSize];
        for (var i = 0; i < outFrameSize; i++) {
            var inIndex = (double) i * inSampleRate / outSampleRate;
            var floor = (int) Math.floor(inIndex);
            var frac = inIndex - floor;
            var idx0 = inBufferPos + floor;
            var idx1 = idx0 + 1;
            int s0 = idx0 < inBuffer.length ? inBuffer[idx0] : 0;
            var s1 = idx1 < inBuffer.length ? inBuffer[idx1] : s0;
            var sample = s0 + frac * (s1 - s0);
            var rounded = (int) Math.round(sample);
            if (rounded > Short.MAX_VALUE) rounded = Short.MAX_VALUE;
            else if (rounded < Short.MIN_VALUE) rounded = Short.MIN_VALUE;
            out[i] = (short) rounded;
        }
        inBufferPos += (int) Math.floor((double) outFrameSize * inSampleRate / outSampleRate);
        compactInputBuffer();
        var pts = ptsMs;
        ptsMs += outFrameDurationMs;
        return new AudioFrame(out, pts);
    }

    /**
     * Appends new input samples to the buffer, reusing the
     * trailing unread region if any.
     *
     * @param more the new samples
     */
    private void appendInput(short[] more) {
        var unread = inBuffer.length - inBufferPos;
        var grown = new short[unread + more.length];
        System.arraycopy(inBuffer, inBufferPos, grown, 0, unread);
        System.arraycopy(more, 0, grown, unread, more.length);
        inBuffer = grown;
        inBufferPos = 0;
    }

    /**
     * Drops consumed samples from the head of the buffer when
     * more than half of it is consumed.
     */
    private void compactInputBuffer() {
        if (inBufferPos > inBuffer.length / 2) {
            var unread = inBuffer.length - inBufferPos;
            var compact = new short[unread];
            System.arraycopy(inBuffer, inBufferPos, compact, 0, unread);
            inBuffer = compact;
            inBufferPos = 0;
        }
    }
}
