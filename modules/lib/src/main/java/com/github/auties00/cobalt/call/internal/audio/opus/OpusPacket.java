package com.github.auties00.cobalt.call.internal.audio.opus;

import java.util.Objects;

/**
 * One Opus-encoded audio frame produced by, or destined for, an
 * {@link AudioPipeline}. The boundary type between the pipeline (which
 * owns the codec + AEC/AGC/NS) and the RTP transport (#78) (which owns
 * RTP packetisation, jitter buffering, and SRTP protection).
 *
 * <p>The {@code voiceActive} flag mirrors what the
 * {@link com.github.auties00.cobalt.call.internal.audio.processing.AudioPreprocessor
 * speexdsp preprocessor} reported for the source PCM: {@code true} for
 * speech, {@code false} for silence/noise. It carries through to the
 * RTP layer so the sender can decide whether to transmit (especially
 * relevant for DTX, which produces a 1-byte packet during silence —
 * the RTP layer may suppress it entirely or downgrade priority).
 *
 * @param payload     the Opus packet bytes; never empty (a DTX frame
 *                    is at least 1 byte)
 * @param ptsMs       the source PCM frame's presentation timestamp,
 *                    in milliseconds since some epoch chosen by the
 *                    pipeline (typically wall-clock-ish)
 * @param voiceActive whether the encoder believed the source frame
 *                    contained voice; flows from the speexdsp VAD
 *                    when enabled
 */
public record OpusPacket(byte[] payload, long ptsMs, boolean voiceActive) {
    /**
     * Compact constructor — null-checks the payload.
     */
    public OpusPacket {
        Objects.requireNonNull(payload, "payload cannot be null");
    }
}
