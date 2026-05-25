package com.github.auties00.cobalt.call.sink;

import com.github.auties00.cobalt.call.frame.audio.AudioFrame;
import com.github.auties00.cobalt.call.frame.audio.AudioSink;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Measures the level of the PCM passing through it and optionally forwards each frame onward.
 *
 * <p>Every frame written to this sink is scanned for its peak amplitude and root-mean-square energy,
 * both normalised to the unit interval against full-scale signed 16-bit amplitude, and the most
 * recent values are published for a caller to read through {@link #snapshot()}. When a downstream
 * sink was supplied at construction, the frame is then forwarded to it unchanged; otherwise the frame
 * is measured and discarded. The published readings reflect the last non-empty frame only; an empty
 * frame is forwarded without updating any reading.
 *
 * <p>Measuring on the audio path and reading the snapshot from another thread are both safe to do
 * concurrently.
 *
 * @apiNote Insert this between the call and a speaker to drive a VU bar or talker indicator, reading
 * {@link #snapshot()} from the user-interface thread on a timer. Convert a reading to decibels with
 * {@code 20 * log10(level)}, treating zero as silence.
 */
public final class LevelMeter implements AudioSink {
    /**
     * Normalises a signed 16-bit sample magnitude to the unit interval.
     *
     * @implNote This implementation divides by 32768, the magnitude of the most negative signed
     * 16-bit sample, so a full-scale frame reads as exactly {@code 1.0}.
     */
    private static final double FULL_SCALE = 32_768.0;

    /**
     * Holds the sink each measured frame is forwarded to, or {@code null} for meter-only mode in
     * which frames are dropped after measurement.
     */
    private final AudioSink downstream;

    /**
     * Holds the most recent peak amplitude, the largest absolute sample of the last frame, in the
     * unit interval.
     */
    private final AtomicReference<Double> lastPeak = new AtomicReference<>(0.0);

    /**
     * Holds the most recent root-mean-square energy of the last frame, in the unit interval.
     */
    private final AtomicReference<Double> lastRms = new AtomicReference<>(0.0);

    /**
     * Counts the non-empty frames measured so far, monotonically increasing.
     */
    private final AtomicLong frameCount = new AtomicLong();

    /**
     * Constructs a meter that forwards each measured frame to the given sink.
     *
     * @param downstream the sink to forward to, or {@code null} for meter-only mode in which frames
     *                   are dropped after measurement
     */
    public LevelMeter(AudioSink downstream) {
        this.downstream = downstream;
    }

    /**
     * Constructs a meter-only sink that measures each frame and then drops it.
     */
    public LevelMeter() {
        this(null);
    }

    /**
     * Captures the meter's readings at one instant.
     *
     * @param peak       the most recent peak amplitude, in the unit interval
     * @param rms        the most recent root-mean-square energy, in the unit interval
     * @param frameCount the number of non-empty frames measured so far
     */
    public record Snapshot(double peak, double rms, long frameCount) {
    }

    /**
     * Returns the meter's current readings as an immutable snapshot.
     *
     * @return a snapshot of the latest peak, root-mean-square energy, and measured-frame count
     */
    public Snapshot snapshot() {
        return new Snapshot(lastPeak.get(), lastRms.get(), frameCount.get());
    }

    /**
     * {@inheritDoc}
     *
     * <p>Scans the frame for its peak absolute sample and root-mean-square energy, normalises both to
     * the unit interval, and publishes them for {@link #snapshot()}. An empty frame is forwarded
     * without updating any reading. The frame is then forwarded to the downstream sink when one was
     * supplied, or dropped otherwise.
     */
    @Override
    public void write(AudioFrame frame) throws InterruptedException {
        Objects.requireNonNull(frame, "frame cannot be null");
        var pcm = frame.pcm();
        if (pcm.length > 0) {
            var peakSample = 0;
            long sumSquares = 0;
            for (var sample : pcm) {
                var abs = sample < 0 ? -sample : sample;
                if (abs > peakSample) {
                    peakSample = abs;
                }
                sumSquares += (long) sample * sample;
            }
            lastPeak.set(peakSample / FULL_SCALE);
            lastRms.set(Math.sqrt((double) sumSquares / pcm.length) / FULL_SCALE);
            frameCount.incrementAndGet();
        }
        if (downstream != null) {
            downstream.write(frame);
        }
    }
}
