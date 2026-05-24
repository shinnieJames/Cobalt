package com.github.auties00.cobalt.call;

/**
 * Configuration for a placed or accepted call. Audio is always on
 * (16 kHz mono — fixed by WhatsApp's wire profile, see
 * {@link com.github.auties00.cobalt.call.frame.audio.AudioFrame}); video is
 * optional and, when on, carries the negotiated picture size.
 *
 * <p>Use the static factories rather than the canonical constructor:
 *
 * <pre>{@code
 *   CallOptions.audioOnly()            // voice call
 *   CallOptions.audioVideo()           // 640×480 @ 30 fps default
 *   CallOptions.audioVideo(1280, 720)  // HD
 * }</pre>
 *
 * @param videoEnabled    {@code true} for an audio + video call,
 *                        {@code false} for audio-only
 * @param videoWidth      frame width in pixels; even and ≥ 2 if
 *                        {@code videoEnabled}, ignored otherwise
 * @param videoHeight     frame height in pixels; even and ≥ 2 if
 *                        {@code videoEnabled}, ignored otherwise
 * @param videoFps        target frame rate; ≥ 1 if
 *                        {@code videoEnabled}
 * @param videoBitrateBps target encoder bitrate in bits per second;
 *                        ≥ 1 if {@code videoEnabled}
 */
public record CallOptions(
        boolean videoEnabled,
        int videoWidth,
        int videoHeight,
        int videoFps,
        int videoBitrateBps
) {
    /**
     * Compact constructor — validates dimensions only when video is
     * enabled.
     */
    public CallOptions {
        if (videoEnabled) {
            if (videoWidth < 2 || videoWidth % 2 != 0) {
                throw new IllegalArgumentException("videoWidth must be even and ≥ 2, got " + videoWidth);
            }
            if (videoHeight < 2 || videoHeight % 2 != 0) {
                throw new IllegalArgumentException("videoHeight must be even and ≥ 2, got " + videoHeight);
            }
            if (videoFps < 1) {
                throw new IllegalArgumentException("videoFps must be ≥ 1, got " + videoFps);
            }
            if (videoBitrateBps < 1) {
                throw new IllegalArgumentException("videoBitrateBps must be ≥ 1, got " + videoBitrateBps);
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
     * Returns options for an audio + video call at 640×480, 30 fps,
     * 800 kbps — Cobalt's default WebRTC SD profile.
     *
     * @return an SD audio + video configuration
     */
    public static CallOptions video() {
        return video(640, 480, 30, 800_000);
    }

    /**
     * Returns options for an audio + video call at the given
     * dimensions, 30 fps, with bitrate auto-derived from the pixel
     * count.
     *
     * @param width  frame width in pixels
     * @param height frame height in pixels
     * @return an audio + video configuration
     */
    public static CallOptions video(int width, int height) {
        return video(width, height, 30, defaultBitrate(width, height, 30));
    }

    /**
     * Returns options for an audio + video call at the given
     * dimensions, frame rate, and bitrate.
     *
     * @param width      frame width in pixels
     * @param height     frame height in pixels
     * @param fps        target frame rate
     * @param bitrateBps target encoder bitrate in bits per second
     * @return an audio + video configuration
     */
    public static CallOptions video(int width, int height, int fps, int bitrateBps) {
        return new CallOptions(true, width, height, fps, bitrateBps);
    }

    /**
     * Heuristic bitrate target for a given resolution + frame rate,
     * matching WebRTC's typical SD/HD operating points: roughly
     * {@code 0.1 bits-per-pixel-per-frame}.
     *
     * @param width  frame width in pixels
     * @param height frame height in pixels
     * @param fps    target frame rate
     * @return a reasonable starting bitrate in bits per second
     */
    private static int defaultBitrate(int width, int height, int fps) {
        return Math.max(64_000, width * height * fps / 10);
    }
}
