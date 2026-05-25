package com.github.auties00.cobalt.call.filter;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Covers {@link PixelFormats} conversions into I420: RGBA, BGR24, and NV12 inputs each yield a frame
 * whose plane byte count matches the I420 layout, and malformed inputs (wrong buffer length, odd or
 * non-positive dimensions) are rejected.
 */
class PixelFormatsTest {
    @Test
    @DisplayName("RGBA -> I420 produces a frame of the right byte count")
    void rgbaToI420Sizes() {
        int w = 4, h = 4;
        var rgba = new byte[w * h * 4];
        var frame = PixelFormats.rgbaToI420(rgba, w, h, 0L);
        assertNotNull(frame);
        assertEquals(w, frame.width());
        assertEquals(h, frame.height());
        // I420: y + u + v = w*h + 2 * (w/2)*(h/2)
        var expected = w * h + 2 * (w / 2) * (h / 2);
        assertEquals(expected, frame.yuvI420().length);
    }

    @Test
    @DisplayName("BGR24 -> I420 produces a frame of the right byte count")
    void bgr24ToI420Sizes() {
        int w = 4, h = 4;
        var bgr = new byte[w * h * 3];
        var frame = PixelFormats.bgr24ToI420(bgr, w, h, 0L);
        var expected = w * h + 2 * (w / 2) * (h / 2);
        assertEquals(expected, frame.yuvI420().length);
    }

    @Test
    @DisplayName("NV12 -> I420 produces a frame of the right byte count")
    void nv12ToI420Sizes() {
        int w = 4, h = 4;
        var ySize = w * h;
        var uvSize = (w / 2) * (h / 2);
        var nv12 = new byte[ySize + 2 * uvSize];
        var frame = PixelFormats.nv12ToI420(nv12, w, h, 0L);
        assertEquals(ySize + 2 * uvSize, frame.yuvI420().length);
    }

    @Test
    @DisplayName("Wrong-length input is rejected")
    void rejectsWrongLength() {
        assertThrows(IllegalArgumentException.class,
                () -> PixelFormats.rgbaToI420(new byte[10], 4, 4, 0L));
        assertThrows(IllegalArgumentException.class,
                () -> PixelFormats.bgr24ToI420(new byte[10], 4, 4, 0L));
        assertThrows(IllegalArgumentException.class,
                () -> PixelFormats.nv12ToI420(new byte[10], 4, 4, 0L));
    }

    @Test
    @DisplayName("Odd or non-positive dimensions are rejected")
    void rejectsBadDimensions() {
        assertThrows(IllegalArgumentException.class,
                () -> PixelFormats.rgbaToI420(new byte[3], 3, 4, 0L));
        assertThrows(IllegalArgumentException.class,
                () -> PixelFormats.rgbaToI420(new byte[12], 4, 3, 0L));
    }
}
