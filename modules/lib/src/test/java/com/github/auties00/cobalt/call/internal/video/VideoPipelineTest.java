package com.github.auties00.cobalt.call.internal.video;

import com.github.auties00.cobalt.call.frame.video.VideoFrame;
import com.github.auties00.cobalt.call.CallEndReason;
import com.github.auties00.cobalt.call.internal.video.VideoCodec;
import com.github.auties00.cobalt.call.internal.video.VideoPacket;
import com.github.auties00.cobalt.call.internal.video.VideoPipeline;
import com.github.auties00.cobalt.call.internal.video.VideoPipelineOptions;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.jid.JidServer;
import org.junit.jupiter.api.Test;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import com.github.auties00.cobalt.call.internal.CallService;
import com.github.auties00.cobalt.call.ActiveCall;
import com.github.auties00.cobalt.call.CallOptions;

/**
 * End-to-end tests for the {@link VideoPipeline} — verifies that
 * I420 captures encode through the {@link VideoCodec} adapter, that
 * inbound packets decode back through {@link ActiveCall#remoteVideoSource()},
 * that {@link VideoPipeline#requestKeyframe()} forces an IDR/keyframe
 * on the next encode, and that BWE adjustments propagate.
 */
public class VideoPipelineTest {

    /**
     * Peer JID used by every test case.
     */
    private static final Jid PEER = Jid.of("12345", JidServer.user());

    /**
     * Local self JID used by every test case.
     */
    private static final Jid SELF = Jid.of("99999", JidServer.user());

    /**
     * 64×64 keeps tests fast; both VP8 and openh264 require even
     * dimensions ≥ 64 for sane output.
     */
    private static final int WIDTH = 64;

    /**
     * Test frame height — see {@link #WIDTH}.
     */
    private static final int HEIGHT = 64;

    /**
     * Test frame rate.
     */
    private static final int FPS = 30;

    /**
     * Test target bitrate.
     */
    private static final int BITRATE = 100_000;

    /**
     * VP8 outbound capture round-trips through the encoder; the
     * first emitted packet is a forced keyframe, and the decoded
     * frame on the inbound side preserves dimensions.
     */
    @Test
    public void vp8EncodeAndDecodeRoundTrip() throws Exception {
        var engine = new RecordingEngine();
        var call = new ActiveCall(engine, "id-vid-1", PEER, PEER, SELF, true,
                CallOptions.video());
        var outbound = new LinkedBlockingQueue<VideoPacket>();
        try (var pipeline = new VideoPipeline(call, outbound::offer,
                VideoCodec.vp8(WIDTH, HEIGHT, BITRATE, FPS),
                new VideoPipelineOptions(WIDTH, HEIGHT, FPS, BITRATE, true))) {
            pipeline.start();

            call.localVideoSink().write(new VideoFrame(gradientI420(), WIDTH, HEIGHT, 0L));

            var packet = outbound.poll(5, TimeUnit.SECONDS);
            assertNotNull(packet, "encoder should emit a packet");
            assertTrue(packet.keyFrame(), "first packet must be a keyframe (keyframeOnStart=true)");
            assertTrue(packet.payload().length > 0);
            assertEquals(WIDTH, packet.width());
            assertEquals(HEIGHT, packet.height());

            // Feed the packet back to the inbound side; the decoder
            // should produce a frame on remoteVideoSource.
            pipeline.feedInboundPacket(packet);
            var decoded = call.remoteVideoSource().next();
            assertNotNull(decoded, "decoder should produce a frame on the remote source");
            assertEquals(WIDTH, decoded.width());
            assertEquals(HEIGHT, decoded.height());
        }
    }

    /**
     * H.264 outbound capture round-trips through the encoder.
     */
    @Test
    public void h264EncodeAndDecodeRoundTrip() throws Exception {
        var engine = new RecordingEngine();
        var call = new ActiveCall(engine, "id-vid-2", PEER, PEER, SELF, true,
                CallOptions.video());
        var outbound = new LinkedBlockingQueue<VideoPacket>();
        try (var pipeline = new VideoPipeline(call, outbound::offer,
                VideoCodec.h264(WIDTH, HEIGHT, BITRATE, FPS),
                new VideoPipelineOptions(WIDTH, HEIGHT, FPS, BITRATE, true))) {
            pipeline.start();

            call.localVideoSink().write(new VideoFrame(gradientI420(), WIDTH, HEIGHT, 0L));

            var packet = outbound.poll(5, TimeUnit.SECONDS);
            assertNotNull(packet, "encoder should emit a packet");
            assertTrue(packet.keyFrame());
            assertTrue(packet.payload().length > 0);

            pipeline.feedInboundPacket(packet);
            var decoded = call.remoteVideoSource().next();
            assertNotNull(decoded);
            assertEquals(WIDTH, decoded.width());
            assertEquals(HEIGHT, decoded.height());
        }
    }

    /**
     * After the initial keyframe, subsequent frames are P-frames —
     * but {@link VideoPipeline#requestKeyframe()} forces the next
     * encode back into a keyframe.
     */
    @Test
    public void requestKeyframeForcesIdrOnNextEncode() throws Exception {
        var engine = new RecordingEngine();
        var call = new ActiveCall(engine, "id-vid-3", PEER, PEER, SELF, true,
                CallOptions.video());
        var outbound = new LinkedBlockingQueue<VideoPacket>();
        try (var pipeline = new VideoPipeline(call, outbound::offer,
                VideoCodec.vp8(WIDTH, HEIGHT, BITRATE, FPS),
                new VideoPipelineOptions(WIDTH, HEIGHT, FPS, BITRATE, true))) {
            pipeline.start();

            // First frame: forced keyframe per keyframeOnStart=true.
            call.localVideoSink().write(new VideoFrame(gradientI420(), WIDTH, HEIGHT, 0L));
            var first = outbound.poll(5, TimeUnit.SECONDS);
            assertNotNull(first);
            assertTrue(first.keyFrame());

            // Drive a few P-frames.
            for (var i = 1; i < 4; i++) {
                call.localVideoSink().write(new VideoFrame(gradientI420(), WIDTH, HEIGHT, i * 33L));
                var pkt = outbound.poll(5, TimeUnit.SECONDS);
                assertNotNull(pkt);
                // Don't assert non-keyframe here — VP8 may insert
                // its own keyframes; just verify it produced output.
            }

            // Now force a keyframe: the next outbound must be one.
            pipeline.requestKeyframe();
            call.localVideoSink().write(new VideoFrame(gradientI420(), WIDTH, HEIGHT, 4 * 33L));
            var forced = outbound.poll(5, TimeUnit.SECONDS);
            assertNotNull(forced);
            assertTrue(forced.keyFrame(),
                    "requestKeyframe() must force the next outbound packet to be a keyframe");
        }
    }

    /**
     * BWE feedback via {@link VideoPipeline#adjustBitrate(int)} pushes
     * the new target into the VP8 encoder via
     * {@code vpx_codec_enc_config_set} without throwing or stalling
     * the pipeline.
     */
    @Test
    public void adjustBitrateAcceptsNewTarget() {
        var engine = new RecordingEngine();
        var call = new ActiveCall(engine, "id-vid-4", PEER, PEER, SELF, true,
                CallOptions.video());
        try (var pipeline = new VideoPipeline(call, p -> {
        }, VideoCodec.vp8(WIDTH, HEIGHT, BITRATE, FPS),
                new VideoPipelineOptions(WIDTH, HEIGHT, FPS, BITRATE, false))) {
            pipeline.adjustBitrate(500_000);
            pipeline.adjustBitrate(2_000_000);
            assertThrows(IllegalArgumentException.class, () -> pipeline.adjustBitrate(0));
        }
    }

    /**
     * Constructing a pipeline with codec dimensions that disagree
     * with the options is rejected.
     */
    @Test
    public void mismatchedCodecResolutionRejected() {
        var engine = new RecordingEngine();
        var call = new ActiveCall(engine, "id-vid-5", PEER, PEER, SELF, true,
                CallOptions.video());
        var codec = VideoCodec.vp8(WIDTH, HEIGHT, BITRATE, FPS);
        try {
            assertThrows(IllegalArgumentException.class, () -> new VideoPipeline(
                    call, p -> {
            }, codec, new VideoPipelineOptions(640, 480, FPS, BITRATE, false)));
        } finally {
            codec.close();
        }
    }

    /**
     * {@link VideoPipeline#close()} is idempotent and unblocks the
     * decoder thread waiting on the inbound queue.
     */
    @Test
    public void closeIsIdempotent() {
        var engine = new RecordingEngine();
        var call = new ActiveCall(engine, "id-vid-6", PEER, PEER, SELF, true,
                CallOptions.video());
        var pipeline = new VideoPipeline(call, p -> {
        }, VideoCodec.vp8(WIDTH, HEIGHT, BITRATE, FPS),
                new VideoPipelineOptions(WIDTH, HEIGHT, FPS, BITRATE, false));
        pipeline.start();
        pipeline.close();
        pipeline.close();
        pipeline.close();
    }

    /**
     * Generates a {@link #WIDTH} × {@link #HEIGHT} I420 frame whose Y
     * plane is a horizontal gradient and whose U/V planes are
     * neutral — gives the encoder real (compressible) content
     * without depending on randomness.
     *
     * @return the I420 frame bytes
     */
    private static byte[] gradientI420() {
        var ySize = WIDTH * HEIGHT;
        var uvSize = (WIDTH / 2) * (HEIGHT / 2);
        var frame = new byte[ySize + 2 * uvSize];
        for (var i = 0; i < ySize; i++) {
            frame[i] = (byte) (i & 0xff);
        }
        // U + V planes default to 0x80 (neutral chroma) — fill them
        // explicitly so frames don't carry green chroma artifacts.
        for (var i = ySize; i < frame.length; i++) {
            frame[i] = (byte) 0x80;
        }
        return frame;
    }

    /**
     * Synthetic {@link CallService}-shaped recipient. Same shape as
     * the one in {@link ActiveCallTest}.
     */
    private static final class RecordingEngine extends CallService {
        RecordingEngine() {
            super(null, null);
        }

        @Override
        public void sendTerminate(Jid peer, Jid creator, String callId, CallEndReason reason) {
        }

        @Override
        public void sendMute(Jid peer, Jid creator, String callId, boolean muted) {
        }

        @Override
        public void sendVideoState(Jid peer, Jid creator, String callId, boolean enabled) {
        }

        @Override
        public void unregister(String callId) {
        }

        @Override
        public void notifyEnded(String callId, Jid fromJid, String reason) {
        }
    }
}
