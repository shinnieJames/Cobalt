package com.github.auties00.cobalt.call.filter;

import com.github.auties00.cobalt.call.frame.audio.AudioFrame;
import com.github.auties00.cobalt.call.frame.audio.AudioSource;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A wrapper {@link AudioSource} with a software mute switch —
 * frames pass through when {@link #setMuted(boolean) unmuted} and
 * are replaced with zero-PCM (preserving the underlying source's
 * pts) when muted, so the encoder keeps producing frames at the
 * right cadence regardless of mute state.
 *
 * <p>Useful for "user pressed mute in the UI" without
 * disconnecting the underlying mic.
 */
public final class MuteSwitch implements AudioSource {
    /**
     * The wrapped source the wrapper passes frames through from.
     */
    private final AudioSource delegate;

    /**
     * Mute state — when {@code true}, every frame is replaced
     * with a zero-pcm frame of the same length.
     */
    private final AtomicBoolean muted = new AtomicBoolean();

    /**
     * Constructs a wrapper around {@code delegate}; starts
     * unmuted.
     *
     * @param delegate the underlying source
     * @throws NullPointerException if {@code delegate} is
     *                              {@code null}
     */
    public MuteSwitch(AudioSource delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate cannot be null");
    }

    /**
     * Returns whether the source is currently muted.
     *
     * @return {@code true} if muted
     */
    public boolean muted() {
        return muted.get();
    }

    /**
     * Sets the mute state.
     *
     * @param muted whether to mute
     */
    public void setMuted(boolean muted) {
        this.muted.set(muted);
    }

    @Override
    public AudioFrame next() throws InterruptedException {
        var frame = delegate.next();
        if (frame == null || !muted.get()) {
            return frame;
        }
        return new AudioFrame(new short[frame.pcm().length], frame.ptsMs());
    }
}
