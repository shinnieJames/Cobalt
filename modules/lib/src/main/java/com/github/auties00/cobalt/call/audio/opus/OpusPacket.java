package com.github.auties00.cobalt.call.audio.opus;

import java.util.Objects;

/**
 * Holds one Opus-encoded audio frame together with its timestamp and
 * voice-activity flag.
 *
 * <p>This record is the boundary type between the
 * {@link com.github.auties00.cobalt.call.audio.AudioPipeline},
 * which owns the codec and the echo-cancellation, gain-control, and
 * noise-suppression stages, and the RTP transport, which owns
 * packetisation, jitter buffering, and SRTP protection. The
 * {@link #voiceActive()} flag mirrors what the
 * {@link com.github.auties00.cobalt.call.audio.processing.AudioPreprocessor}
 * reported for the source PCM ({@code true} for speech, {@code false} for
 * silence or noise) and carries through to the RTP layer so the sender
 * can decide whether to transmit; this matters for discontinuous
 * transmission, where a silence frame is a single byte that the transport
 * may suppress or send at lower priority.
 *
 * @param payload     the Opus packet bytes; never empty, since even a
 *                    discontinuous-transmission frame is at least one byte
 * @param ptsMs       the source PCM frame's presentation timestamp, in
 *                    milliseconds, measured against an epoch chosen by the
 *                    pipeline
 * @param voiceActive whether the preprocessor judged the source frame to
 *                    contain voice, taken from its voice-activity detector
 *                    when enabled
 */
public record OpusPacket(byte[] payload, long ptsMs, boolean voiceActive) {
    /**
     * Validates that the payload is present.
     *
     * @throws NullPointerException if {@code payload} is {@code null}
     */
    public OpusPacket {
        Objects.requireNonNull(payload, "payload cannot be null");
    }
}
