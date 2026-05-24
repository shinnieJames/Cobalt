package com.github.auties00.cobalt.call.internal.video;

/**
 * Configuration for a {@link VideoPipeline}. Defaults track WhatsApp's
 * 1:1 video-call profile: 480p (720×480), 30 fps, 1 Mbps target,
 * VP8 codec.
 *
 * @param width            frame width in pixels (must be even)
 * @param height           frame height in pixels (must be even)
 * @param fps              capture frame rate; controls the encoder's
 *                         time base and natural keyframe cadence
 * @param targetBitrateBps initial target bitrate in bits per second
 *                         — the BWE driver may move this up or down
 *                         at runtime via
 *                         {@link VideoPipeline#adjustBitrate(int)}
 * @param keyframeOnStart  whether the first encoded frame is forced
 *                         to be a keyframe; {@code true} is normal
 *                         for a fresh receiver
 */
public record VideoPipelineOptions(
        int width,
        int height,
        int fps,
        int targetBitrateBps,
        boolean keyframeOnStart
) {
    /**
     * Default frame width — 720 pixels.
     */
    public static final int DEFAULT_WIDTH = 720;

    /**
     * Default frame height — 480 pixels.
     */
    public static final int DEFAULT_HEIGHT = 480;

    /**
     * Default frame rate — 30 fps.
     */
    public static final int DEFAULT_FPS = 30;

    /**
     * Default target bitrate — 1 Mbps.
     */
    public static final int DEFAULT_BITRATE_BPS = 1_000_000;

    /**
     * Compact constructor — validates frame dimensions and rates.
     */
    public VideoPipelineOptions {
        if (width < 2 || width % 2 != 0) {
            throw new IllegalArgumentException("width must be even and ≥ 2");
        }
        if (height < 2 || height % 2 != 0) {
            throw new IllegalArgumentException("height must be even and ≥ 2");
        }
        if (fps < 1) {
            throw new IllegalArgumentException("fps must be ≥ 1");
        }
        if (targetBitrateBps < 1) {
            throw new IllegalArgumentException("targetBitrateBps must be ≥ 1");
        }
    }

    /**
     * Returns the WhatsApp-1:1-video default profile.
     *
     * @return the default options
     */
    public static VideoPipelineOptions defaults() {
        return new VideoPipelineOptions(DEFAULT_WIDTH, DEFAULT_HEIGHT, DEFAULT_FPS,
                DEFAULT_BITRATE_BPS, true);
    }

    /**
     * Returns a copy with new resolution.
     *
     * @param width  the new width
     * @param height the new height
     * @return the modified copy
     */
    public VideoPipelineOptions withResolution(int width, int height) {
        return new VideoPipelineOptions(width, height, fps, targetBitrateBps, keyframeOnStart);
    }

    /**
     * Returns a copy with a new target bitrate.
     *
     * @param bps the new target bitrate
     * @return the modified copy
     */
    public VideoPipelineOptions withBitrate(int bps) {
        return new VideoPipelineOptions(width, height, fps, bps, keyframeOnStart);
    }
}
