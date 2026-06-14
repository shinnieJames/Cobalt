package com.github.auties00.cobalt.call.session;

import com.github.auties00.cobalt.call.video.VideoCodec;
import com.github.auties00.cobalt.call.video.VideoPipelineOptions;

import java.util.Objects;

/**
 * Configures one video track added to a voice call.
 *
 * <p>A value of this type describes a single camera or screen-share track: the local and remote
 * video synchronization sources (SSRCs), the RTP payload type for the chosen {@link Codec}, the
 * {@link VideoPipelineOptions} resolution, frame-rate, and bitrate profile, and the {@link Kind} that
 * tells the peer whether the track carries a camera feed or a screen capture. The local sender
 * stamps {@code localVideoSsrc} onto outbound packets and the receiver accepts only
 * {@code remoteVideoSsrc} from the peer; the two SSRCs must differ. A track is added to an existing
 * voice session through
 * {@link com.github.auties00.cobalt.call.session.VoiceCallSession#startVideoTrack(VideoTrackOptions)},
 * which may run a camera track and a screen-share track simultaneously, each on its own SSRC and
 * payload type.
 *
 * <p>Instances are constructed by the application and passed into the video-track API. The
 * {@link #defaults(int, int)} and {@link #screenShareDefaults(int, int)} factories produce a
 * ready-to-use VP8 configuration, leaving only the SSRCs to fill in, and {@link #withResolution(int, int)}
 * derives a copy at a different resolution.
 *
 * @param localVideoSsrc   the 32-bit SSRC the local video sender stamps onto outbound RTP packets
 * @param remoteVideoSsrc  the 32-bit SSRC the receiver accepts from the peer; must differ from
 *                         {@code localVideoSsrc}
 * @param videoPayloadType the RTP payload type for the chosen codec, in {@code [0, 127]} (the WebRTC
 *                         convention is {@code 96} for VP8 and {@code 102} or {@code 127} for H.264)
 * @param codec            the video codec, either {@link Codec#VP8} or {@link Codec#H264}
 * @param pipeline         the resolution, frame-rate, and bitrate profile
 * @param kind             whether this track carries a {@link Kind#CAMERA} feed or a
 *                         {@link Kind#SCREEN_SHARE} capture, surfaced to the peer so receiving
 *                         clients can render screen captures differently from a camera feed
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
     * Holds the RTP payload type WebRTC conventionally assigns to VP8.
     *
     * @implNote This implementation uses {@code 96}, the dynamic payload type WebRTC endpoints
     * customarily negotiate for VP8.
     */
    public static final int DEFAULT_VP8_PAYLOAD_TYPE = 96;

    /**
     * Holds the RTP payload type used by default for H.264.
     *
     * @implNote This implementation uses {@code 102}; WebRTC endpoints negotiate several payload
     * types for H.264 depending on profile, with {@code 102} and {@code 127} being the most common.
     */
    public static final int DEFAULT_H264_PAYLOAD_TYPE = 102;

    /**
     * Selects the video codec for a track.
     *
     * <p>The concrete encoder/decoder pair is constructed from the selected constant by
     * {@link #buildCodec()} using the configured resolution and bitrate.
     */
    public enum Codec {
        /**
         * Selects libvpx VP8, the default codec for WhatsApp video.
         */
        VP8,
        /**
         * Selects openh264, used as a fallback when VP8 is not available.
         */
        H264
    }

    /**
     * Identifies what a video track carries.
     *
     * <p>The kind is surfaced to the peer via call signaling so receiving clients can render a
     * screen-share differently from a regular camera feed.
     */
    public enum Kind {
        /**
         * Marks the track as the local device's camera capture.
         */
        CAMERA,
        /**
         * Marks the track as a desktop or window screen-share capture.
         */
        SCREEN_SHARE
    }

    /**
     * Validates the codec, pipeline, and kind references, the payload-type range, and the SSRC
     * distinctness invariant.
     *
     * @throws NullPointerException     if {@code codec}, {@code pipeline}, or {@code kind} is
     *                                  {@code null}
     * @throws IllegalArgumentException if {@code videoPayloadType} is outside {@code [0, 127]}, or if
     *                                  {@code localVideoSsrc} equals {@code remoteVideoSsrc}
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
     * Returns default VP8 camera options with the given SSRCs.
     *
     * <p>The returned options use {@link #DEFAULT_VP8_PAYLOAD_TYPE}, {@link Codec#VP8},
     * {@link Kind#CAMERA}, and the {@link VideoPipelineOptions#defaults()} profile of 720x480 at 30
     * frames per second and a 1 Mbps target bitrate.
     *
     * @param localSsrc  the local video SSRC
     * @param remoteSsrc the remote video SSRC; must differ from {@code localSsrc}
     * @return the default camera options
     * @throws IllegalArgumentException if {@code localSsrc} equals {@code remoteSsrc}
     */
    public static VideoTrackOptions defaults(int localSsrc, int remoteSsrc) {
        return new VideoTrackOptions(localSsrc, remoteSsrc, DEFAULT_VP8_PAYLOAD_TYPE,
                Codec.VP8, VideoPipelineOptions.defaults(), Kind.CAMERA);
    }

    /**
     * Returns default screen-share options with the given SSRCs.
     *
     * <p>The returned options are identical to {@link #defaults(int, int)} except that the kind is
     * {@link Kind#SCREEN_SHARE}, which is signaled to the peer so it can render the capture
     * differently from a camera feed.
     *
     * @param localSsrc  the local video SSRC
     * @param remoteSsrc the remote video SSRC; must differ from {@code localSsrc}
     * @return the default screen-share options
     * @throws IllegalArgumentException if {@code localSsrc} equals {@code remoteSsrc}
     */
    public static VideoTrackOptions screenShareDefaults(int localSsrc, int remoteSsrc) {
        return new VideoTrackOptions(localSsrc, remoteSsrc, DEFAULT_VP8_PAYLOAD_TYPE,
                Codec.VP8, VideoPipelineOptions.defaults(), Kind.SCREEN_SHARE);
    }

    /**
     * Returns a copy of these options with the resolution replaced.
     *
     * <p>Every other field, including the SSRCs, payload type, codec, and kind, is carried over
     * unchanged; only the {@link #pipeline()} resolution changes.
     *
     * @param width  the new frame width in pixels; must be even
     * @param height the new frame height in pixels; must be even
     * @return a new options instance with the given resolution
     * @throws IllegalArgumentException if {@code width} or {@code height} is odd or less than
     *                                  {@code 2}
     */
    public VideoTrackOptions withResolution(int width, int height) {
        return new VideoTrackOptions(localVideoSsrc, remoteVideoSsrc, videoPayloadType,
                codec, pipeline.withResolution(width, height), kind);
    }

    /**
     * Constructs the codec adapter described by these options.
     *
     * <p>A {@link VideoCodec} is built for the selected {@link #codec()} at the {@link #pipeline()}
     * resolution, target bitrate, and frame rate. The returned codec owns native encoder and decoder
     * resources, so the caller is responsible for closing it.
     *
     * @return a fresh {@link VideoCodec}; the caller owns its lifecycle
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
