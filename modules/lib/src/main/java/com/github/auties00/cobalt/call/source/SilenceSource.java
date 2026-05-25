package com.github.auties00.cobalt.call.source;

import com.github.auties00.cobalt.call.frame.audio.AudioFrame;
import com.github.auties00.cobalt.call.frame.audio.AudioSource;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Emits zero-filled audio frames at the call's frame cadence as an {@link AudioSource}.
 *
 * <p>Every {@link #next()} returns a fresh {@link AudioFrame} of all-zero signed 16-bit samples with
 * a monotonically increasing presentation timestamp. The source never ends; it keeps producing
 * silence until the call stops pulling from it. The default profile emits 160-sample, 10 ms frames
 * matching the call wire format, and a custom geometry can be supplied through
 * {@link #SilenceSource(int, long)}.
 *
 * @apiNote Wire this source into a call as a placeholder when no real audio is available, for
 * example while the microphone is muted at the operating-system level, as a test fixture, or as
 * hold filler while the user chooses a track. The emitted PCM is true digital silence, not
 * comfort noise; a producer that needs audible comfort noise should layer a noise generator on top.
 * Because it never returns {@code null}, the call treats it as an endless stream, so swap it out
 * rather than relying on it to signal end-of-stream.
 */
public final class SilenceSource implements AudioSource {
    /**
     * Holds the number of samples in each emitted frame.
     */
    private final int frameSize;

    /**
     * Holds the duration represented by one frame, in milliseconds, by which each frame's
     * presentation timestamp advances.
     */
    private final long frameDurationMs;

    /**
     * Holds the presentation timestamp, in milliseconds, of the next frame, advanced atomically so
     * the source may be polled from any thread.
     */
    private final AtomicLong ptsMs = new AtomicLong();

    /**
     * Constructs a silence source for the default call profile.
     *
     * <p>Equivalent to {@link #SilenceSource(int, long)} with 160 samples and a 10 ms frame
     * duration, matching the call wire cadence.
     */
    public SilenceSource() {
        this(160, 10);
    }

    /**
     * Constructs a silence source with an explicit frame geometry.
     *
     * @param frameSize       the number of samples per frame
     * @param frameDurationMs the duration of each frame in milliseconds
     * @throws IllegalArgumentException if {@code frameSize} or {@code frameDurationMs} is less than
     *                                  {@code 1}
     */
    public SilenceSource(int frameSize, long frameDurationMs) {
        if (frameSize < 1) {
            throw new IllegalArgumentException("frameSize must be >= 1");
        }
        if (frameDurationMs < 1) {
            throw new IllegalArgumentException("frameDurationMs must be >= 1");
        }
        this.frameSize = frameSize;
        this.frameDurationMs = frameDurationMs;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Returns a fresh all-zero frame and advances the presentation timestamp by the configured
     * frame duration. Never blocks and never returns {@code null}.
     *
     * @return a new silent frame; never {@code null}
     */
    @Override
    public AudioFrame next() {
        var pts = ptsMs.getAndAdd(frameDurationMs);
        return new AudioFrame(new short[frameSize], pts);
    }
}
