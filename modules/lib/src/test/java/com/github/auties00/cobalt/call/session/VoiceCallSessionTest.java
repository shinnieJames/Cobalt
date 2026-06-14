package com.github.auties00.cobalt.call.session;
import com.github.auties00.cobalt.call.internal.NoopCallService;

import com.github.auties00.cobalt.call.CallRuntime;
import com.github.auties00.cobalt.call.internal.CallTestRuntimes;
import com.github.auties00.cobalt.call.audio.AudioPipelineOptions;
import com.github.auties00.cobalt.call.stream.AudioFrame;
import com.github.auties00.cobalt.call.stream.VideoFrame;
import com.github.auties00.cobalt.call.rtp.srtp.SrtpRole;
import com.github.auties00.cobalt.call.transport.dtls.DtlsCertificate;
import com.github.auties00.cobalt.call.transport.ice.DatagramTransport;
import com.github.auties00.cobalt.call.video.VideoPipelineOptions;
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

/**
 * Covers end-to-end 1:1 {@link VoiceCallSession} behaviour by wiring two sessions back-to-back
 * through an in-memory loopback {@link DatagramTransport} pair, driving the DTLS handshake to
 * completion, and verifying that media written into one side's {@link CallRuntime#audioOut() audio}
 * (and {@link CallRuntime#videoOut() video}) output stream arrives at the other side's
 * {@link CallRuntime#audioIn() audio} (and {@link CallRuntime#videoIn() video}) input stream after a
 * full encode, RTP, SRTP, loopback, decode round-trip. Also exercises transport reconnect, video and
 * screen-share track lifecycle, and DTLS fingerprint-mismatch failure.
 */
public class VoiceCallSessionTest {

    private static final Jid PEER = Jid.of("12345", JidServer.user());

    private static final Jid SELF = Jid.of("99999", JidServer.user());

    // 160 samples = 16 kHz mono 10 ms, the WhatsApp voice-call profile.
    private static final int FRAME_SIZE = 160;

    @Test
    public void voiceCallRoundTripsAudio() throws Exception {
        var clientCert = DtlsCertificate.generate();
        var serverCert = DtlsCertificate.generate();
        var transports = LoopbackDatagramPair.pair();

        var clientEngine = new NoopCallService();
        var serverEngine = new NoopCallService();
        var clientCall = CallTestRuntimes.of(clientEngine, "id-vc-1",
                PEER, PEER, SELF, true, false);
        var serverCall = CallTestRuntimes.of(serverEngine, "id-vc-2",
                SELF, PEER, PEER, false, false);

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

            for (var i = 0; i < 5; i++) {
                clientCall.audioOut().write(
                        new AudioFrame(sineFrame(1000), i * 10L));
            }

            var received = pollForFrame(serverCall, 5_000);
            assertNotNull(received, "server should receive at least one decoded frame");
            assertEquals(FRAME_SIZE, received.pcm().length,
                    "decoded frame must match source frame size");
        }
    }

    @Test
    public void reconnectReestablishesMedia() throws Exception {
        var clientCert = DtlsCertificate.generate();
        var serverCert = DtlsCertificate.generate();
        var transports = LoopbackDatagramPair.pair();

        var clientCall = CallTestRuntimes.of(new NoopCallService(), "id-vc-rc-1",
                PEER, PEER, SELF, true, false);
        var serverCall = CallTestRuntimes.of(new NoopCallService(), "id-vc-rc-2",
                SELF, PEER, PEER, false, false);

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

            // Simulate a network change: both peers swap to a fresh transport pair and rerun the
            // handshake. In production the call layer drives this from the ICE agent's pair-failure event.
            var newTransports = LoopbackDatagramPair.pair();
            clientSession.reconnect(newTransports[0]);
            serverSession.reconnect(newTransports[1]);

            var deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
            while (!clientSession.connected() && System.nanoTime() < deadline) {
                Thread.sleep(20);
            }
            while (!serverSession.connected() && System.nanoTime() < deadline) {
                Thread.sleep(20);
            }
            assertTrue(clientSession.connected(), "client must reconverge after reconnect");
            assertTrue(serverSession.connected(), "server must reconverge after reconnect");

            clientCall.audioOut().write(new AudioFrame(sineFrame(800), 0L));
            var received = pollForFrame(serverCall, 5_000);
            assertNotNull(received, "audio must flow again after reconnect");
        }
    }

    @Test
    public void videoTrackRoundTripsFrames() throws Exception {
        var clientCert = DtlsCertificate.generate();
        var serverCert = DtlsCertificate.generate();
        var transports = LoopbackDatagramPair.pair();

        var clientCall = CallTestRuntimes.of(new NoopCallService(), "id-vc-vid-1",
                PEER, PEER, SELF, true, true);
        var serverCall = CallTestRuntimes.of(new NoopCallService(), "id-vc-vid-2",
                SELF, PEER, PEER, false, true);

        var clientOpts = new VoiceCallOptions(0xA1, 0xB1, 111,
                AudioPipelineOptions.defaults().withoutAec().withoutPreprocessor());
        var serverOpts = new VoiceCallOptions(0xB1, 0xA1, 111,
                AudioPipelineOptions.defaults().withoutAec().withoutPreprocessor());

        // 64x64 keeps the encoders fast under the test stack.
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
            clientCall.videoOut().write(new VideoFrame(i420, 64, 64, 0L));

            var received = pollForVideoFrame(serverCall, 5_000);
            assertNotNull(received, "server should receive the encoded video frame");
            assertEquals(64, received.width());
            assertEquals(64, received.height());

            clientSession.stopVideoTrack(VideoTrackOptions.Kind.CAMERA);
            Assertions.assertFalse(clientSession.videoActive());
        }
    }

    @Test
    public void requestKeyframeEmitsPli() throws Exception {
        var clientCert = DtlsCertificate.generate();
        var serverCert = DtlsCertificate.generate();
        var rawTransports = LoopbackDatagramPair.pair();
        // Tap the client's outbound datagrams so the emitted RTCP PLI can be inspected; SRTCP leaves the
        // feedback header (FMT, PT, sender SSRC) in clear, so the request is identifiable without keys.
        var clientTap = new CapturingTransport(rawTransports[0]);

        var clientCall = CallTestRuntimes.of(new NoopCallService(), "id-vc-pli-1",
                PEER, PEER, SELF, true, true);
        var serverCall = CallTestRuntimes.of(new NoopCallService(), "id-vc-pli-2",
                SELF, PEER, PEER, false, true);

        var clientOpts = new VoiceCallOptions(0xA1, 0xB1, 111,
                AudioPipelineOptions.defaults().withoutAec().withoutPreprocessor());
        var serverOpts = new VoiceCallOptions(0xB1, 0xA1, 111,
                AudioPipelineOptions.defaults().withoutAec().withoutPreprocessor());

        var videoPipeline = new VideoPipelineOptions(64, 64, 30, 100_000, true);
        var clientVideo = new VideoTrackOptions(0xC1, 0xD1, 96,
                VideoTrackOptions.Codec.VP8, videoPipeline, VideoTrackOptions.Kind.CAMERA);
        var serverVideo = new VideoTrackOptions(0xD1, 0xC1, 96,
                VideoTrackOptions.Codec.VP8, videoPipeline, VideoTrackOptions.Kind.CAMERA);

        try (var clientSession = new VoiceCallSession(clientCall, clientTap,
                SrtpRole.CLIENT, clientCert, serverCert.sha256Fingerprint(), clientOpts);
             var serverSession = new VoiceCallSession(serverCall, rawTransports[1],
                     SrtpRole.SERVER, serverCert, clientCert.sha256Fingerprint(), serverOpts)) {
            serverSession.start();
            clientSession.start();
            clientSession.awaitConnected(15, TimeUnit.SECONDS);
            serverSession.awaitConnected(15, TimeUnit.SECONDS);

            clientSession.startVideoTrack(clientVideo);
            serverSession.startVideoTrack(serverVideo);

            clientTap.captured().clear();
            clientSession.requestKeyframe();

            // The PLI is a PSFB (PT 206) feedback message type 1, with the local video SSRC (0xC1) as the
            // packet sender; the 0x81 first byte encodes V=2, P=0, FMT=1.
            var pli = clientTap.captured().stream()
                    .filter(p -> p.length >= 8 && (p[1] & 0xFF) == 206 && (p[0] & 0x1F) == 1)
                    .findFirst()
                    .orElse(null);
            assertNotNull(pli, "requestKeyframe must emit an RTCP PLI");
            assertEquals((byte) 0x81, pli[0], "V=2, P=0, FMT=1 (PLI)");
            var senderSsrc = ((pli[4] & 0xFF) << 24) | ((pli[5] & 0xFF) << 16)
                    | ((pli[6] & 0xFF) << 8) | (pli[7] & 0xFF);
            assertEquals(0xC1, senderSsrc, "PLI packet-sender SSRC is the local video SSRC");
        }
    }

    @Test
    public void cameraAndScreenShareCoexist() throws Exception {
        var clientCert = DtlsCertificate.generate();
        var serverCert = DtlsCertificate.generate();
        var transports = LoopbackDatagramPair.pair();

        var clientCall = CallTestRuntimes.of(new NoopCallService(), "id-vc-ss-1",
                PEER, PEER, SELF, true, true);
        var serverCall = CallTestRuntimes.of(new NoopCallService(), "id-vc-ss-2",
                SELF, PEER, PEER, false, true);

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

            // Starting a second camera track is rejected: one is already running.
            Assertions.assertThrows(
                    IllegalStateException.class,
                    () -> clientSession.startVideoTrack(camera));

            // startScreenShare rejects a CAMERA-kind option.
            Assertions.assertThrows(
                    IllegalArgumentException.class,
                    () -> clientSession.startScreenShare(camera));

            clientSession.stopScreenShare();
            Assertions.assertFalse(
                    clientSession.videoActive(VideoTrackOptions.Kind.SCREEN_SHARE));
            assertTrue(clientSession.videoActive(VideoTrackOptions.Kind.CAMERA));
        }
    }

    @Test
    public void mismatchedFingerprintFails() {
        var clientCert = DtlsCertificate.generate();
        var serverCert = DtlsCertificate.generate();
        var bogusCert = DtlsCertificate.generate();
        var transports = LoopbackDatagramPair.pair();

        var clientEngine = new NoopCallService();
        var serverEngine = new NoopCallService();
        var clientCall = CallTestRuntimes.of(clientEngine, "id-vc-3",
                PEER, PEER, SELF, true, false);
        var serverCall = CallTestRuntimes.of(serverEngine, "id-vc-4",
                SELF, PEER, PEER, false, false);

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

    // w-by-h I420 frame: Y plane is a horizontal gradient, U/V planes neutral; compressible by VP8.
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

    // Polls the call's remote video stream until timeoutMs, returning null on timeout.
    private static VideoFrame pollForVideoFrame(CallRuntime call, long timeoutMs)
            throws InterruptedException {
        var deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs);
        while (System.nanoTime() < deadline) {
            var holder = new AtomicReference<VideoFrame>();
            var t = Thread.ofVirtual().start(() -> {
                try {
                    holder.set(call.videoIn().read());
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

    // 16 kHz mono sine frame at the given frequency.
    private static short[] sineFrame(int freqHz) {
        var pcm = new short[FRAME_SIZE];
        for (var i = 0; i < FRAME_SIZE; i++) {
            pcm[i] = (short) (Math.sin(i * 2 * Math.PI * freqHz / 16_000.0) * 0x4000);
        }
        return pcm;
    }

    // Polls the call's remote audio stream until timeoutMs, returning null on timeout so the test
    // does not hang forever when the round-trip silently drops a packet.
    private static AudioFrame pollForFrame(CallRuntime call, long timeoutMs)
            throws InterruptedException {
        var deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs);
        while (System.nanoTime() < deadline) {
            // audioIn().read() blocks; spin a virtual thread to race it against the deadline.
            var holder = new AtomicReference<AudioFrame>();
            var t = Thread.ofVirtual().start(() -> {
                try {
                    holder.set(call.audioIn().read());
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

    // Wraps a DatagramTransport, recording every datagram the session sends so a test can inspect the
    // outbound RTCP feedback the session emits.
    private static final class CapturingTransport implements DatagramTransport {
        private final DatagramTransport delegate;
        private final java.util.List<byte[]> captured = new java.util.concurrent.CopyOnWriteArrayList<>();

        private CapturingTransport(DatagramTransport delegate) {
            this.delegate = delegate;
        }

        java.util.List<byte[]> captured() {
            return captured;
        }

        @Override
        public InetSocketAddress localAddress() {
            return delegate.localAddress();
        }

        @Override
        public InetSocketAddress remoteAddress() {
            return delegate.remoteAddress();
        }

        @Override
        public void send(byte[] packet) {
            captured.add(packet);
            delegate.send(packet);
        }

        @Override
        public void setInboundListener(InboundListener listener) {
            delegate.setInboundListener(listener);
        }

        @Override
        public void close() {
            delegate.close();
        }
    }

    // In-memory pair of DatagramTransports; what one side sends, the other's listener receives
    // via a virtual-thread pump.
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

}
