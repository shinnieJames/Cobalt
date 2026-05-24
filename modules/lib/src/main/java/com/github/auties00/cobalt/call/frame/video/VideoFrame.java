package com.github.auties00.cobalt.call.frame.video;

import java.util.Objects;

/**
 * One frame of raw video in I420 (YUV 4:2:0 planar) layout — Y plane
 * first, then U, then V. The byte size of {@code yuvI420} is always
 * {@code width*height + 2*(width/2)*(height/2)}; both dimensions
 * must be even.
 *
 * <p>{@code width} and {@code height} are carried per-frame (not
 * fixed at call-options time) because the decoder may produce
 * different resolutions across a single call as the peer adapts to
 * bandwidth — the SPS in each VP8/H.264 keyframe authoritatively
 * sets the picture size.
 *
 * @param yuvI420 the I420 planar bytes; never {@code null}
 * @param width   frame width in pixels; even and ≥ 2
 * @param height  frame height in pixels; even and ≥ 2
 * @param ptsMs   the presentation timestamp in milliseconds,
 *                monotonic within a call
 */
public record VideoFrame(byte[] yuvI420, int width, int height, long ptsMs) {
    /**
     * Compact constructor — null-checks and validates dimensions.
     */
    public VideoFrame {
        Objects.requireNonNull(yuvI420, "yuvI420 cannot be null");
        if (width < 2 || width % 2 != 0) {
            throw new IllegalArgumentException("width must be even and ≥ 2, got " + width);
        }
        if (height < 2 || height % 2 != 0) {
            throw new IllegalArgumentException("height must be even and ≥ 2, got " + height);
        }
        var expected = width * height + 2 * (width / 2) * (height / 2);
        if (yuvI420.length != expected) {
            throw new IllegalArgumentException(
                    "yuvI420 must be " + expected + " bytes for " + width + "×" + height
                            + " (Y + U + V), got " + yuvI420.length);
        }
    }
}
