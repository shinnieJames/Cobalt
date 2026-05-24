package com.github.auties00.cobalt.call.session;

import com.github.auties00.cobalt.call.internal.video.VideoCodec;
import com.github.auties00.cobalt.call.internal.video.VideoPipelineOptions;

import java.util.Objects;

/**
 * Configuration for one video track (camera or screen-share) added
 * to a {@link VoiceCallSession} via
 * {@link VoiceCallSession#startVideoTrack}.
 *
 * @param localVideoSsrc    32-bit SSRC the local video sender stamps
 *                          into outbound RTP packets
 * @param remoteVideoSsrc   the SSRC the receiver accepts from the
 *                          peer
 * @param videoPayloadType  RTP payload type for the chosen codec —
 *                          WebRTC convention is 96 for VP8, 97 for
 *                          VP9, 102 / 127 for H.264
 * @param codec             {@link Codec#VP8} or {@link Codec#H264}
 * @param pipeline          the resolution/fps/bitrate profile
 * @param kind              whether this track carries
 *                          {@link Kind#CAMERA} or
 *                          {@link Kind#SCREEN_SHARE} bytes —
 *                          surfaced to the peer via signaling so it
 *                          can render screen captures differently
 */
public record VideoTrackOptions(
        int localVideoSsrc,
        int remoteVideoSsrc,
        int videoPayloadType,
        Codec codec,
        VideoPipelineOptions pipeline,
        Kind kind
) {
    /**
     * WebRTC convention for the VP8 RTP payload type.
     */
    public static final int DEFAULT_VP8_PAYLOAD_TYPE = 96;

    /**
     * WebRTC convention for the H.264 RTP payload type (one of
     * several — 102 + 127 are common).
     */
    public static final int DEFAULT_H264_PAYLOAD_TYPE = 102;

    /**
     * Codec selector — concrete instances are constructed inside
     * {@link VoiceCallSession#startVideoTrack(VideoTrackOptions)}
     * with the configured resolution/bitrate.
     */
    public enum Codec {
        /**
         * libvpx VP8 — the default for WhatsApp video.
         */
        VP8,
        /**
         * openh264 — used as a fallback when VP8 isn't available.
         */
        H264
    }

    /**
     * Track kind, surfaced to the peer via call signaling so
     * receiving UIs can render screen-share differently from a
     * regular camera feed.
     */
    public enum Kind {
        /**
         * The local device's camera capture.
         */
        CAMERA,
        /**
         * Desktop / window screen-share capture (#69).
         */
        SCREEN_SHARE
    }

    /**
     * Compact constructor — null-checks fields and validates ranges.
     */
    public VideoTrackOptions {
        Objects.requireNonNull(codec, "codec cannot be null");
        Objects.requireNonNull(pipeline, "pipeline cannot be null");
        Objects.requireNonNull(kind, "kind cannot be null");
        if (videoPayloadType < 0 || videoPayloadType > 0x7F) {
            throw new IllegalArgumentException(
                    "videoPayloadType out of range [0, 127]: " + videoPayloadType);
        }
        if (localVideoSsrc == remoteVideoSsrc) {
            throw new IllegalArgumentException(
                    "localVideoSsrc and remoteVideoSsrc must differ");
        }
    }

    /**
     * Builds default VP8 camera options at 720×480 30fps 1 Mbps.
     *
     * @param localSsrc  the local video SSRC
     * @param remoteSsrc the remote video SSRC
     * @return the default options
     */
    public static VideoTrackOptions defaults(int localSsrc, int remoteSsrc) {
        return new VideoTrackOptions(localSsrc, remoteSsrc, DEFAULT_VP8_PAYLOAD_TYPE,
                Codec.VP8, VideoPipelineOptions.defaults(), Kind.CAMERA);
    }

    /**
     * Builds default screen-share options — same VP8 codec but
     * {@link Kind#SCREEN_SHARE} for signaling.
     *
     * @param localSsrc  the local video SSRC
     * @param remoteSsrc the remote video SSRC
     * @return the default options
     */
    public static VideoTrackOptions screenShareDefaults(int localSsrc, int remoteSsrc) {
        return new VideoTrackOptions(localSsrc, remoteSsrc, DEFAULT_VP8_PAYLOAD_TYPE,
                Codec.VP8, VideoPipelineOptions.defaults(), Kind.SCREEN_SHARE);
    }

    /**
     * Returns a copy with a new resolution.
     *
     * @param width  the new width
     * @param height the new height
     * @return the modified copy
     */
    public VideoTrackOptions withResolution(int width, int height) {
        return new VideoTrackOptions(localVideoSsrc, remoteVideoSsrc, videoPayloadType,
                codec, pipeline.withResolution(width, height), kind);
    }

    /**
     * Constructs the codec adapter for these options.
     *
     * @return a fresh {@link VideoCodec}; the caller owns its
     *         lifecycle
     */
    public VideoCodec buildCodec() {
        return switch (codec) {
            case VP8 -> VideoCodec.vp8(pipeline.width(), pipeline.height(),
                    pipeline.targetBitrateBps(), pipeline.fps());
            case H264 -> VideoCodec.h264(pipeline.width(), pipeline.height(),
                    pipeline.targetBitrateBps(), pipeline.fps());
        };
    }
}
