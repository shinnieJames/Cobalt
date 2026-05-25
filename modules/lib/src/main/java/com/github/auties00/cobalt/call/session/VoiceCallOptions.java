package com.github.auties00.cobalt.call.session;

import com.github.auties00.cobalt.call.internal.audio.AudioPipelineOptions;

import java.security.SecureRandom;
import java.util.Objects;

/**
 * Configures a one-to-one voice call.
 *
 * <p>A value of this type bundles the local and remote audio synchronization sources (SSRCs), the
 * RTP payload type to use for Opus, and the underlying {@link AudioPipelineOptions} that govern the
 * codec, echo cancellation, and voice-activity-detection stack. The SSRCs identify the two audio
 * streams of the call: the local sender stamps {@code localAudioSsrc} onto its outbound packets, and
 * the receiver accepts only {@code remoteAudioSsrc} from the peer. The two SSRCs must differ.
 *
 * <p>Instances are constructed by the application and passed into the voice-call API. The
 * {@link #defaults(int, int)} and {@link #randomDefaults()} factories produce a ready-to-use
 * configuration that matches WhatsApp's voice profile, leaving only the SSRCs to fill in.
 *
 * @param localAudioSsrc   the 32-bit SSRC the local sender stamps onto outbound RTP packets, as
 *                         exchanged with the peer via call signaling
 * @param remoteAudioSsrc  the 32-bit SSRC the receiver accepts from the peer; must differ from
 *                         {@code localAudioSsrc}
 * @param opusPayloadType  the RTP payload type to use for Opus, in {@code [0, 127]} (the WebRTC
 *                         convention is {@code 111})
 * @param audio            the codec, echo-cancellation, and voice-activity-detection pipeline
 *                         options
 */
public record VoiceCallOptions(
        int localAudioSsrc,
        int remoteAudioSsrc,
        int opusPayloadType,
        AudioPipelineOptions audio
) {
    /**
     * Holds the RTP payload type WebRTC conventionally assigns to Opus.
     *
     * @implNote This implementation uses {@code 111}, the dynamic payload type WebRTC endpoints
     * customarily negotiate for Opus.
     */
    public static final int DEFAULT_OPUS_PAYLOAD_TYPE = 111;

    /**
     * Validates the audio sub-options, the payload-type range, and the SSRC distinctness invariant.
     *
     * @throws NullPointerException     if {@code audio} is {@code null}
     * @throws IllegalArgumentException if {@code opusPayloadType} is outside {@code [0, 127]}, or if
     *                                  {@code localAudioSsrc} equals {@code remoteAudioSsrc}
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
     * Returns a default configuration with the given SSRCs.
     *
     * <p>The returned options use {@link #DEFAULT_OPUS_PAYLOAD_TYPE} and the WhatsApp-voice
     * {@link AudioPipelineOptions#defaults()} profile. The call layer typically overrides the SSRCs
     * with the values exchanged via signaling.
     *
     * @param localSsrc  the local audio SSRC
     * @param remoteSsrc the remote audio SSRC; must differ from {@code localSsrc}
     * @return the default options
     * @throws IllegalArgumentException if {@code localSsrc} equals {@code remoteSsrc}
     */
    public static VoiceCallOptions defaults(int localSsrc, int remoteSsrc) {
        return new VoiceCallOptions(localSsrc, remoteSsrc, DEFAULT_OPUS_PAYLOAD_TYPE,
                AudioPipelineOptions.defaults());
    }

    /**
     * Returns a default configuration with a random local SSRC and a derived, distinct remote SSRC.
     *
     * <p>The remote SSRC is the local SSRC with alternating bits flipped, which guarantees the two
     * differ so the compact constructor's distinctness invariant always holds. This factory is
     * useful for tests that do not care about cross-peer SSRC alignment.
     *
     * @return the default options
     */
    public static VoiceCallOptions randomDefaults() {
        var rng = new SecureRandom();
        var local = rng.nextInt();
        var remote = local ^ 0x55555555;  // alternating bits keep remote != local
        return defaults(local, remote);
    }
}
