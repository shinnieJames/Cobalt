package com.github.auties00.cobalt.call.video;

/**
 * Holds the construction-time configuration for a {@link VideoPipeline}.
 *
 * <p>An instance fixes the encoder's resolution, frame rate, starting bitrate, and whether the first
 * encoded frame is forced to be a keyframe. The {@link #defaults()} profile mirrors WhatsApp's 1:1
 * video-call defaults. After construction the bitrate is the only field that moves; the BWE driver
 * retargets it at runtime through {@link VideoPipeline#adjustBitrate(int)}, while resolution and
 * frame rate stay fixed for the lifetime of a pipeline.
 *
 * @param width            the frame width in pixels; must be even
 * @param height           the frame height in pixels; must be even
 * @param fps              the capture frame rate, which controls the encoder's time base and natural
 *                         keyframe cadence
 * @param targetBitrateBps the initial target bitrate in bits per second, which the BWE driver may
 *                         move up or down at runtime via {@link VideoPipeline#adjustBitrate(int)}
 * @param keyframeOnStart  whether the first encoded frame is forced to be a keyframe, normally
 *                         {@code true} so a fresh receiver can decode from the first packet
 */
public record VideoPipelineOptions(
        int width,
        int height,
        int fps,
        int targetBitrateBps,
        boolean keyframeOnStart
) {
    /**
     * Holds the default frame width in pixels.
     *
     * @implNote This implementation uses {@code 720}, the width of WhatsApp's 480p 1:1 video-call
     * profile.
     */
    public static final int DEFAULT_WIDTH = 720;

    /**
     * Holds the default frame height in pixels.
     *
     * @implNote This implementation uses {@code 480}, the height of WhatsApp's 480p 1:1 video-call
     * profile.
     */
    public static final int DEFAULT_HEIGHT = 480;

    /**
     * Holds the default capture frame rate.
     *
     * @implNote This implementation uses {@code 30} frames per second, matching WhatsApp's 1:1
     * video-call profile.
     */
    public static final int DEFAULT_FPS = 30;

    /**
     * Holds the default target bitrate in bits per second.
     *
     * @implNote This implementation uses {@code 1_000_000} (1 Mbps), the starting target of
     * WhatsApp's 1:1 video-call profile before BWE adaptation moves it.
     */
    public static final int DEFAULT_BITRATE_BPS = 1_000_000;

    /**
     * Validates the frame dimensions and rates.
     *
     * <p>Both dimensions must be even and at least {@code 2} so the I420 chroma planes have integral
     * sizes; the frame rate and target bitrate must each be at least {@code 1}.
     *
     * @throws IllegalArgumentException if {@code width} or {@code height} is odd or less than
     *                                  {@code 2}, or if {@code fps} or {@code targetBitrateBps} is
     *                                  less than {@code 1}
     */
    public VideoPipelineOptions {
        if (width < 2 || width % 2 != 0) {
            throw new IllegalArgumentException("width must be even and >= 2");
        }
        if (height < 2 || height % 2 != 0) {
            throw new IllegalArgumentException("height must be even and >= 2");
        }
        if (fps < 1) {
            throw new IllegalArgumentException("fps must be >= 1");
        }
        if (targetBitrateBps < 1) {
            throw new IllegalArgumentException("targetBitrateBps must be >= 1");
        }
    }

    /**
     * Returns the default WhatsApp 1:1 video-call profile.
     *
     * <p>The returned options use {@link #DEFAULT_WIDTH}, {@link #DEFAULT_HEIGHT},
     * {@link #DEFAULT_FPS}, and {@link #DEFAULT_BITRATE_BPS}, with the first frame forced to a
     * keyframe.
     *
     * @return the default options
     */
    public static VideoPipelineOptions defaults() {
        return new VideoPipelineOptions(DEFAULT_WIDTH, DEFAULT_HEIGHT, DEFAULT_FPS,
                DEFAULT_BITRATE_BPS, true);
    }

    /**
     * Returns a copy of these options with the resolution replaced.
     *
     * <p>The frame rate, target bitrate, and keyframe-on-start flag are carried over unchanged.
     *
     * @param width  the new frame width in pixels; must be even
     * @param height the new frame height in pixels; must be even
     * @return a new options instance with the given resolution
     * @throws IllegalArgumentException if {@code width} or {@code height} is odd or less than
     *                                  {@code 2}
     */
    public VideoPipelineOptions withResolution(int width, int height) {
        return new VideoPipelineOptions(width, height, fps, targetBitrateBps, keyframeOnStart);
    }

    /**
     * Returns a copy of these options with the target bitrate replaced.
     *
     * <p>The resolution, frame rate, and keyframe-on-start flag are carried over unchanged.
     *
     * @param bps the new target bitrate in bits per second; must be at least {@code 1}
     * @return a new options instance with the given target bitrate
     * @throws IllegalArgumentException if {@code bps} is less than {@code 1}
     */
    public VideoPipelineOptions withBitrate(int bps) {
        return new VideoPipelineOptions(width, height, fps, bps, keyframeOnStart);
    }
}
