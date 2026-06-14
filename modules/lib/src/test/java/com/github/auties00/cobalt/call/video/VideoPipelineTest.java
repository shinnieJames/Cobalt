package com.github.auties00.cobalt.call.video;
import com.github.auties00.cobalt.call.internal.NoopCallService;

import com.github.auties00.cobalt.call.stream.VideoFrame;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.jid.JidServer;
import org.junit.jupiter.api.Test;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import com.github.auties00.cobalt.call.internal.CallTestRuntimes;

/**
 * End-to-end tests for the {@link VideoPipeline}: I420 captures encode through the
 * {@link VideoCodec} adapter, inbound packets decode back onto the runtime's remote-video stream,
 * {@link VideoPipeline#requestKeyframe()} forces a keyframe on the next encode, and
 * bandwidth-estimation adjustments propagate. Each case runs against a no-op call-engine recipient
 * and synthetic 64x64 I420 frames.
 */
public class VideoPipelineTest {

    private static final Jid PEER = Jid.of("12345", JidServer.user());

    private static final Jid SELF = Jid.of("99999", JidServer.user());

    // VP8 and openh264 both require even dimensions of at least 64 for sane output.
    private static final int WIDTH = 64;

    private static final int HEIGHT = 64;

    private static final int FPS = 30;

    private static final int BITRATE = 100_000;

    @Test
    public void vp8EncodeAndDecodeRoundTrip() throws Exception {
        var engine = new NoopCallService();
        var call = CallTestRuntimes.of(engine, "id-vid-1", PEER, PEER, SELF, true,
                true);
        var outbound = new LinkedBlockingQueue<VideoPacket>();
        try (var pipeline = new VideoPipeline(call, outbound::offer,
                VideoCodec.vp8(WIDTH, HEIGHT, BITRATE, FPS),
                new VideoPipelineOptions(WIDTH, HEIGHT, FPS, BITRATE, true))) {
            pipeline.start();

            call.videoOut().write(new VideoFrame(gradientI420(), WIDTH, HEIGHT, 0L));

            var packet = outbound.poll(5, TimeUnit.SECONDS);
            assertNotNull(packet, "encoder should emit a packet");
            assertTrue(packet.keyFrame(), "first packet must be a keyframe (keyframeOnStart=true)");
            assertTrue(packet.payload().length > 0);
            assertEquals(WIDTH, packet.width());
            assertEquals(HEIGHT, packet.height());

            pipeline.feedInboundPacket(packet);
            var decoded = call.videoIn().read();
            assertNotNull(decoded, "decoder should produce a frame on the remote source");
            assertEquals(WIDTH, decoded.width());
            assertEquals(HEIGHT, decoded.height());
        }
    }

    @Test
    public void h264EncodeAndDecodeRoundTrip() throws Exception {
        var engine = new NoopCallService();
        var call = CallTestRuntimes.of(engine, "id-vid-2", PEER, PEER, SELF, true,
                true);
        var outbound = new LinkedBlockingQueue<VideoPacket>();
        try (var pipeline = new VideoPipeline(call, outbound::offer,
                VideoCodec.h264(WIDTH, HEIGHT, BITRATE, FPS),
                new VideoPipelineOptions(WIDTH, HEIGHT, FPS, BITRATE, true))) {
            pipeline.start();

            call.videoOut().write(new VideoFrame(gradientI420(), WIDTH, HEIGHT, 0L));

            var packet = outbound.poll(5, TimeUnit.SECONDS);
            assertNotNull(packet, "encoder should emit a packet");
            assertTrue(packet.keyFrame());
            assertTrue(packet.payload().length > 0);

            pipeline.feedInboundPacket(packet);
            var decoded = call.videoIn().read();
            assertNotNull(decoded);
            assertEquals(WIDTH, decoded.width());
            assertEquals(HEIGHT, decoded.height());
        }
    }

    @Test
    public void requestKeyframeForcesIdrOnNextEncode() throws Exception {
        var engine = new NoopCallService();
        var call = CallTestRuntimes.of(engine, "id-vid-3", PEER, PEER, SELF, true,
                true);
        var outbound = new LinkedBlockingQueue<VideoPacket>();
        try (var pipeline = new VideoPipeline(call, outbound::offer,
                VideoCodec.vp8(WIDTH, HEIGHT, BITRATE, FPS),
                new VideoPipelineOptions(WIDTH, HEIGHT, FPS, BITRATE, true))) {
            pipeline.start();

            call.videoOut().write(new VideoFrame(gradientI420(), WIDTH, HEIGHT, 0L));
            var first = outbound.poll(5, TimeUnit.SECONDS);
            assertNotNull(first);
            assertTrue(first.keyFrame());

            for (var i = 1; i < 4; i++) {
                call.videoOut().write(new VideoFrame(gradientI420(), WIDTH, HEIGHT, i * 33L));
                var pkt = outbound.poll(5, TimeUnit.SECONDS);
                // Don't assert non-keyframe: VP8 may insert its own keyframes, so only verify output.
                assertNotNull(pkt);
            }

            pipeline.requestKeyframe();
            call.videoOut().write(new VideoFrame(gradientI420(), WIDTH, HEIGHT, 4 * 33L));
            var forced = outbound.poll(5, TimeUnit.SECONDS);
            assertNotNull(forced);
            assertTrue(forced.keyFrame(),
                    "requestKeyframe() must force the next outbound packet to be a keyframe");
        }
    }

    @Test
    public void adjustBitrateAcceptsNewTarget() {
        var engine = new NoopCallService();
        var call = CallTestRuntimes.of(engine, "id-vid-4", PEER, PEER, SELF, true,
                true);
        try (var pipeline = new VideoPipeline(call, p -> {
        }, VideoCodec.vp8(WIDTH, HEIGHT, BITRATE, FPS),
                new VideoPipelineOptions(WIDTH, HEIGHT, FPS, BITRATE, false))) {
            pipeline.adjustBitrate(500_000);
            pipeline.adjustBitrate(2_000_000);
            assertThrows(IllegalArgumentException.class, () -> pipeline.adjustBitrate(0));
        }
    }

    @Test
    public void mismatchedCodecResolutionRejected() {
        var engine = new NoopCallService();
        var call = CallTestRuntimes.of(engine, "id-vid-5", PEER, PEER, SELF, true,
                true);
        var codec = VideoCodec.vp8(WIDTH, HEIGHT, BITRATE, FPS);
        try {
            assertThrows(IllegalArgumentException.class, () -> new VideoPipeline(
                    call, p -> {
            }, codec, new VideoPipelineOptions(640, 480, FPS, BITRATE, false)));
        } finally {
            codec.close();
        }
    }

    @Test
    public void closeIsIdempotent() {
        var engine = new NoopCallService();
        var call = CallTestRuntimes.of(engine, "id-vid-6", PEER, PEER, SELF, true,
                true);
        var pipeline = new VideoPipeline(call, p -> {
        }, VideoCodec.vp8(WIDTH, HEIGHT, BITRATE, FPS),
                new VideoPipelineOptions(WIDTH, HEIGHT, FPS, BITRATE, false));
        pipeline.start();
        pipeline.close();
        pipeline.close();
        pipeline.close();
    }

    private static byte[] gradientI420() {
        var ySize = WIDTH * HEIGHT;
        var uvSize = (WIDTH / 2) * (HEIGHT / 2);
        var frame = new byte[ySize + 2 * uvSize];
        for (var i = 0; i < ySize; i++) {
            frame[i] = (byte) (i & 0xff);
        }
        // 0x80 is neutral chroma; filling U/V explicitly avoids green artifacts in the frame.
        for (var i = ySize; i < frame.length; i++) {
            frame[i] = (byte) 0x80;
        }
        return frame;
    }

}
