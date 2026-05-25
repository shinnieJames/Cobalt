package com.github.auties00.cobalt.call.internal.video.h264;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Smoke tests for the openh264 FFM bindings and the {@link H264Encoder} / {@link H264Decoder} wrapper pair. The tests
 * fail loudly if libopenh264 is not loadable on the running platform; silently skipping would let a broken native
 * bundle ship green.
 */
public class H264Test {

    @Test
    public void encodeDecodeRoundTrip() {
        var width = 64;
        var height = 64;
        try (var enc = new H264Encoder(width, height, 100_000, 30);
             var dec = new H264Decoder()) {
            var yuv = new byte[enc.frameByteSize()];
            for (var i = 0; i < width * height; i++) {
                yuv[i] = (byte) (i & 0xff);
            }
            // openh264 emits SPS and PPS NALs with the first IDR, so a single forced keyframe is enough for a
            // fresh decoder to produce a complete picture
            var pkt = enc.encode(yuv, 0L, true);
            assertNotNull(pkt, "encoder should emit a packet for a forced keyframe");
            assertTrue(pkt.keyFrame(), "first packet must be a keyframe");
            assertTrue(pkt.payload().length > 0);
            var decoded = dec.decode(pkt.payload());
            assertNotNull(decoded, "decoder should produce a frame from the keyframe");
            assertEquals(width, decoded.width());
            assertEquals(height, decoded.height());
            assertEquals(enc.frameByteSize(), decoded.yuvI420().length);
        }
    }

    @Test
    public void rejectsBadDimensions() {
        assertThrows(IllegalArgumentException.class, () -> new H264Encoder(0, 64, 1, 30));
        assertThrows(IllegalArgumentException.class, () -> new H264Encoder(63, 64, 1, 30));
        assertThrows(IllegalArgumentException.class, () -> new H264Encoder(64, 64, 0, 30));
        assertThrows(IllegalArgumentException.class, () -> new H264Encoder(64, 64, 1, 0));
    }
}
