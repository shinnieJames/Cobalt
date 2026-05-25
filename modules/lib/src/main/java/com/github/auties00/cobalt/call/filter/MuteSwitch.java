package com.github.auties00.cobalt.call.filter;

import com.github.auties00.cobalt.call.frame.audio.AudioFrame;
import com.github.auties00.cobalt.call.frame.audio.AudioSource;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Wraps an {@link AudioSource} with a software mute switch.
 *
 * <p>While unmuted, frames pass through from the wrapped source unchanged. While muted, each frame
 * is replaced by a zero-PCM frame of the same sample count and the same presentation timestamp, so
 * the encoder keeps receiving frames at the source's cadence regardless of mute state and the
 * outbound stream stays continuous. The mute state is toggled through {@link #setMuted(boolean)} and
 * read through {@link #muted()}; both are safe to call concurrently with {@link #next()}. The switch
 * gates frames only, so muting does not disconnect or pause the underlying source.
 */
public final class MuteSwitch implements AudioSource {
    /**
     * Wrapped source whose frames are gated by the mute switch.
     */
    private final AudioSource delegate;

    /**
     * Current mute state; {@code true} replaces each delivered frame with silence.
     */
    private final AtomicBoolean muted = new AtomicBoolean();

    /**
     * Constructs a mute switch around {@code delegate} in the unmuted state.
     *
     * @param delegate the source whose frames are gated
     * @throws NullPointerException if {@code delegate} is {@code null}
     */
    public MuteSwitch(AudioSource delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate cannot be null");
    }

    /**
     * Returns whether the switch is currently muted.
     *
     * @return {@code true} if muted, {@code false} otherwise
     */
    public boolean muted() {
        return muted.get();
    }

    /**
     * Sets the mute state.
     *
     * <p>The change takes effect on the next {@link #next()} call.
     *
     * @param muted {@code true} to mute, {@code false} to pass frames through
     */
    public void setMuted(boolean muted) {
        this.muted.set(muted);
    }

    /**
     * {@inheritDoc}
     *
     * @implNote This implementation pulls one frame from the wrapped source and, when muted,
     * returns a {@link AudioFrame} whose PCM array is all zeros and whose length and
     * {@link AudioFrame#ptsMs()} match the pulled frame, preserving the source's timing. A
     * {@code null} from the wrapped source is forwarded unchanged so end-of-stream still propagates
     * while muted.
     */
    @Override
    public AudioFrame next() throws InterruptedException {
        var frame = delegate.next();
        if (frame == null || !muted.get()) {
            return frame;
        }
        return new AudioFrame(new short[frame.pcm().length], frame.ptsMs());
    }
}
