package com.github.auties00.cobalt.call.session;

import com.github.auties00.cobalt.call.internal.audio.AudioPipelineOptions;

import java.security.SecureRandom;
import java.util.Objects;

/**
 * Configuration for a {@link VoiceCallSession}. Bundles the local and
 * remote audio SSRCs, the RTP payload type to use for Opus, and the
 * underlying {@link AudioPipelineOptions} for the codec / AEC / VAD
 * stack.
 *
 * @param localAudioSsrc       the 32-bit SSRC the local sender stamps
 *                             into outbound RTP packets — exchanged
 *                             with the peer via call signaling
 * @param remoteAudioSsrc      the SSRC the receiver accepts from the
 *                             peer
 * @param opusPayloadType      the RTP payload type to use for Opus —
 *                             WebRTC convention is 111
 * @param audio                the codec/AEC/VAD pipeline options
 */
public record VoiceCallOptions(
        int localAudioSsrc,
        int remoteAudioSsrc,
        int opusPayloadType,
        AudioPipelineOptions audio
) {
    /**
     * WebRTC convention for the Opus RTP payload type.
     */
    public static final int DEFAULT_OPUS_PAYLOAD_TYPE = 111;

    /**
     * Compact constructor — null-checks the audio sub-options and
     * validates the payload-type range.
     */
    public VoiceCallOptions {
        Objects.requireNonNull(audio, "audio cannot be null");
        if (opusPayloadType < 0 || opusPayloadType > 0x7F) {
            throw new IllegalArgumentException(
                    "opusPayloadType out of range [0, 127]: " + opusPayloadType);
        }
        if (localAudioSsrc == remoteAudioSsrc) {
            throw new IllegalArgumentException(
                    "localAudioSsrc and remoteAudioSsrc must differ");
        }
    }

    /**
     * Builds a default options block with random SSRCs (the call
     * layer typically overrides them with the values exchanged via
     * signaling) and the WhatsApp-voice {@link AudioPipelineOptions}
     * profile.
     *
     * @param localSsrc  the local SSRC
     * @param remoteSsrc the remote SSRC
     * @return the default options
     */
    public static VoiceCallOptions defaults(int localSsrc, int remoteSsrc) {
        return new VoiceCallOptions(localSsrc, remoteSsrc, DEFAULT_OPUS_PAYLOAD_TYPE,
                AudioPipelineOptions.defaults());
    }

    /**
     * Builds a default options block with the given local SSRC and a
     * randomly-chosen remote SSRC — useful for tests that don't care
     * about cross-peer SSRC alignment.
     *
     * @return the default options
     */
    public static VoiceCallOptions randomDefaults() {
        var rng = new SecureRandom();
        var local = rng.nextInt();
        var remote = local ^ 0x55555555;  // ensure non-equal
        return defaults(local, remote);
    }
}
