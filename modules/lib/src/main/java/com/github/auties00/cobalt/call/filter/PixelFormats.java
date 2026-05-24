package com.github.auties00.cobalt.call.filter;

import com.github.auties00.cobalt.call.frame.video.VideoFrame;

import java.util.Objects;

/**
 * Pure-Java pixel-format converters for video frames — turns the
 * various RGB / BGR / NV12 layouts cameras and screen-capture
 * sources commonly produce into the I420 layout the call's video
 * pipelines expect.
 *
 * <p>Performance notes:
 *
 * <ul>
 *   <li>BT.601 conversion matrix; matches what FFmpeg uses by
 *       default for 8-bit-per-channel YUV ↔ RGB.</li>
 *   <li>720×480 @ 30 fps is ~10 megapixels/s — trivial CPU.
 *       Higher resolutions (1080p @ 30) become non-trivial.</li>
 * </ul>
 *
 * <p>This class is final and stateless; every method is static.
 */
public final class PixelFormats {
    /**
     * BT.601 RGB → Y coefficients, fixed-point 8-bit-shifted.
     */
    private static final int CY_R = 66;
    private static final int CY_G = 129;
    private static final int CY_B = 25;

    /**
     * BT.601 RGB → U coefficients.
     */
    private static final int CU_R = -38;
    private static final int CU_G = -74;
    private static final int CU_B = 112;

    /**
     * BT.601 RGB → V coefficients.
     */
    private static final int CV_R = 112;
    private static final int CV_G = -94;
    private static final int CV_B = -18;

    /**
     * Prevents instantiation.
     */
    private PixelFormats() {
        throw new AssertionError("PixelFormats is not instantiable");
    }

    /**
     * Converts an RGBA byte buffer ({@code [R G B A]} per pixel,
     * row-major top-down) into a fresh I420 {@link VideoFrame}.
     *
     * @param rgba   the source pixels; length must be
     *               {@code width * height * 4}
     * @param width  frame width in pixels (even)
     * @param height frame height in pixels (even)
     * @param ptsMs  the timestamp to stamp on the output frame
     * @return the converted frame
     * @throws IllegalArgumentException if {@code rgba.length} is
     *                                  wrong, or dimensions
     *                                  aren't even
     */
    public static VideoFrame rgbaToI420(byte[] rgba, int width, int height, long ptsMs) {
        Objects.requireNonNull(rgba, "rgba cannot be null");
        validateDimensions(width, height);
        var expected = width * height * 4;
        if (rgba.length != expected) {
            throw new IllegalArgumentException(
                    "rgba must be " + expected + " bytes, got " + rgba.length);
        }
        return convertInterleaved(rgba, width, height, ptsMs, 4, 0, 1, 2);
    }

    /**
     * Converts a packed BGR byte buffer ({@code [B G R]} per
     * pixel) into a fresh I420 {@link VideoFrame}. {@code BGR24}
     * is what most v4l2 cameras produce.
     *
     * @param bgr    the source pixels; length must be
     *               {@code width * height * 3}
     * @param width  frame width in pixels (even)
     * @param height frame height in pixels (even)
     * @param ptsMs  the timestamp to stamp on the output frame
     * @return the converted frame
     */
    public static VideoFrame bgr24ToI420(byte[] bgr, int width, int height, long ptsMs) {
        Objects.requireNonNull(bgr, "bgr cannot be null");
        validateDimensions(width, height);
        var expected = width * height * 3;
        if (bgr.length != expected) {
            throw new IllegalArgumentException(
                    "bgr must be " + expected + " bytes, got " + bgr.length);
        }
        return convertInterleaved(bgr, width, height, ptsMs, 3, 2, 1, 0);
    }

    /**
     * Converts a packed RGB byte buffer ({@code [R G B]} per
     * pixel) into a fresh I420 {@link VideoFrame}.
     *
     * @param rgb    the source pixels
     * @param width  frame width in pixels (even)
     * @param height frame height in pixels (even)
     * @param ptsMs  the timestamp to stamp on the output frame
     * @return the converted frame
     */
    public static VideoFrame rgb24ToI420(byte[] rgb, int width, int height, long ptsMs) {
        Objects.requireNonNull(rgb, "rgb cannot be null");
        validateDimensions(width, height);
        var expected = width * height * 3;
        if (rgb.length != expected) {
            throw new IllegalArgumentException(
                    "rgb must be " + expected + " bytes, got " + rgb.length);
        }
        return convertInterleaved(rgb, width, height, ptsMs, 3, 0, 1, 2);
    }

    /**
     * Converts an NV12 buffer (Y plane followed by interleaved
     * UV plane — what most macOS / Windows hardware capture
     * pipelines produce) into a fresh I420 {@link VideoFrame}.
     *
     * @param nv12   the source bytes; length must be
     *               {@code width*height + 2*(width/2)*(height/2)}
     * @param width  frame width in pixels (even)
     * @param height frame height in pixels (even)
     * @param ptsMs  the timestamp
     * @return the converted frame
     */
    public static VideoFrame nv12ToI420(byte[] nv12, int width, int height, long ptsMs) {
        Objects.requireNonNull(nv12, "nv12 cannot be null");
        validateDimensions(width, height);
        var ySize = width * height;
        var uvWidth = width / 2;
        var uvHeight = height / 2;
        var uvSize = uvWidth * uvHeight;
        var expected = ySize + 2 * uvSize;
        if (nv12.length != expected) {
            throw new IllegalArgumentException(
                    "nv12 must be " + expected + " bytes, got " + nv12.length);
        }
        var i420 = new byte[expected];
        System.arraycopy(nv12, 0, i420, 0, ySize);
        for (var i = 0; i < uvSize; i++) {
            i420[ySize + i] = nv12[ySize + 2 * i];
            i420[ySize + uvSize + i] = nv12[ySize + 2 * i + 1];
        }
        return new VideoFrame(i420, width, height, ptsMs);
    }

    /**
     * Convert a generic interleaved RGB-ish buffer at
     * {@code stride} bytes per pixel into I420, picking the
     * channel indices via the supplied offsets.
     *
     * @param src    interleaved source
     * @param width  width
     * @param height height
     * @param ptsMs  timestamp to stamp on the output
     * @param stride bytes per pixel
     * @param rOff   R-channel byte offset within a pixel
     * @param gOff   G-channel byte offset
     * @param bOff   B-channel byte offset
     * @return the converted I420 frame
     */
    private static VideoFrame convertInterleaved(byte[] src, int width, int height, long ptsMs,
                                                 int stride, int rOff, int gOff, int bOff) {
        var ySize = width * height;
        var uvWidth = width / 2;
        var uvHeight = height / 2;
        var uvSize = uvWidth * uvHeight;
        var i420 = new byte[ySize + 2 * uvSize];

        for (var row = 0; row < height; row++) {
            var srcRow = row * width * stride;
            var yRow = row * width;
            for (var col = 0; col < width; col++) {
                var p = srcRow + col * stride;
                var r = src[p + rOff] & 0xFF;
                var g = src[p + gOff] & 0xFF;
                var b = src[p + bOff] & 0xFF;
                var y = ((CY_R * r + CY_G * g + CY_B * b + 128) >> 8) + 16;
                i420[yRow + col] = (byte) clamp(y);
            }
        }

        for (var row = 0; row < uvHeight; row++) {
            for (var col = 0; col < uvWidth; col++) {
                var srcRow0 = (row * 2) * width * stride;
                var srcRow1 = (row * 2 + 1) * width * stride;
                var srcCol0 = (col * 2) * stride;
                var srcCol1 = (col * 2 + 1) * stride;
                var rSum = (src[srcRow0 + srcCol0 + rOff] & 0xFF)
                           + (src[srcRow0 + srcCol1 + rOff] & 0xFF)
                           + (src[srcRow1 + srcCol0 + rOff] & 0xFF)
                           + (src[srcRow1 + srcCol1 + rOff] & 0xFF);
                var gSum = (src[srcRow0 + srcCol0 + gOff] & 0xFF)
                           + (src[srcRow0 + srcCol1 + gOff] & 0xFF)
                           + (src[srcRow1 + srcCol0 + gOff] & 0xFF)
                           + (src[srcRow1 + srcCol1 + gOff] & 0xFF);
                var bSum = (src[srcRow0 + srcCol0 + bOff] & 0xFF)
                           + (src[srcRow0 + srcCol1 + bOff] & 0xFF)
                           + (src[srcRow1 + srcCol0 + bOff] & 0xFF)
                           + (src[srcRow1 + srcCol1 + bOff] & 0xFF);
                var rAvg = rSum >> 2;
                var gAvg = gSum >> 2;
                var bAvg = bSum >> 2;
                var u = ((CU_R * rAvg + CU_G * gAvg + CU_B * bAvg + 128) >> 8) + 128;
                var v = ((CV_R * rAvg + CV_G * gAvg + CV_B * bAvg + 128) >> 8) + 128;
                var uvIdx = row * uvWidth + col;
                i420[ySize + uvIdx] = (byte) clamp(u);
                i420[ySize + uvSize + uvIdx] = (byte) clamp(v);
            }
        }

        return new VideoFrame(i420, width, height, ptsMs);
    }

    /**
     * Clamps an int to {@code [0, 255]}.
     *
     * @param v the value
     * @return the clamped value
     */
    private static int clamp(int v) {
        if (v < 0) return 0;
        if (v > 255) return 255;
        return v;
    }

    /**
     * Validates that both dimensions are positive even integers.
     *
     * @param width  width
     * @param height height
     */
    private static void validateDimensions(int width, int height) {
        if (width < 2 || width % 2 != 0) {
            throw new IllegalArgumentException("width must be even and ≥ 2, got " + width);
        }
        if (height < 2 || height % 2 != 0) {
            throw new IllegalArgumentException("height must be even and ≥ 2, got " + height);
        }
    }
}
