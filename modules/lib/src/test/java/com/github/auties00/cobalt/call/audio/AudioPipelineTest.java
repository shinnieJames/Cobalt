package com.github.auties00.cobalt.call.audio;
import com.github.auties00.cobalt.call.internal.NoopCallService;

import com.github.auties00.cobalt.call.internal.CallTestRuntimes;
import com.github.auties00.cobalt.call.stream.AudioFrame;
import com.github.auties00.cobalt.call.audio.opus.OpusPacket;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.jid.JidServer;
import org.junit.jupiter.api.Test;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end coverage for {@link AudioPipeline}: captured PCM round-trips through libopus and
 * speexdsp out the {@link OpusPacket} sink, inbound packets decode onto the runtime's remote-audio
 * stream, VAD populates the voice-active flag, and the pipeline survives malformed input and
 * idempotent close. Tests drive the pipeline with synthesised sine frames at the 160-sample 10 ms /
 * 16 kHz WhatsApp voice profile.
 */
public class AudioPipelineTest {
    private static final Jid PEER = Jid.of("12345", JidServer.user());
    private static final Jid SELF = Jid.of("99999", JidServer.user());

    // 10 ms at 16 kHz: the WhatsApp voice profile.
    private static final int FRAME_SIZE = 160;

    @Test
    public void encodeAndDecodeRoundTrip() throws Exception {
        var engine = new NoopCallService();
        var call = CallTestRuntimes.of(engine, "id-pipe-1", PEER, PEER, SELF, true, false);
        var outbound = new LinkedBlockingQueue<OpusPacket>();
        try (var pipeline = new AudioPipeline(call, outbound::offer,
                AudioPipelineOptions.defaults().withoutAec().withoutPreprocessor())) {
            pipeline.start();

            for (var i = 0; i < 5; i++) {
                call.audioOut().write(new AudioFrame(sineFrame(1000), i * 10L));
            }

            for (var i = 0; i < 5; i++) {
                var packet = outbound.poll(2, TimeUnit.SECONDS);
                assertNotNull(packet, "encoder should produce frame " + i);
                assertTrue(packet.payload().length > 0, "packet payload must be non-empty");
            }

            var encoded = outbound.poll();
            if (encoded == null) {
                // Queue may already be drained by the asserts above; encode one more to feed back.
                call.audioOut().write(new AudioFrame(sineFrame(800), 1000L));
                encoded = outbound.poll(2, TimeUnit.SECONDS);
                assertNotNull(encoded);
            }
            pipeline.feedInboundPacket(encoded);
            var decoded = call.audioIn().read();
            assertNotNull(decoded, "decoder should produce a frame on the remote source");
            assertEquals(FRAME_SIZE, decoded.pcm().length);
        }
    }

    @Test
    public void vadPopulatesVoiceActiveFlag() throws Exception {
        var engine = new NoopCallService();
        var call = CallTestRuntimes.of(engine, "id-pipe-2", PEER, PEER, SELF, true, false);
        var outbound = new LinkedBlockingQueue<OpusPacket>();
        try (var pipeline = new AudioPipeline(call, outbound::offer,
                AudioPipelineOptions.defaults().withoutAec())) {
            pipeline.start();

            // 30 frames gives the VAD time to converge before counting hits.
            var voiceFrames = 0;
            for (var i = 0; i < 30; i++) {
                call.audioOut().write(new AudioFrame(sineFrame(1000), i * 10L));
            }
            for (var i = 0; i < 30; i++) {
                var packet = outbound.poll(2, TimeUnit.SECONDS);
                assertNotNull(packet);
                if (packet.voiceActive()) voiceFrames++;
            }
            assertTrue(voiceFrames > 10,
                    "VAD should fire on most sine frames, got " + voiceFrames + "/30");
        }
    }

    @Test
    public void mismatchedInputDoesNotKillPipeline() throws Exception {
        var engine = new NoopCallService();
        var call = CallTestRuntimes.of(engine, "id-pipe-3", PEER, PEER, SELF, true, false);
        var outbound = new LinkedBlockingQueue<OpusPacket>();
        try (var pipeline = new AudioPipeline(call, outbound::offer,
                AudioPipelineOptions.defaults().withoutAec().withoutPreprocessor())) {
            pipeline.start();

            // 80-sample frame is the wrong size; the capture thread must drop it, not crash.
            call.audioOut().write(new AudioFrame(new short[80], 0L));
            call.audioOut().write(new AudioFrame(sineFrame(1000), 10L));

            var packet = outbound.poll(2, TimeUnit.SECONDS);
            assertNotNull(packet, "pipeline must keep encoding after a bad frame");
        }
    }

    @Test
    public void closeIsIdempotent() {
        var engine = new NoopCallService();
        var call = CallTestRuntimes.of(engine, "id-pipe-4", PEER, PEER, SELF, true, false);
        var pipeline = new AudioPipeline(call, p -> {
        }, AudioPipelineOptions.defaults().withoutAec().withoutPreprocessor());
        pipeline.start();
        pipeline.close();
        pipeline.close();
        pipeline.close();
    }

    private static short[] sineFrame(int freqHz) {
        var pcm = new short[FRAME_SIZE];
        for (var i = 0; i < FRAME_SIZE; i++) {
            pcm[i] = (short) (Math.sin(i * 2 * Math.PI * freqHz / 16_000.0) * 0x4000);
        }
        return pcm;
    }
}
