package com.github.auties00.cobalt.call.filter;

import com.github.auties00.cobalt.call.frame.video.VideoFrame;

import java.util.Objects;

/**
 * Converts common camera and screen-capture pixel layouts into the I420 layout the call video
 * pipeline expects.
 *
 * <p>Sources such as webcams and desktop capture commonly deliver RGBA, packed RGB, packed BGR, or
 * NV12 buffers, whereas the encoder consumes {@link VideoFrame} instances in I420 (YUV 4:2:0 planar)
 * layout. Each public method takes one such buffer plus its dimensions and a presentation timestamp
 * and returns a freshly allocated I420 {@link VideoFrame}. The RGB-family conversions apply a colour
 * matrix; the NV12 conversion only deinterleaves the chroma plane, since NV12 and I420 already share
 * the same luma plane and 4:2:0 subsampling. All methods reject buffers whose length does not match
 * the declared dimensions and reject odd dimensions.
 *
 * <p>The class is final, stateless, and not instantiable; every method is static.
 *
 * @implNote This implementation uses the BT.601 colour matrix with 8-bit fixed-point coefficients,
 * matching FFmpeg's default for 8-bit-per-channel YUV/RGB conversion. At 720x480 and 30 fps the
 * per-pixel work is roughly 10 megapixels per second and negligible on a modern CPU; at 1080p and
 * 30 fps it becomes measurable.
 */
public final class PixelFormats {
    /**
     * BT.601 red coefficient for the luma (Y) channel, in 8-bit fixed point.
     */
    private static final int CY_R = 66;

    /**
     * BT.601 green coefficient for the luma (Y) channel, in 8-bit fixed point.
     */
    private static final int CY_G = 129;

    /**
     * BT.601 blue coefficient for the luma (Y) channel, in 8-bit fixed point.
     */
    private static final int CY_B = 25;

    /**
     * BT.601 red coefficient for the blue-difference chroma (U) channel, in 8-bit fixed point.
     */
    private static final int CU_R = -38;

    /**
     * BT.601 green coefficient for the blue-difference chroma (U) channel, in 8-bit fixed point.
     */
    private static final int CU_G = -74;

    /**
     * BT.601 blue coefficient for the blue-difference chroma (U) channel, in 8-bit fixed point.
     */
    private static final int CU_B = 112;

    /**
     * BT.601 red coefficient for the red-difference chroma (V) channel, in 8-bit fixed point.
     */
    private static final int CV_R = 112;

    /**
     * BT.601 green coefficient for the red-difference chroma (V) channel, in 8-bit fixed point.
     */
    private static final int CV_G = -94;

    /**
     * BT.601 blue coefficient for the red-difference chroma (V) channel, in 8-bit fixed point.
     */
    private static final int CV_B = -18;

    /**
     * Prevents instantiation of this static-only utility class.
     *
     * @throws AssertionError always
     */
    private PixelFormats() {
        throw new AssertionError("PixelFormats is not instantiable");
    }

    /**
     * Converts a row-major top-down RGBA buffer into a fresh I420 {@link VideoFrame}.
     *
     * <p>The source holds four bytes per pixel in {@code [R, G, B, A]} order; the alpha channel is
     * ignored. The buffer length must equal {@code width * height * 4} and both dimensions must be
     * even.
     *
     * @param rgba   the source pixels in {@code [R, G, B, A]} order; never {@code null}
     * @param width  the frame width in pixels; even and at least {@code 2}
     * @param height the frame height in pixels; even and at least {@code 2}
     * @param ptsMs  the presentation timestamp to stamp on the output frame
     * @return the converted I420 frame
     * @throws NullPointerException     if {@code rgba} is {@code null}
     * @throws IllegalArgumentException if {@code rgba.length} is not {@code width * height * 4}, or a
     *                                  dimension is odd or less than {@code 2}
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
     * Converts a packed BGR24 buffer into a fresh I420 {@link VideoFrame}.
     *
     * <p>The source holds three bytes per pixel in {@code [B, G, R]} order, the layout most v4l2
     * cameras produce. The buffer length must equal {@code width * height * 3} and both dimensions
     * must be even.
     *
     * @param bgr    the source pixels in {@code [B, G, R]} order; never {@code null}
     * @param width  the frame width in pixels; even and at least {@code 2}
     * @param height the frame height in pixels; even and at least {@code 2}
     * @param ptsMs  the presentation timestamp to stamp on the output frame
     * @return the converted I420 frame
     * @throws NullPointerException     if {@code bgr} is {@code null}
     * @throws IllegalArgumentException if {@code bgr.length} is not {@code width * height * 3}, or a
     *                                  dimension is odd or less than {@code 2}
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
     * Converts a packed RGB24 buffer into a fresh I420 {@link VideoFrame}.
     *
     * <p>The source holds three bytes per pixel in {@code [R, G, B]} order. The buffer length must
     * equal {@code width * height * 3} and both dimensions must be even.
     *
     * @param rgb    the source pixels in {@code [R, G, B]} order; never {@code null}
     * @param width  the frame width in pixels; even and at least {@code 2}
     * @param height the frame height in pixels; even and at least {@code 2}
     * @param ptsMs  the presentation timestamp to stamp on the output frame
     * @return the converted I420 frame
     * @throws NullPointerException     if {@code rgb} is {@code null}
     * @throws IllegalArgumentException if {@code rgb.length} is not {@code width * height * 3}, or a
     *                                  dimension is odd or less than {@code 2}
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
     * Converts an NV12 buffer into a fresh I420 {@link VideoFrame}.
     *
     * <p>NV12 stores the full-resolution luma (Y) plane followed by a single chroma plane of
     * interleaved {@code U, V} pairs at half resolution in each dimension; it is the layout most
     * macOS and Windows hardware capture pipelines produce. The luma plane is copied unchanged and
     * the interleaved chroma is split into the separate U and V planes I420 requires. The buffer
     * length must equal {@code width * height + 2 * (width / 2) * (height / 2)} and both dimensions
     * must be even.
     *
     * @param nv12   the source NV12 bytes; never {@code null}
     * @param width  the frame width in pixels; even and at least {@code 2}
     * @param height the frame height in pixels; even and at least {@code 2}
     * @param ptsMs  the presentation timestamp to stamp on the output frame
     * @return the converted I420 frame
     * @throws NullPointerException     if {@code nv12} is {@code null}
     * @throws IllegalArgumentException if {@code nv12.length} does not match the expected NV12 size,
     *                                  or a dimension is odd or less than {@code 2}
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
     * Converts a generic interleaved RGB-style buffer into a fresh I420 {@link VideoFrame}.
     *
     * <p>The per-pixel channels are located through {@code stride}, {@code rOff}, {@code gOff}, and
     * {@code bOff}, which lets the RGBA, RGB24, and BGR24 entry points share one conversion loop.
     * Each output luma sample is computed from one source pixel; each output chroma sample is the
     * average of the four source pixels in a 2x2 block, matching 4:2:0 subsampling.
     *
     * @implNote This implementation evaluates the BT.601 matrix in 8-bit fixed point. Luma adds the
     * {@code 128} rounding bias before the {@code >> 8} shift and offsets the result by {@code 16}
     * to the studio-swing floor; chroma applies the same rounding bias and shift and offsets by
     * {@code 128} to centre the signed difference. All three outputs are clamped to {@code [0, 255]}
     * via {@link #clamp(int)}.
     *
     * @param src    the interleaved source bytes
     * @param width  the frame width in pixels
     * @param height the frame height in pixels
     * @param ptsMs  the presentation timestamp to stamp on the output frame
     * @param stride the number of bytes per pixel
     * @param rOff   the red-channel byte offset within a pixel
     * @param gOff   the green-channel byte offset within a pixel
     * @param bOff   the blue-channel byte offset within a pixel
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
     * Clamps an integer to the inclusive range {@code [0, 255]}.
     *
     * @param v the value to clamp
     * @return {@code 0} if {@code v} is negative, {@code 255} if {@code v} exceeds {@code 255},
     *         otherwise {@code v}
     */
    private static int clamp(int v) {
        if (v < 0) return 0;
        if (v > 255) return 255;
        return v;
    }

    /**
     * Validates that both dimensions are even and at least {@code 2}.
     *
     * @param width  the frame width in pixels
     * @param height the frame height in pixels
     * @throws IllegalArgumentException if either dimension is odd or less than {@code 2}
     */
    private static void validateDimensions(int width, int height) {
        if (width < 2 || width % 2 != 0) {
            throw new IllegalArgumentException("width must be even and >= 2, got " + width);
        }
        if (height < 2 || height % 2 != 0) {
            throw new IllegalArgumentException("height must be even and >= 2, got " + height);
        }
    }
}
