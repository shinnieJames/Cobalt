package com.github.auties00.cobalt.call;

/**
 * Configures a placed or accepted call.
 *
 * <p>Audio is always present at 16 kHz mono, fixed by WhatsApp's wire
 * profile (see
 * {@link com.github.auties00.cobalt.call.frame.audio.AudioFrame}). Video
 * is optional; when enabled it carries the negotiated picture size,
 * frame rate, and encoder bitrate, and the video fields are validated
 * only in that case. Instances are created through the static factories
 * rather than the canonical constructor.
 *
 * {@snippet :
 *   CallOptions.audio();              // voice call
 *   CallOptions.video();              // 640x480 at 30 fps default
 *   CallOptions.video(1280, 720);     // HD
 * }
 *
 * @param videoEnabled    {@code true} for an audio-and-video call,
 *                        {@code false} for audio-only
 * @param videoWidth      frame width in pixels; even and at least 2 when
 *                        {@code videoEnabled}, ignored otherwise
 * @param videoHeight     frame height in pixels; even and at least 2 when
 *                        {@code videoEnabled}, ignored otherwise
 * @param videoFps        target frame rate; at least 1 when
 *                        {@code videoEnabled}
 * @param videoBitrateBps target encoder bitrate in bits per second; at
 *                        least 1 when {@code videoEnabled}
 */
public record CallOptions(
        boolean videoEnabled,
        int videoWidth,
        int videoHeight,
        int videoFps,
        int videoBitrateBps
) {
    /**
     * Constructs call options, validating the video dimensions only when
     * video is enabled.
     *
     * @throws IllegalArgumentException if {@code videoEnabled} and any of
     *                                  {@code videoWidth} or
     *                                  {@code videoHeight} is odd or below
     *                                  2, or {@code videoFps} or
     *                                  {@code videoBitrateBps} is below 1
     */
    public CallOptions {
        if (videoEnabled) {
            if (videoWidth < 2 || videoWidth % 2 != 0) {
                throw new IllegalArgumentException("videoWidth must be even and >= 2, got " + videoWidth);
            }
            if (videoHeight < 2 || videoHeight % 2 != 0) {
                throw new IllegalArgumentException("videoHeight must be even and >= 2, got " + videoHeight);
            }
            if (videoFps < 1) {
                throw new IllegalArgumentException("videoFps must be >= 1, got " + videoFps);
            }
            if (videoBitrateBps < 1) {
                throw new IllegalArgumentException("videoBitrateBps must be >= 1, got " + videoBitrateBps);
            }
        }
    }

    /**
     * Returns options for an audio-only call.
     *
     * @return an audio-only configuration
     */
    public static CallOptions audio() {
        return new CallOptions(false, 0, 0, 0, 0);
    }

    /**
     * Returns options for an audio-and-video call at the default SD
     * profile: 640x480 at 30 fps and 800 kbps.
     *
     * @return an SD audio-and-video configuration
     */
    public static CallOptions video() {
        return video(640, 480, 30, 800_000);
    }

    /**
     * Returns options for an audio-and-video call at the given
     * dimensions, 30 fps, with the bitrate auto-derived from the pixel
     * count.
     *
     * @param width  frame width in pixels
     * @param height frame height in pixels
     * @return an audio-and-video configuration
     */
    public static CallOptions video(int width, int height) {
        return video(width, height, 30, defaultBitrate(width, height, 30));
    }

    /**
     * Returns options for an audio-and-video call at the given
     * dimensions, frame rate, and bitrate.
     *
     * @param width      frame width in pixels
     * @param height     frame height in pixels
     * @param fps        target frame rate
     * @param bitrateBps target encoder bitrate in bits per second
     * @return an audio-and-video configuration
     */
    public static CallOptions video(int width, int height, int fps, int bitrateBps) {
        return new CallOptions(true, width, height, fps, bitrateBps);
    }

    /**
     * Computes a heuristic bitrate target for a resolution and frame
     * rate.
     *
     * <p>The estimate follows WebRTC's typical SD and HD operating
     * points at roughly 0.1 bits per pixel per frame, clamped to a
     * 64 kbps floor.
     *
     * @param width  frame width in pixels
     * @param height frame height in pixels
     * @param fps    target frame rate
     * @return a starting bitrate in bits per second
     */
    private static int defaultBitrate(int width, int height, int fps) {
        return Math.max(64_000, width * height * fps / 10);
    }
}
