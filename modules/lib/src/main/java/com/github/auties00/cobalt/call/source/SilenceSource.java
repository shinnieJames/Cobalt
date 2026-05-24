package com.github.auties00.cobalt.call.source;

import com.github.auties00.cobalt.call.frame.audio.AudioFrame;
import com.github.auties00.cobalt.call.frame.audio.AudioSource;

import java.util.concurrent.atomic.AtomicLong;

/**
 * An {@link AudioSource} that emits zero-filled frames at the
 * call's frame cadence — useful as a placeholder when no real
 * input is available (mic muted at the OS level, test fixture,
 * hold-music while the user picks a track).
 *
 * <p>The emitted PCM is all zeros at 16-bit signed; producers
 * that need comfort-noise should layer a noise generator on top.
 */
public final class SilenceSource implements AudioSource {
    /**
     * Number of samples in each emitted frame — typically 160
     * for the WhatsApp 10-ms-at-16kHz cadence.
     */
    private final int frameSize;

    /**
     * Wall-clock-ish duration represented by one frame, in
     * milliseconds.
     */
    private final long frameDurationMs;

    /**
     * Monotonic timestamp of the next frame.
     */
    private final AtomicLong ptsMs = new AtomicLong();

    /**
     * Constructs a silence source for the WhatsApp default profile
     * (160 samples, 10 ms).
     */
    public SilenceSource() {
        this(160, 10);
    }

    /**
     * Constructs a silence source with explicit frame geometry.
     *
     * @param frameSize       samples per frame
     * @param frameDurationMs duration of each frame in milliseconds
     * @throws IllegalArgumentException if either argument is &lt; 1
     */
    public SilenceSource(int frameSize, long frameDurationMs) {
        if (frameSize < 1) {
            throw new IllegalArgumentException("frameSize must be ≥ 1");
        }
        if (frameDurationMs < 1) {
            throw new IllegalArgumentException("frameDurationMs must be ≥ 1");
        }
        this.frameSize = frameSize;
        this.frameDurationMs = frameDurationMs;
    }

    @Override
    public AudioFrame next() {
        var pts = ptsMs.getAndAdd(frameDurationMs);
        return new AudioFrame(new short[frameSize], pts);
    }
}
