package com.github.auties00.cobalt.call.sink;

import com.github.auties00.cobalt.call.frame.audio.AudioFrame;
import com.github.auties00.cobalt.call.frame.audio.AudioSink;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * An {@link AudioSink} that monitors PCM level (peak + RMS) for
 * UI VU bars and forwards every frame on to a delegate sink. The
 * meter values are exposed as snapshots via {@link #snapshot()}.
 *
 * <p>Thread-safe: {@link #write} can run on the encoder thread,
 * {@link #snapshot} on the UI thread.
 */
public final class LevelMeter implements AudioSink {
    /**
     * Peak amplitude of int16 PCM — used to normalise to a
     * unit-interval level.
     */
    private static final double FULL_SCALE = 32_768.0;

    /**
     * Where frames flow after metering. May be {@code null} for
     * meter-only mode.
     */
    private final AudioSink downstream;

    /**
     * Most-recent peak (absolute max sample) in [0, 1].
     */
    private final AtomicReference<Double> lastPeak = new AtomicReference<>(0.0);

    /**
     * Most-recent RMS in [0, 1].
     */
    private final AtomicReference<Double> lastRms = new AtomicReference<>(0.0);

    /**
     * Total frames metered, monotonic.
     */
    private final AtomicLong frameCount = new AtomicLong();

    /**
     * Constructs a metering sink that forwards to
     * {@code downstream}.
     *
     * @param downstream the sink to forward to; may be
     *                   {@code null} for meter-only mode (frames
     *                   are dropped)
     */
    public LevelMeter(AudioSink downstream) {
        this.downstream = downstream;
    }

    /**
     * Constructs a meter-only sink — frames are measured then
     * dropped.
     */
    public LevelMeter() {
        this(null);
    }

    /**
     * One snapshot of the meter's current state.
     *
     * @param peak       most-recent peak amplitude in [0, 1]
     * @param rms        most-recent RMS in [0, 1]
     * @param frameCount total frames metered
     */
    public record Snapshot(double peak, double rms, long frameCount) {
    }

    /**
     * Returns a snapshot of the current meter readings.
     *
     * @return the snapshot
     */
    public Snapshot snapshot() {
        return new Snapshot(lastPeak.get(), lastRms.get(), frameCount.get());
    }

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
