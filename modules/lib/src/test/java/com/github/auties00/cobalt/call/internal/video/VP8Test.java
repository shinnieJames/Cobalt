package com.github.auties00.cobalt.call.internal.video;

import com.github.auties00.cobalt.call.internal.video.vpx.VP8Decoder;
import com.github.auties00.cobalt.call.internal.video.vpx.VP8Encoder;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Smoke tests for the libvpx FFM bindings and the {@link VP8Encoder} / {@link VP8Decoder}
 * wrapper pair. The tests fail loudly if libvpx is not loadable on the running platform
 * rather than skipping, so a broken native bundle cannot ship green.
 */
public class VP8Test {

    @Test
    public void encodeDecodeRoundTrip() {
        var width = 64;
        var height = 64;
        try (var enc = new VP8Encoder(width, height, 100_000, 30);
             var dec = new VP8Decoder()) {
            var yuv = new byte[enc.frameByteSize()];
            // Fill the Y plane with a gradient so the encoder has
            // some real content to compress; U/V stay at zero
            // (which is grey 128 after the unsigned cast).
            for (var i = 0; i < width * height; i++) {
                yuv[i] = (byte) (i & 0xff);
            }
            var packets = enc.encode(yuv, 0L, true);
            assertFalse(packets.isEmpty(), "encoder should emit at least one packet for a forced keyframe");
            var first = packets.get(0);
            assertTrue(first.keyFrame(), "first packet must be a keyframe");
            assertTrue(first.payload().length > 0);
            var decoded = dec.decode(first.payload());
            assertNotNull(decoded, "decoder should produce a frame from the keyframe");
            assertEquals(width, decoded.width());
            assertEquals(height, decoded.height());
            assertEquals(enc.frameByteSize(), decoded.yuvI420().length);
        }
    }

    @Test
    public void rejectsBadDimensions() {
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> new VP8Encoder(0, 64, 1, 30));
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> new VP8Encoder(63, 64, 1, 30));
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> new VP8Encoder(64, 64, 0, 30));
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> new VP8Encoder(64, 64, 1, 0));
    }
}
