package com.github.auties00.cobalt.call.source;

import com.github.auties00.cobalt.call.frame.audio.AudioFrame;
import com.github.auties00.cobalt.call.frame.audio.AudioSource;

import java.util.concurrent.atomic.AtomicLong;

/**
 * An {@link AudioSource} that synthesises a sine wave (or a sum
 * of two sines for DTMF tones) at the call's PCM rate — the
 * batteries-included answer for ringback, hold music, DTMF
 * emission, and acoustic test signals.
 *
 * <p>Every {@link #next()} returns a fresh frame whose phase
 * advances continuously, so concatenating frames produces a
 * click-free waveform.
 *
 * <h2>Common factories</h2>
 *
 * <ul>
 *   <li>{@link #sine(int)} — single tone</li>
 *   <li>{@link #ringback()} — Western European ringback (425 Hz,
 *       1 s on / 4 s off)</li>
 *   <li>{@link #dtmf(char)} — DTMF tone for one of
 *       {@code 0-9 * # A B C D}</li>
 * </ul>
 */
public final class ToneGenerator implements AudioSource {
    /**
     * Sample rate of the WhatsApp wire profile, hard-coded.
     */
    public static final int SAMPLE_RATE = 16_000;

    /**
     * Default samples per emitted frame (10 ms at 16 kHz).
     */
    public static final int DEFAULT_FRAME_SIZE = 160;

    /**
     * Default emitted-frame duration, ms.
     */
    public static final long DEFAULT_FRAME_DURATION_MS = 10;

    /**
     * Peak amplitude of the synthesised waveform — half full-scale
     * so two summed sines (DTMF) don't clip.
     */
    private static final double AMPLITUDE = 0x4000;

    /**
     * Primary tone frequency in Hz.
     */
    private final double freq1Hz;

    /**
     * Secondary tone frequency in Hz, or {@code 0} for a single
     * sine.
     */
    private final double freq2Hz;

    /**
     * Frames in one full on/off cadence — for a continuous tone,
     * {@code Long.MAX_VALUE}. For ringback the cycle is the on +
     * off duration in frames.
     */
    private final long cadenceFrames;

    /**
     * Frames the cadence spends in the "on" phase. Beyond this
     * and before {@link #cadenceFrames}, the source emits silence.
     */
    private final long onFrames;

    /**
     * Samples per emitted frame.
     */
    private final int frameSize;

    /**
     * Duration of each emitted frame in milliseconds.
     */
    private final long frameDurationMs;

    /**
     * Monotonic frame counter — drives both phase and the on/off
     * cadence.
     */
    private final AtomicLong frameIndex = new AtomicLong();

    /**
     * Monotonic timestamp of the next frame.
     */
    private final AtomicLong ptsMs = new AtomicLong();

    /**
     * Constructs a tone source.
     *
     * @param freq1Hz         primary frequency in Hz, must be &gt; 0
     * @param freq2Hz         secondary frequency in Hz, or {@code 0}
     *                        for a single sine
     * @param onFrames        frames in the on phase per cadence
     *                        cycle, or {@code Long.MAX_VALUE} for a
     *                        continuous tone
     * @param cadenceFrames   total frames in one on+off cycle, or
     *                        {@code Long.MAX_VALUE} for continuous
     * @param frameSize       samples per emitted frame
     * @param frameDurationMs duration of each emitted frame, ms
     */
    public ToneGenerator(double freq1Hz, double freq2Hz, long onFrames, long cadenceFrames,
                int frameSize, long frameDurationMs) {
        if (freq1Hz <= 0) {
            throw new IllegalArgumentException("freq1Hz must be > 0");
        }
        if (freq2Hz < 0) {
            throw new IllegalArgumentException("freq2Hz must be ≥ 0");
        }
        if (onFrames < 1) {
            throw new IllegalArgumentException("onFrames must be ≥ 1");
        }
        if (cadenceFrames < onFrames) {
            throw new IllegalArgumentException("cadenceFrames must be ≥ onFrames");
        }
        if (frameSize < 1) {
            throw new IllegalArgumentException("frameSize must be ≥ 1");
        }
        if (frameDurationMs < 1) {
            throw new IllegalArgumentException("frameDurationMs must be ≥ 1");
        }
        this.freq1Hz = freq1Hz;
        this.freq2Hz = freq2Hz;
        this.onFrames = onFrames;
        this.cadenceFrames = cadenceFrames;
        this.frameSize = frameSize;
        this.frameDurationMs = frameDurationMs;
    }

    /**
     * Returns a continuous single-tone source at the given
     * frequency, default frame geometry.
     *
     * @param frequencyHz the tone frequency
     * @return the source
     */
    public static ToneGenerator sine(int frequencyHz) {
        return new ToneGenerator(frequencyHz, 0,
                Long.MAX_VALUE, Long.MAX_VALUE,
                DEFAULT_FRAME_SIZE, DEFAULT_FRAME_DURATION_MS);
    }

    /**
     * Returns a Western European ringback tone source — 425 Hz on
     * for 1 second, off for 4 seconds, repeating.
     *
     * @return the source
     */
    public static ToneGenerator ringback() {
        var onFrames = 1000 / DEFAULT_FRAME_DURATION_MS;
        var cadenceFrames = 5000 / DEFAULT_FRAME_DURATION_MS;
        return new ToneGenerator(425, 0, onFrames, cadenceFrames,
                DEFAULT_FRAME_SIZE, DEFAULT_FRAME_DURATION_MS);
    }

    /**
     * Returns a DTMF tone source for the given digit, continuous.
     *
     * @param digit one of {@code 0-9 * # A B C D}
     * @return the source
     * @throws IllegalArgumentException if {@code digit} isn't a
     *                                  valid DTMF symbol
     */
    public static ToneGenerator dtmf(char digit) {
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
        return new ToneGenerator(row, col,
                Long.MAX_VALUE, Long.MAX_VALUE,
                DEFAULT_FRAME_SIZE, DEFAULT_FRAME_DURATION_MS);
    }

    @Override
    public AudioFrame next() {
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
