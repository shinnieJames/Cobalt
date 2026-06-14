package com.github.auties00.cobalt.call.stream.capture;

import com.github.auties00.cobalt.call.stream.AudioFrame;
import com.github.auties00.cobalt.call.stream.AudioOutputStream;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Transmits a synthesised sine wave, or a sum of two sines, as the local audio of a call.
 *
 * <p>This is the device-backed {@link AudioOutputStream} returned by
 * {@link AudioOutputStream#tone(double)}. Each {@link #take()} renders the next slice of the waveform
 * into a fresh {@link AudioFrame}. Phase is derived from a monotonic frame index rather than reset per
 * frame, so concatenating the emitted frames reproduces one continuous, click-free waveform. A single
 * frequency produces a pure tone; a second non-zero frequency sums two sines, which is how dual-tone
 * signals such as DTMF are built. The stream can also follow an on/off cadence: within each cadence
 * cycle it emits the tone for the configured number of "on" frames and digital silence for the
 * remainder, repeating indefinitely. A stream configured with {@code Long.MAX_VALUE} for both the on
 * and cadence frame counts plays continuously.
 *
 * <p>The stream never ends on its own; like other synthetic sources it produces frames until the call
 * engine shuts it down. Use {@link #sine(int)} for a single test tone, {@link #ringback()} for a
 * Western European ringback while a call is being set up, and {@link #dtmf(char)} to emit a single dial
 * digit.
 */
public final class ToneAudioOutputStream extends AudioOutputStream {
    /**
     * Holds the synthesis sample rate, in Hz, fixed to the call wire profile.
     *
     * @implNote This implementation hard-codes 16000 rather than making it configurable, because the
     * generated samples feed the call encoder directly and that is the only rate it consumes.
     */
    public static final int SAMPLE_RATE = 16_000;

    /**
     * Holds the default sample count per emitted frame.
     *
     * @implNote This implementation uses 160, which is 10 ms at {@link #SAMPLE_RATE}.
     */
    public static final int DEFAULT_FRAME_SIZE = 160;

    /**
     * Holds the default duration, in milliseconds, of each emitted frame.
     */
    public static final long DEFAULT_FRAME_DURATION_MS = 10;

    /**
     * Holds the peak amplitude of the synthesised waveform.
     *
     * @implNote This implementation uses {@code 0x4000} (half of the signed 16-bit full scale of
     * {@code 0x8000}) so that summing two sines for a dual-tone signal cannot exceed full scale and
     * clip.
     */
    private static final double AMPLITUDE = 0x4000;

    /**
     * Holds the primary tone frequency, in Hz.
     */
    private final double freq1Hz;

    /**
     * Holds the secondary tone frequency, in Hz, or {@code 0} for a single sine.
     */
    private final double freq2Hz;

    /**
     * Holds the total number of frames in one on/off cadence cycle, or {@code Long.MAX_VALUE} for a
     * continuous tone.
     */
    private final long cadenceFrames;

    /**
     * Holds the number of frames the cadence spends in the "on" phase.
     *
     * <p>Frames whose index within a cadence cycle is at or beyond this value, and before
     * {@link #cadenceFrames}, are emitted as silence.
     */
    private final long onFrames;

    /**
     * Holds the number of samples in each emitted frame.
     */
    private final int frameSize;

    /**
     * Holds the duration of each emitted frame, in milliseconds, by which each frame's presentation
     * timestamp advances.
     */
    private final long frameDurationMs;

    /**
     * Holds the monotonic frame counter that drives both the waveform phase and the on/off cadence,
     * advanced atomically so the stream may be drained from any thread.
     */
    private final AtomicLong frameIndex = new AtomicLong();

    /**
     * Holds the presentation timestamp, in milliseconds, of the next frame, advanced atomically so the
     * stream may be drained from any thread.
     */
    private final AtomicLong ptsMs = new AtomicLong();

    /**
     * Constructs a continuous single-tone stream at the given frequency with the default frame
     * geometry.
     *
     * <p>This is the entry point for {@link AudioOutputStream#tone(double)}; it produces an endless pure
     * sine at {@code frequencyHz} truncated to an integer number of hertz.
     *
     * @param frequencyHz the tone frequency in Hz; must be greater than {@code 0}
     * @throws IllegalArgumentException if {@code frequencyHz} is not greater than {@code 0}
     */
    public ToneAudioOutputStream(double frequencyHz) {
        this((int) frequencyHz, 0,
                Long.MAX_VALUE, Long.MAX_VALUE,
                DEFAULT_FRAME_SIZE, DEFAULT_FRAME_DURATION_MS);
    }

    /**
     * Constructs a tone stream from explicit frequencies, cadence, and frame geometry.
     *
     * @param freq1Hz         the primary frequency in Hz; must be greater than {@code 0}
     * @param freq2Hz         the secondary frequency in Hz, or {@code 0} for a single sine
     * @param onFrames        the number of frames in the "on" phase per cadence cycle, or
     *                        {@code Long.MAX_VALUE} for a continuous tone
     * @param cadenceFrames   the total number of frames in one on plus off cycle, or
     *                        {@code Long.MAX_VALUE} for a continuous tone
     * @param frameSize       the number of samples per emitted frame
     * @param frameDurationMs the duration of each emitted frame in milliseconds
     * @throws IllegalArgumentException if {@code freq1Hz} is not greater than {@code 0}, if
     *                                  {@code freq2Hz} is negative, if {@code onFrames} is less than
     *                                  {@code 1}, if {@code cadenceFrames} is less than
     *                                  {@code onFrames}, or if {@code frameSize} or
     *                                  {@code frameDurationMs} is less than {@code 1}
     */
    public ToneAudioOutputStream(double freq1Hz, double freq2Hz, long onFrames, long cadenceFrames,
                                 int frameSize, long frameDurationMs) {
        if (freq1Hz <= 0) {
            throw new IllegalArgumentException("freq1Hz must be > 0");
        }
        if (freq2Hz < 0) {
            throw new IllegalArgumentException("freq2Hz must be >= 0");
        }
        if (onFrames < 1) {
            throw new IllegalArgumentException("onFrames must be >= 1");
        }
        if (cadenceFrames < onFrames) {
            throw new IllegalArgumentException("cadenceFrames must be >= onFrames");
        }
        if (frameSize < 1) {
            throw new IllegalArgumentException("frameSize must be >= 1");
        }
        if (frameDurationMs < 1) {
            throw new IllegalArgumentException("frameDurationMs must be >= 1");
        }
        this.freq1Hz = freq1Hz;
        this.freq2Hz = freq2Hz;
        this.onFrames = onFrames;
        this.cadenceFrames = cadenceFrames;
        this.frameSize = frameSize;
        this.frameDurationMs = frameDurationMs;
    }

    /**
     * Returns a continuous single-tone stream at the given frequency with the default frame geometry.
     *
     * @param frequencyHz the tone frequency in Hz
     * @return a continuous single-tone stream
     * @throws IllegalArgumentException if {@code frequencyHz} is not greater than {@code 0}
     */
    public static ToneAudioOutputStream sine(int frequencyHz) {
        return new ToneAudioOutputStream(frequencyHz, 0,
                Long.MAX_VALUE, Long.MAX_VALUE,
                DEFAULT_FRAME_SIZE, DEFAULT_FRAME_DURATION_MS);
    }

    /**
     * Returns a Western European ringback tone stream.
     *
     * <p>Emits a 425 Hz tone for one second, then silence for four seconds, repeating the five-second
     * cadence indefinitely.
     *
     * @return a ringback tone stream
     */
    public static ToneAudioOutputStream ringback() {
        var onFrames = 1000 / DEFAULT_FRAME_DURATION_MS;
        var cadenceFrames = 5000 / DEFAULT_FRAME_DURATION_MS;
        return new ToneAudioOutputStream(425, 0, onFrames, cadenceFrames,
                DEFAULT_FRAME_SIZE, DEFAULT_FRAME_DURATION_MS);
    }

    /**
     * Returns a continuous DTMF tone stream for the given dial digit.
     *
     * <p>Maps the digit to its standard low-group (row) and high-group (column) frequencies and
     * synthesises both summed continuously.
     *
     * @param digit one of the DTMF symbols {@code 0} through {@code 9}, {@code *}, {@code #}, or
     *              {@code A} through {@code D}
     * @return a continuous DTMF tone stream
     * @throws IllegalArgumentException if {@code digit} is not a valid DTMF symbol
     */
    public static ToneAudioOutputStream dtmf(char digit) {
        double row, col;
        switch (digit) {
            case '1' -> { row = 697; col = 1209; }
            case '2' -> { row = 697; col = 1336; }
            case '3' -> { row = 697; col = 1477; }
            case 'A' -> { row = 697; col = 1633; }
            case '4' -> { row = 770; col = 1209; }
            case '5' -> { row = 770; col = 1336; }
            case '6' -> { row = 770; col = 1477; }
            case 'B' -> { row = 770; col = 1633; }
            case '7' -> { row = 852; col = 1209; }
            case '8' -> { row = 852; col = 1336; }
            case '9' -> { row = 852; col = 1477; }
            case 'C' -> { row = 852; col = 1633; }
            case '*' -> { row = 941; col = 1209; }
            case '0' -> { row = 941; col = 1336; }
            case '#' -> { row = 941; col = 1477; }
            case 'D' -> { row = 941; col = 1633; }
            default -> throw new IllegalArgumentException(
                    "not a DTMF digit: '" + digit + "'");
        }
        return new ToneAudioOutputStream(row, col,
                Long.MAX_VALUE, Long.MAX_VALUE,
                DEFAULT_FRAME_SIZE, DEFAULT_FRAME_DURATION_MS);
    }

    /**
     * {@inheritDoc}
     *
     * <p>Advances the frame counter and presentation timestamp, then renders the next slice of the
     * waveform. When the current frame index falls in the cadence's off phase the frame is silence;
     * otherwise each sample is the sine of the primary frequency, plus the secondary frequency when
     * configured, scaled to the peak amplitude and rounded to a signed 16-bit sample. Never returns
     * {@code null}, so the stream ends only when {@link #shutdown()} runs.
     *
     * @return the next synthesised frame; never {@code null}
     * @implNote This implementation returns one frame per call with no sleep: the call engine's capture
     * loop paces outbound audio to wall-clock using each frame's running presentation timestamp, so the
     * synthesised tone is transmitted at its natural rate without this stream having to sleep.
     */
    @Override
    public AudioFrame take() {
        if (closed.get()) {
            return null;
        }
        var index = frameIndex.getAndIncrement();
        var pts = ptsMs.getAndAdd(frameDurationMs);
        var inOnPhase = (index % cadenceFrames) < onFrames;
        var pcm = new short[frameSize];
        if (!inOnPhase) {
            return new AudioFrame(pcm, pts);
        }
        var sampleBase = index * frameSize;
        var w1 = 2 * Math.PI * freq1Hz / SAMPLE_RATE;
        var w2 = 2 * Math.PI * freq2Hz / SAMPLE_RATE;
        var dual = freq2Hz > 0;
        var scale = dual ? AMPLITUDE / 2 : AMPLITUDE;
        for (var i = 0; i < frameSize; i++) {
            double t = sampleBase + i;
            var sample = Math.sin(w1 * t);
            if (dual) {
                sample += Math.sin(w2 * t);
            }
            pcm[i] = (short) Math.round(sample * scale);
        }
        return new AudioFrame(pcm, pts);
    }
}
