package com.github.auties00.cobalt.call.stream;

import java.util.Objects;

/**
 * Holds one frame of mono PCM audio in the format WhatsApp's call wire protocol uses.
 *
 * <p>The payload is signed 16-bit PCM sampled at 16 kHz, single channel. Sample rate and channel
 * count are not carried per-frame because the Cobalt call API is fixed to 16 kHz mono on the wire
 * to match the WhatsApp Opus configuration; a frame therefore describes only its samples and the
 * point in time at which they are presented. The frame's duration is implied by its sample count:
 * at 16 kHz, 160 samples corresponds to 10 ms, the cadence Opus encodes at for calls, though any
 * legal Opus frame size is accepted.
 *
 * <p>Producers that capture at a different rate, such as 48 kHz from an operating system
 * microphone, must downsample to 16 kHz mono before pushing into an {@link AudioOutputStream}.
 *
 * @apiNote Sources feeding an {@link AudioOutputStream} and sinks draining an
 * {@link AudioInputStream} that capture or render at a native rate other than 16 kHz mono are
 * responsible for resampling; the call API does not negotiate alternate formats and a mismatched
 * rate is reproduced at the wrong pitch.
 * @param pcm   the signed 16-bit PCM samples, one channel; never {@code null}
 * @param ptsMs the presentation timestamp in milliseconds, monotonically increasing within a call
 */
public record AudioFrame(short[] pcm, long ptsMs) {
    /**
     * Constructs a frame, rejecting a {@code null} sample buffer.
     *
     * <p>The buffer reference is retained as-is; callers transfer ownership and must not mutate the
     * array after construction.
     *
     * @throws NullPointerException if {@code pcm} is {@code null}
     */
    public AudioFrame {
        Objects.requireNonNull(pcm, "pcm cannot be null");
    }
}
