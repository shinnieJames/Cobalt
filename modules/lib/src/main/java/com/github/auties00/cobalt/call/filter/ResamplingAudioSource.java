package com.github.auties00.cobalt.call.filter;

import com.github.auties00.cobalt.call.frame.audio.AudioFrame;
import com.github.auties00.cobalt.call.frame.audio.AudioSource;

import java.util.Objects;

/**
 * Resamples a wrapped {@link AudioSource} from one PCM rate to another and emits fixed-size frames.
 *
 * <p>A typical use is converting 48 kHz microphone input to the 16 kHz call profile. The wrapper
 * pulls frames from the delegate at the input rate, buffers them across calls, and emits frames of a
 * constant sample count ({@link #outFrameSize}) at the output rate, so the output cadence matches
 * the call encoder regardless of the delegate's frame sizes. Output frames carry a monotonic
 * presentation timestamp advanced by the per-frame duration. When the delegate signals end-of-stream
 * by returning {@code null}, the wrapper emits any remaining buffered samples as one final frame and
 * then returns {@code null} itself.
 *
 * @implNote This implementation resamples by per-sample linear interpolation, the pure-Java
 * fallback. The interpolation is exact for the integer downsampling ratios Cobalt hits in practice
 * (48000/16000 = 3, 44100/22050 = 2, 48000/8000 = 6).
 */
public final class ResamplingAudioSource implements AudioSource {
    /**
     * Wrapped source emitting frames at {@link #inSampleRate}.
     */
    private final AudioSource delegate;

    /**
     * Sample rate, in hertz, of the frames pulled from the delegate.
     */
    private final int inSampleRate;

    /**
     * Sample rate, in hertz, of the frames emitted by this wrapper.
     */
    private final int outSampleRate;

    /**
     * Number of samples in each frame emitted by this wrapper.
     */
    private final int outFrameSize;

    /**
     * Duration, in milliseconds, of each emitted frame, derived from {@link #outFrameSize} and
     * {@link #outSampleRate}.
     */
    private final long outFrameDurationMs;

    /**
     * Samples pulled from the delegate that have not yet been resampled.
     */
    private short[] inBuffer = new short[0];

    /**
     * Index into {@link #inBuffer} of the next unread sample.
     */
    private int inBufferPos;

    /**
     * Presentation timestamp, in milliseconds, stamped on the next emitted frame.
     */
    private long ptsMs;

    /**
     * Whether the delegate has returned {@code null}, so that this wrapper exhausts after emitting
     * one final partial frame.
     */
    private boolean delegateExhausted;

    /**
     * Constructs a resampling source wrapping {@code delegate}.
     *
     * @param delegate      the source to resample
     * @param inSampleRate  the sample rate, in hertz, of the delegate's frames
     * @param outSampleRate the sample rate, in hertz, of the emitted frames
     * @param outFrameSize  the number of samples per emitted frame
     * @throws NullPointerException     if {@code delegate} is {@code null}
     * @throws IllegalArgumentException if {@code inSampleRate}, {@code outSampleRate}, or
     *                                  {@code outFrameSize} is not positive
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

    /**
     * {@inheritDoc}
     *
     * @implNote This implementation pulls frames from the delegate until enough input samples are
     * buffered to produce one {@link #outFrameSize}-sample output frame, then fills the output by
     * per-sample linear interpolation between the two nearest input samples, clamping to the signed
     * 16-bit range. It advances {@link #inBufferPos} by the consumed input span, compacts the buffer
     * via {@link #compactInputBuffer()}, and stamps the frame with the running {@link #ptsMs}
     * advanced by {@link #outFrameDurationMs}. Once the delegate has returned {@code null}, it emits
     * the remaining buffered samples once and then returns {@code null}.
     */
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
     * Appends new delegate samples to the input buffer, preserving any trailing unread samples.
     *
     * <p>The unread tail of {@link #inBuffer} is copied to the head of a new array, {@code more} is
     * appended after it, and {@link #inBufferPos} is reset to {@code 0}.
     *
     * @param more the newly pulled samples to append
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
     * Drops consumed samples from the head of the input buffer once more than half of it is
     * consumed.
     *
     * <p>Compaction is deferred until {@link #inBufferPos} exceeds half the buffer length so that
     * reallocation is amortised rather than performed on every frame.
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
