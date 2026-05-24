package com.github.auties00.cobalt.call.frame.audio;

import java.util.Objects;

/**
 * One frame of mono PCM audio at the rate WhatsApp's wire protocol
 * uses (16 kHz, signed 16-bit). The frame's duration is determined by
 * its sample count: 160 samples = 10 ms (the cadence Opus encodes at
 * for VOIP) — but Cobalt accepts any legal Opus frame size.
 *
 * <p>Sample rate and channel count are not carried per-frame: the
 * Cobalt call API is locked to 16 kHz mono on the wire to match the
 * WhatsApp Web Opus configuration. Producers that capture at a
 * different rate (e.g. 48 kHz from the OS mic) must downsample before
 * pushing into a {@link AudioSink} — the
 * {@code cobalt-media-local} companion module does this automatically.
 *
 * @param pcm   the PCM samples; never {@code null}
 * @param ptsMs the presentation timestamp in milliseconds, monotonic
 *              within a call
 */
public record AudioFrame(short[] pcm, long ptsMs) {
    /**
     * Compact constructor — null-checks the payload.
     */
    public AudioFrame {
        Objects.requireNonNull(pcm, "pcm cannot be null");
    }
}
