package com.github.auties00.cobalt.call.internal.session;

import com.github.auties00.cobalt.call.ActiveCall;
import com.github.auties00.cobalt.call.CallOptions;
import com.github.auties00.cobalt.call.internal.CallService;
import com.github.auties00.cobalt.call.internal.audio.AudioPipelineOptions;
import com.github.auties00.cobalt.call.frame.audio.AudioFrame;
import com.github.auties00.cobalt.call.frame.video.VideoFrame;
import com.github.auties00.cobalt.call.internal.rtp.srtp.SrtpRole;
import com.github.auties00.cobalt.call.internal.transport.dtls.DtlsCertificate;
import com.github.auties00.cobalt.call.internal.transport.ice.DatagramTransport;
import com.github.auties00.cobalt.call.internal.video.VideoPipelineOptions;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.jid.JidServer;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import com.github.auties00.cobalt.call.session.VoiceCallOptions;
import com.github.auties00.cobalt.call.session.VideoTrackOptions;
import com.github.auties00.cobalt.call.internal.video.VideoPipeline;

/**
 * End-to-end M1 1:1 voice-call MVP test — wires two
 * {@link VoiceCallSession}s back-to-back through a loopback
 * {@link DatagramTransport} pair, drives the DTLS handshake to
 * completion, writes mic frames into one side's
 * {@link ActiveCall#localAudioSink}, and observes them arriving at
 * the other side's {@link ActiveCall#remoteAudioSource} after a full
 * Opus encode → RTP → SRTP → loopback → SRTP → RTP → Opus decode
 * round-trip.
 */
public class VoiceCallSessionTest {

    /**
     * Peer JID used by every test case.
     */
    private static final Jid PEER = Jid.of("12345", JidServer.user());

    /**
     * Local self JID used by every test case.
     */
    private static final Jid SELF = Jid.of("99999", JidServer.user());

    /**
     * 16 kHz mono 10 ms — the WhatsApp voice-call profile.
     */
    private static final int FRAME_SIZE = 160;

    /**
     * Drives a full M1 voice-call round-trip — both sides handshake
     * successfully, then PCM written into one side's mic sink ends
     * up on the other side's remote audio source.
     */
    @Test
    public void voiceCallRoundTripsAudio() throws Exception {
        var clientCert = DtlsCertificate.generate();
        var serverCert = DtlsCertificate.generate();
        var transports = LoopbackDatagramPair.pair();

        var clientEngine = new RecordingEngine();
        var serverEngine = new RecordingEngine();
        var clientCall = new ActiveCall(clientEngine, "id-vc-1",
                PEER, PEER, SELF, true, CallOptions.audio());
        var serverCall = new ActiveCall(serverEngine, "id-vc-2",
                SELF, PEER, PEER, false, CallOptions.audio());

        // Each side picks its own SSRC; the peer accepts the other.
        var clientSsrc = 0xAAAAAAAA;
        var serverSsrc = 0xBBBBBBBB;
        var clientOptions = new VoiceCallOptions(clientSsrc, serverSsrc, 111,
                AudioPipelineOptions.defaults().withoutAec().withoutPreprocessor());
        var serverOptions = new VoiceCallOptions(serverSsrc, clientSsrc, 111,
                AudioPipelineOptions.defaults().withoutAec().withoutPreprocessor());

        try (var clientSession = new VoiceCallSession(clientCall, transports[0],
                SrtpRole.CLIENT, clientCert, serverCert.sha256Fingerprint(),
                clientOptions);
             var serverSession = new VoiceCallSession(serverCall, transports[1],
                     SrtpRole.SERVER, serverCert, clientCert.sha256Fingerprint(),
                     serverOptions)) {

            serverSession.start();
            clientSession.start();

            clientSession.awaitConnected(15, TimeUnit.SECONDS);
            serverSession.awaitConnected(15, TimeUnit.SECONDS);

            assertTrue(clientSession.connected());
            assertTrue(serverSession.connected());

            // Push a sine frame into the client's mic — it should
            // appear on the server's remote audio source after Opus
            // round-trip + RTP/SRTP.
            for (var i = 0; i < 5; i++) {
                clientCall.localAudioSink().write(
                        new AudioFrame(sineFrame(1000), i * 10L));
            }

            var received = pollForFrame(serverCall, 5_000);
            assertNotNull(received, "server should receive at least one decoded frame");
            assertEquals(FRAME_SIZE, received.pcm().length,
                    "decoded frame must match source frame size");
        }
    }

    /**
     * After a successful initial handshake, calling
     * {@link VoiceCallSession#reconnect(DatagramTransport)} on both
     * sides with a fresh {@link DatagramTransport} pair re-runs the
     * DTLS handshake and resumes media without dropping the call.
     */
    @Test
    public void reconnectReestablishesMedia() throws Exception {
        var clientCert = DtlsCertificate.generate();
        var serverCert = DtlsCertificate.generate();
        var transports = LoopbackDatagramPair.pair();

        var clientCall = new ActiveCall(new RecordingEngine(), "id-vc-rc-1",
                PEER, PEER, SELF, true, CallOptions.audio());
        var serverCall = new ActiveCall(new RecordingEngine(), "id-vc-rc-2",
                SELF, PEER, PEER, false, CallOptions.audio());

        var clientOptions = new VoiceCallOptions(0xCC, 0xDD, 111,
                AudioPipelineOptions.defaults().withoutAec().withoutPreprocessor());
        var serverOptions = new VoiceCallOptions(0xDD, 0xCC, 111,
                AudioPipelineOptions.defaults().withoutAec().withoutPreprocessor());

        try (var clientSession = new VoiceCallSession(clientCall, transports[0],
                SrtpRole.CLIENT, clientCert, serverCert.sha256Fingerprint(),
                clientOptions);
             var serverSession = new VoiceCallSession(serverCall, transports[1],
                     SrtpRole.SERVER, serverCert, clientCert.sha256Fingerprint(),
                     serverOptions)) {
            serverSession.start();
            clientSession.start();
            clientSession.awaitConnected(15, TimeUnit.SECONDS);
            serverSession.awaitConnected(15, TimeUnit.SECONDS);
            assertTrue(clientSession.connected());

            // Simulate a network change — both peers swap to a fresh
            // transport pair and rerun the handshake. (In production
            // the call layer drives this from the ICE agent's
            // pair-failure event.)
            var newTransports = LoopbackDatagramPair.pair();
            clientSession.reconnect(newTransports[0]);
            serverSession.reconnect(newTransports[1]);

            // The session may briefly report not-connected during the
            // gap; await reconvergence.
            var deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
            while (!clientSession.connected() && System.nanoTime() < deadline) {
                Thread.sleep(20);
            }
            while (!serverSession.connected() && System.nanoTime() < deadline) {
                Thread.sleep(20);
            }
            assertTrue(clientSession.connected(), "client must reconverge after reconnect");
            assertTrue(serverSession.connected(), "server must reconverge after reconnect");

            // Drive a fresh frame post-reconnect — verifies the new
            // SRTP keys + RTP plumbing actually move bytes.
            clientCall.localAudioSink().write(new AudioFrame(sineFrame(800), 0L));
            var received = pollForFrame(serverCall, 5_000);
            assertNotNull(received, "audio must flow again after reconnect");
        }
    }

    /**
     * After a connected voice call, both sides start a VP8 video
     * track and a frame written to one side's
     * {@link ActiveCall#localVideoSink} arrives at the other side's
     * {@link ActiveCall#remoteVideoSource}.
     */
    @Test
    public void videoTrackRoundTripsFrames() throws Exception {
        var clientCert = DtlsCertificate.generate();
        var serverCert = DtlsCertificate.generate();
        var transports = LoopbackDatagramPair.pair();

        var clientCall = new ActiveCall(new RecordingEngine(), "id-vc-vid-1",
                PEER, PEER, SELF, true, CallOptions.video());
        var serverCall = new ActiveCall(new RecordingEngine(), "id-vc-vid-2",
                SELF, PEER, PEER, false, CallOptions.video());

        var clientOpts = new VoiceCallOptions(0xA1, 0xB1, 111,
                AudioPipelineOptions.defaults().withoutAec().withoutPreprocessor());
        var serverOpts = new VoiceCallOptions(0xB1, 0xA1, 111,
                AudioPipelineOptions.defaults().withoutAec().withoutPreprocessor());

        // 64×64 keeps the encoders fast under the test stack.
        var videoPipeline = new VideoPipelineOptions(64, 64, 30, 100_000, true);
        var clientVideo = new VideoTrackOptions(0xC1, 0xD1, 96,
                VideoTrackOptions.Codec.VP8, videoPipeline,
                VideoTrackOptions.Kind.CAMERA);
        var serverVideo = new VideoTrackOptions(0xD1, 0xC1, 96,
                VideoTrackOptions.Codec.VP8, videoPipeline,
                VideoTrackOptions.Kind.CAMERA);

        try (var clientSession = new VoiceCallSession(clientCall, transports[0],
                SrtpRole.CLIENT, clientCert, serverCert.sha256Fingerprint(), clientOpts);
             var serverSession = new VoiceCallSession(serverCall, transports[1],
                     SrtpRole.SERVER, serverCert, clientCert.sha256Fingerprint(), serverOpts)) {
            serverSession.start();
            clientSession.start();
            clientSession.awaitConnected(15, TimeUnit.SECONDS);
            serverSession.awaitConnected(15, TimeUnit.SECONDS);

            clientSession.startVideoTrack(clientVideo);
            serverSession.startVideoTrack(serverVideo);

            assertTrue(clientSession.videoActive());
            assertTrue(serverSession.videoActive());

            var i420 = gradientI420(64, 64);
            clientCall.localVideoSink().write(new VideoFrame(i420, 64, 64, 0L));

            var received = pollForVideoFrame(serverCall, 5_000);
            assertNotNull(received, "server should receive the encoded video frame");
            assertEquals(64, received.width());
            assertEquals(64, received.height());

            clientSession.stopVideoTrack(VideoTrackOptions.Kind.CAMERA);
            Assertions.assertFalse(clientSession.videoActive());
        }
    }

    /**
     * The M7 screen-share track coexists with a camera track —
     * each kind has its own SSRC, codec, and pipeline; both can be
     * active simultaneously.
     */
    @Test
    public void cameraAndScreenShareCoexist() throws Exception {
        var clientCert = DtlsCertificate.generate();
        var serverCert = DtlsCertificate.generate();
        var transports = LoopbackDatagramPair.pair();

        var clientCall = new ActiveCall(new RecordingEngine(), "id-vc-ss-1",
                PEER, PEER, SELF, true, CallOptions.video());
        var serverCall = new ActiveCall(new RecordingEngine(), "id-vc-ss-2",
                SELF, PEER, PEER, false, CallOptions.video());

        var clientOpts = new VoiceCallOptions(0xCA1, 0xCA2, 111,
                AudioPipelineOptions.defaults().withoutAec().withoutPreprocessor());
        var serverOpts = new VoiceCallOptions(0xCA2, 0xCA1, 111,
                AudioPipelineOptions.defaults().withoutAec().withoutPreprocessor());

        var pipelineOpts = new VideoPipelineOptions(64, 64, 30, 100_000, true);
        var camera = new VideoTrackOptions(0xCA10, 0xCA20, 96,
                VideoTrackOptions.Codec.VP8, pipelineOpts,
                VideoTrackOptions.Kind.CAMERA);
        var screen = new VideoTrackOptions(0x5C10, 0x5C20, 96,
                VideoTrackOptions.Codec.VP8, pipelineOpts,
                VideoTrackOptions.Kind.SCREEN_SHARE);

        try (var clientSession = new VoiceCallSession(clientCall, transports[0],
                SrtpRole.CLIENT, clientCert, serverCert.sha256Fingerprint(), clientOpts);
             var serverSession = new VoiceCallSession(serverCall, transports[1],
                     SrtpRole.SERVER, serverCert, clientCert.sha256Fingerprint(), serverOpts)) {
            serverSession.start();
            clientSession.start();
            clientSession.awaitConnected(15, TimeUnit.SECONDS);
            serverSession.awaitConnected(15, TimeUnit.SECONDS);

            clientSession.startVideoTrack(camera);
            clientSession.startScreenShare(screen);

            assertTrue(clientSession.videoActive(VideoTrackOptions.Kind.CAMERA));
            assertTrue(clientSession.videoActive(VideoTrackOptions.Kind.SCREEN_SHARE));
            assertTrue(clientSession.videoActive());

            // Starting a second camera track is rejected — already
            // running.
            Assertions.assertThrows(
                    IllegalStateException.class,
                    () -> clientSession.startVideoTrack(camera));

            // Starting via startScreenShare with a CAMERA-kind
            // option is rejected.
            Assertions.assertThrows(
                    IllegalArgumentException.class,
                    () -> clientSession.startScreenShare(camera));

            clientSession.stopScreenShare();
            Assertions.assertFalse(
                    clientSession.videoActive(VideoTrackOptions.Kind.SCREEN_SHARE));
            assertTrue(clientSession.videoActive(VideoTrackOptions.Kind.CAMERA));
        }
    }

    /**
     * A handshake against a peer with a wrong fingerprint fails on
     * both sides; both sessions surface the failure via
     * {@link VoiceCallSession#awaitConnected}.
     */
    @Test
    public void mismatchedFingerprintFails() {
        var clientCert = DtlsCertificate.generate();
        var serverCert = DtlsCertificate.generate();
        var bogusCert = DtlsCertificate.generate();
        var transports = LoopbackDatagramPair.pair();

        var clientEngine = new RecordingEngine();
        var serverEngine = new RecordingEngine();
        var clientCall = new ActiveCall(clientEngine, "id-vc-3",
                PEER, PEER, SELF, true, CallOptions.audio());
        var serverCall = new ActiveCall(serverEngine, "id-vc-4",
                SELF, PEER, PEER, false, CallOptions.audio());

        var optionsClient = new VoiceCallOptions(0x10, 0x20, 111,
                AudioPipelineOptions.defaults().withoutAec().withoutPreprocessor());
        var optionsServer = new VoiceCallOptions(0x20, 0x10, 111,
                AudioPipelineOptions.defaults().withoutAec().withoutPreprocessor());

        try (var clientSession = new VoiceCallSession(clientCall, transports[0],
                SrtpRole.CLIENT, clientCert, bogusCert.sha256Fingerprint(),  // wrong!
                optionsClient);
             var serverSession = new VoiceCallSession(serverCall, transports[1],
                     SrtpRole.SERVER, serverCert, clientCert.sha256Fingerprint(),
                     optionsServer)) {
            serverSession.start();
            clientSession.start();

            Assertions.assertThrows(IOException.class,
                    () -> clientSession.awaitConnected(10, TimeUnit.SECONDS));
        }
    }

    /**
     * Builds a {@code w × h} I420 frame whose Y plane is a
     * horizontal gradient and whose U/V planes are neutral —
     * voice-test content that's small but compressible enough for
     * VP8.
     *
     * @param w the width
     * @param h the height
     * @return the I420 bytes
     */
    private static byte[] gradientI420(int w, int h) {
        var ySize = w * h;
        var uvSize = (w / 2) * (h / 2);
        var frame = new byte[ySize + 2 * uvSize];
        for (var i = 0; i < ySize; i++) {
            frame[i] = (byte) (i & 0xff);
        }
        for (var i = ySize; i < frame.length; i++) {
            frame[i] = (byte) 0x80;
        }
        return frame;
    }

    /**
     * Polls the call's remote video source with a deadline.
     *
     * @param call      the call
     * @param timeoutMs deadline in ms
     * @return the next decoded video frame, or {@code null} on
     *         timeout
     * @throws InterruptedException if interrupted
     */
    private static VideoFrame pollForVideoFrame(ActiveCall call, long timeoutMs)
            throws InterruptedException {
        var deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs);
        while (System.nanoTime() < deadline) {
            var holder = new AtomicReference<VideoFrame>();
            var t = Thread.ofVirtual().start(() -> {
                try {
                    holder.set(call.remoteVideoSource().next());
                } catch (InterruptedException _) {
                }
            });
            t.join(50);
            if (holder.get() != null) {
                return holder.get();
            }
            t.interrupt();
        }
        return null;
    }

    /**
     * Generates a 16-kHz mono sine frame at the given frequency.
     *
     * @param freqHz the sine frequency
     * @return a fresh {@code short[FRAME_SIZE]} sine frame
     */
    private static short[] sineFrame(int freqHz) {
        var pcm = new short[FRAME_SIZE];
        for (var i = 0; i < FRAME_SIZE; i++) {
            pcm[i] = (short) (Math.sin(i * 2 * Math.PI * freqHz / 16_000.0) * 0x4000);
        }
        return pcm;
    }

    /**
     * Polls the call's remote audio source for a frame, with a
     * deadline. Used so the test doesn't hang forever if the
     * round-trip silently drops a packet.
     *
     * @param call      the call
     * @param timeoutMs maximum wait in ms
     * @return the next decoded frame, or {@code null} on timeout
     * @throws InterruptedException if interrupted
     */
    private static AudioFrame pollForFrame(ActiveCall call, long timeoutMs)
            throws InterruptedException {
        var deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs);
        while (System.nanoTime() < deadline) {
            // remoteAudioSource.next() blocks; spin a virtual thread
            // to do it concurrently with the deadline check.
            var holder = new AtomicReference<AudioFrame>();
            var t = Thread.ofVirtual().start(() -> {
                try {
                    holder.set(call.remoteAudioSource().next());
                } catch (InterruptedException _) {
                }
            });
            t.join(50);
            if (holder.get() != null) {
                return holder.get();
            }
            t.interrupt();
        }
        return null;
    }

    /**
     * In-memory pair of {@link DatagramTransport}s — what one side
     * sends, the other side's listener receives via a virtual-thread
     * pump.
     */
    private static final class LoopbackDatagramPair implements DatagramTransport {
        private final LinkedBlockingQueue<byte[]> inbound;
        private final LinkedBlockingQueue<byte[]> peerInbound;
        private volatile InboundListener listener;
        private final Thread pump;
        private volatile boolean closed;

        private LoopbackDatagramPair(LinkedBlockingQueue<byte[]> inbound,
                                     LinkedBlockingQueue<byte[]> peerInbound) {
            this.inbound = inbound;
            this.peerInbound = peerInbound;
            this.pump = Thread.ofVirtual().name("voice-loopback-pump").start(this::run);
        }

        static LoopbackDatagramPair[] pair() {
            var q1 = new LinkedBlockingQueue<byte[]>();
            var q2 = new LinkedBlockingQueue<byte[]>();
            return new LoopbackDatagramPair[]{
                    new LoopbackDatagramPair(q1, q2),
                    new LoopbackDatagramPair(q2, q1)
            };
        }

        @Override
        public InetSocketAddress localAddress() {
            return new InetSocketAddress("127.0.0.1", 0);
        }

        @Override
        public InetSocketAddress remoteAddress() {
            return new InetSocketAddress("127.0.0.1", 0);
        }

        @Override
        public void send(byte[] packet) {
            if (!closed) {
                peerInbound.offer(packet);
            }
        }

        @Override
        public void setInboundListener(InboundListener listener) {
            this.listener = listener;
        }

        @Override
        public void close() {
            closed = true;
            pump.interrupt();
        }

        private void run() {
            try {
                while (!Thread.currentThread().isInterrupted()) {
                    var packet = inbound.take();
                    var l = listener;
                    if (l != null) {
                        try {
                            l.onDatagram(packet);
                        } catch (Throwable _) {
                        }
                    }
                }
            } catch (InterruptedException _) {
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * Synthetic {@link CallService}-shaped recipient
     */
    private static final class RecordingEngine extends CallService {
        RecordingEngine() {
            super(null, null);
        }
    }
}
