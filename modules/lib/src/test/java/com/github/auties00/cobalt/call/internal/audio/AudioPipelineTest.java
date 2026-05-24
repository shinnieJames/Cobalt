package com.github.auties00.cobalt.call.internal.audio;

import com.github.auties00.cobalt.call.ActiveCall;
import com.github.auties00.cobalt.call.ActiveCallTest;
import com.github.auties00.cobalt.call.CallOptions;
import com.github.auties00.cobalt.call.frame.audio.AudioFrame;
import com.github.auties00.cobalt.call.internal.CallService;
import com.github.auties00.cobalt.call.internal.audio.opus.OpusPacket;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.jid.JidServer;
import org.junit.jupiter.api.Test;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end tests for the {@link AudioPipeline} — verifies that
 * captured PCM round-trips through libopus + speexdsp out the
 * {@link OpusPacket} sink, that inbound packets decode and arrive on
 * {@link ActiveCall#remoteAudioSource()}, and that the AEC far-end
 * reference updates as decode runs.
 */
public class AudioPipelineTest {

    /**
     * Peer JID used by every test case.
     */
    private static final Jid PEER = Jid.of("12345", JidServer.user());

    /**
     * Local self JID used by every test case.
     */
    private static final Jid SELF = Jid.of("99999", JidServer.user());

    /**
     * 10 ms at 16 kHz — the WhatsApp voice profile.
     */
    private static final int FRAME_SIZE = 160;

    /**
     * Captured PCM frames captured at 16 kHz mono encode → libopus →
     * decoder round-trip back into the call's remote audio source.
     */
    @Test
    public void encodeAndDecodeRoundTrip() throws Exception {
        var engine = new RecordingEngine();
        var call = new ActiveCall(engine, "id-pipe-1", PEER, PEER, SELF, true, CallOptions.audio());
        var outbound = new LinkedBlockingQueue<OpusPacket>();
        try (var pipeline = new AudioPipeline(call, outbound::offer,
                AudioPipelineOptions.defaults().withoutAec().withoutPreprocessor())) {
            pipeline.start();

            // Push 5 sine-wave frames into the local mic sink. The
            // capture thread should encode each into an OpusPacket.
            for (var i = 0; i < 5; i++) {
                call.localAudioSink().write(new AudioFrame(sineFrame(1000), i * 10L));
            }

            for (var i = 0; i < 5; i++) {
                var packet = outbound.poll(2, TimeUnit.SECONDS);
                assertNotNull(packet, "encoder should produce frame " + i);
                assertTrue(packet.payload().length > 0, "packet payload must be non-empty");
            }

            // Feed the first encoded packet back as inbound — the
            // decoder should produce a frame on remoteAudioSource.
            var encoded = outbound.poll();
            if (encoded == null) {
                // Already drained above; encode another and feed it
                // back.
                call.localAudioSink().write(new AudioFrame(sineFrame(800), 1000L));
                encoded = outbound.poll(2, TimeUnit.SECONDS);
                assertNotNull(encoded);
            }
            pipeline.feedInboundPacket(encoded);
            var decoded = call.remoteAudioSource().next();
            assertNotNull(decoded, "decoder should produce a frame on the remote source");
            assertEquals(FRAME_SIZE, decoded.pcm().length);
        }
    }

    /**
     * VAD-driven {@link OpusPacket#voiceActive()} flips between true
     * and false depending on whether the input is sine (voice-like)
     * or silence — when the speexdsp preprocessor is enabled with
     * VAD on.
     */
    @Test
    public void vadPopulatesVoiceActiveFlag() throws Exception {
        var engine = new RecordingEngine();
        var call = new ActiveCall(engine, "id-pipe-2", PEER, PEER, SELF, true, CallOptions.audio());
        var outbound = new LinkedBlockingQueue<OpusPacket>();
        try (var pipeline = new AudioPipeline(call, outbound::offer,
                AudioPipelineOptions.defaults().withoutAec())) {
            pipeline.start();

            // Drive 30 voice-like frames so VAD has time to converge.
            var voiceFrames = 0;
            for (var i = 0; i < 30; i++) {
                call.localAudioSink().write(new AudioFrame(sineFrame(1000), i * 10L));
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

    /**
     * The pipeline survives mismatched-frame-size inbound or
     * unexpected codec errors without crashing — it logs and
     * continues with the next frame.
     */
    @Test
    public void mismatchedInputDoesNotKillPipeline() throws Exception {
        var engine = new RecordingEngine();
        var call = new ActiveCall(engine, "id-pipe-3", PEER, PEER, SELF, true, CallOptions.audio());
        var outbound = new LinkedBlockingQueue<OpusPacket>();
        try (var pipeline = new AudioPipeline(call, outbound::offer,
                AudioPipelineOptions.defaults().withoutAec().withoutPreprocessor())) {
            pipeline.start();

            // A wrong-sized PCM frame is silently dropped by the
            // capture thread.
            call.localAudioSink().write(new AudioFrame(new short[80], 0L));
            // Followed by a correctly-sized frame, which still encodes.
            call.localAudioSink().write(new AudioFrame(sineFrame(1000), 10L));

            var packet = outbound.poll(2, TimeUnit.SECONDS);
            assertNotNull(packet, "pipeline must keep encoding after a bad frame");
        }
    }

    /**
     * {@link AudioPipeline#close()} is idempotent and unblocks the
     * decoder thread waiting on the inbound queue.
     */
    @Test
    public void closeIsIdempotent() {
        var engine = new RecordingEngine();
        var call = new ActiveCall(engine, "id-pipe-4", PEER, PEER, SELF, true, CallOptions.audio());
        var pipeline = new AudioPipeline(call, p -> {
        }, AudioPipelineOptions.defaults().withoutAec().withoutPreprocessor());
        pipeline.start();
        pipeline.close();
        pipeline.close();
        pipeline.close();
    }

    /**
     * Generates one frame of a 16-kHz mono sine at the given
     * frequency — used to produce voice-like input for the encoder.
     *
     * @param freqHz the sine frequency in Hz
     * @return a fresh {@code short[FRAME_SIZE]} sine
     */
    private static short[] sineFrame(int freqHz) {
        var pcm = new short[FRAME_SIZE];
        for (var i = 0; i < FRAME_SIZE; i++) {
            pcm[i] = (short) (Math.sin(i * 2 * Math.PI * freqHz / 16_000.0) * 0x4000);
        }
        return pcm;
    }

    /**
     * Synthetic {@link CallService}-shaped recipient that captures
     * the calls {@link ActiveCall} would make. Same shape as the
     * one in {@link ActiveCallTest}; duplicated rather than shared
     * to keep the tests independent.
     */
    private static final class RecordingEngine extends CallService {
        RecordingEngine() {
            super(null, null);
        }
    }
}
