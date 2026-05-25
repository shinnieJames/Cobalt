package com.github.auties00.cobalt.call.frame.video;

import java.util.Objects;

/**
 * Holds one frame of raw video in I420 (YUV 4:2:0 planar) layout.
 *
 * <p>The pixel buffer stores the three planes back to back: the full-resolution Y (luma) plane
 * first, then the half-resolution U (chroma blue) plane, then the half-resolution V (chroma red)
 * plane. The byte length of {@code yuvI420} is therefore always
 * {@code width*height + 2*(width/2)*(height/2)}, and both dimensions must be even so the chroma
 * planes have integral sizes. The compact constructor rejects any buffer whose length does not
 * match the declared dimensions.
 *
 * <p>{@code width} and {@code height} are carried per frame rather than fixed for the lifetime of a
 * call: a single call may switch resolution as the decoder follows the peer's bandwidth adaptation,
 * and the keyframe header of each VP8 or H.264 picture authoritatively sets the picture size. The
 * presentation timestamp {@code ptsMs} is measured in milliseconds and is monotonically
 * non-decreasing within one call.
 *
 * @param yuvI420 the I420 planar bytes laid out as Y then U then V; never {@code null}, and exactly
 *                {@code width*height + 2*(width/2)*(height/2)} bytes long
 * @param width   the frame width in pixels; even and at least {@code 2}
 * @param height  the frame height in pixels; even and at least {@code 2}
 * @param ptsMs   the presentation timestamp in milliseconds, monotonically non-decreasing within a
 *                single call
 */
public record VideoFrame(byte[] yuvI420, int width, int height, long ptsMs) {
    /**
     * Validates the planar buffer and the frame dimensions.
     *
     * <p>Rejects a {@code null} buffer, an odd or sub-{@code 2} {@code width} or {@code height}, and
     * a buffer whose length does not equal {@code width*height + 2*(width/2)*(height/2)}. The frame
     * is otherwise stored as supplied; the buffer reference is shared rather than copied.
     *
     * @throws NullPointerException     if {@code yuvI420} is {@code null}
     * @throws IllegalArgumentException if {@code width} or {@code height} is odd or less than
     *                                  {@code 2}, or if {@code yuvI420} does not have the expected
     *                                  length for the declared dimensions
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
