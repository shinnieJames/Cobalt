package com.github.auties00.cobalt.call.internal.session;

import com.github.auties00.cobalt.call.ActiveCall;
import com.github.auties00.cobalt.call.CallOptions;
import com.github.auties00.cobalt.call.internal.CallService;
import com.github.auties00.cobalt.call.internal.audio.AudioPipelineOptions;
import com.github.auties00.cobalt.call.frame.audio.AudioFrame;
import com.github.auties00.cobalt.call.internal.rtp.srtp.SrtpRole;
import com.github.auties00.cobalt.call.internal.transport.dtls.DtlsCertificate;
import com.github.auties00.cobalt.call.internal.transport.ice.DatagramTransport;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.jid.JidServer;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import com.github.auties00.cobalt.call.session.VoiceCallOptions;
import com.github.auties00.cobalt.call.session.GroupCallParticipant;

/**
 * Covers {@link GroupCallSession} by pairing two sessions over an in-memory loopback
 * transport, exercising per-SSRC inbound demux, per-peer audio listener routing, and the
 * add/remove participant lifecycle. The SFU of a real group call is stood in by a direct
 * loopback between the two sessions, which is sufficient to drive the SSRC-based demux path.
 */
public class GroupCallSessionTest {

    private static final Jid PEER = Jid.of("12345", JidServer.user());

    private static final Jid SELF = Jid.of("99999", JidServer.user());

    @Test
    public void twoPartyGroupSubscribeRoundTripsAudio() throws Exception {
        var clientCert = DtlsCertificate.generate();
        var serverCert = DtlsCertificate.generate();
        var transports = LoopbackPair.pair();

        var clientCall = new ActiveCall(new RecordingEngine(), "id-grp-1",
                PEER, PEER, SELF, true, CallOptions.audio());
        var serverCall = new ActiveCall(new RecordingEngine(), "id-grp-2",
                SELF, PEER, PEER, false, CallOptions.audio());

        var clientOpts = new VoiceCallOptions(0xAAA1, 0xBBB1, 111,
                AudioPipelineOptions.defaults().withoutAec().withoutPreprocessor());
        var serverOpts = new VoiceCallOptions(0xBBB1, 0xAAA1, 111,
                AudioPipelineOptions.defaults().withoutAec().withoutPreprocessor());

        try (var clientSession = new GroupCallSession(clientCall, transports[0],
                SrtpRole.CLIENT, clientCert, serverCert.sha256Fingerprint(), clientOpts);
             var serverSession = new GroupCallSession(serverCall, transports[1],
                     SrtpRole.SERVER, serverCert, clientCert.sha256Fingerprint(), serverOpts)) {
            serverSession.start();
            clientSession.start();
            clientSession.awaitConnected(15, TimeUnit.SECONDS);
            serverSession.awaitConnected(15, TimeUnit.SECONDS);

            var receivedAtServer = new LinkedBlockingQueue<AudioFrame>();
            serverSession.addParticipant(new GroupCallParticipant(
                    PEER, clientOpts.localAudioSsrc(), receivedAtServer::offer));

            assertTrue(serverSession.subscribedSsrcs().contains(clientOpts.localAudioSsrc()));

            for (var i = 0; i < 5; i++) {
                clientCall.localAudioSink().write(new AudioFrame(sineFrame(900), i * 10L));
            }
            var first = receivedAtServer.poll(5, TimeUnit.SECONDS);
            assertNotNull(first, "server-side participant listener must fire");
            assertEquals(160, first.pcm().length);

            serverSession.removeParticipant(PEER);
            assertFalse(serverSession.subscribedSsrcs().contains(clientOpts.localAudioSsrc()));
        }
    }

    @Test
    public void resubscribeReplacesListener() throws Exception {
        var clientCert = DtlsCertificate.generate();
        var serverCert = DtlsCertificate.generate();
        var transports = LoopbackPair.pair();

        var serverCall = new ActiveCall(new RecordingEngine(), "id-grp-rs",
                SELF, PEER, PEER, false, CallOptions.audio());

        var clientOpts = new VoiceCallOptions(0xAAA2, 0xBBB2, 111,
                AudioPipelineOptions.defaults().withoutAec().withoutPreprocessor());
        var serverOpts = new VoiceCallOptions(0xBBB2, 0xAAA2, 111,
                AudioPipelineOptions.defaults().withoutAec().withoutPreprocessor());

        try (var clientSession = new GroupCallSession(
                new ActiveCall(new RecordingEngine(), "id-grp-rs-client",
                        PEER, PEER, SELF, true, CallOptions.audio()),
                transports[0], SrtpRole.CLIENT, clientCert, serverCert.sha256Fingerprint(), clientOpts);
             var serverSession = new GroupCallSession(serverCall, transports[1],
                     SrtpRole.SERVER, serverCert, clientCert.sha256Fingerprint(), serverOpts)) {
            serverSession.start();
            clientSession.start();
            clientSession.awaitConnected(15, TimeUnit.SECONDS);
            serverSession.awaitConnected(15, TimeUnit.SECONDS);

            var firstListener = new AtomicReference<AudioFrame>();
            serverSession.addParticipant(new GroupCallParticipant(
                    PEER, clientOpts.localAudioSsrc(), firstListener::set));
            var secondListener = new AtomicReference<AudioFrame>();
            serverSession.addParticipant(new GroupCallParticipant(
                    PEER, clientOpts.localAudioSsrc(), secondListener::set));

            assertEquals(1, serverSession.subscribedSsrcs().size(),
                    "resubscribe must not stack");
        }
    }

    // 16 kHz mono sine frame.
    private static short[] sineFrame(int freqHz) {
        var pcm = new short[160];
        for (var i = 0; i < 160; i++) {
            pcm[i] = (short) (Math.sin(i * 2 * Math.PI * freqHz / 16_000.0) * 0x4000);
        }
        return pcm;
    }

    // In-memory paired DatagramTransports; what one side sends, the other's listener receives.
    private static final class LoopbackPair implements DatagramTransport {
        private final LinkedBlockingQueue<byte[]> inbound;
        private final LinkedBlockingQueue<byte[]> peerInbound;
        private volatile InboundListener listener;
        private final Thread pump;
        private volatile boolean closed;

        private LoopbackPair(LinkedBlockingQueue<byte[]> inbound,
                             LinkedBlockingQueue<byte[]> peerInbound) {
            this.inbound = inbound;
            this.peerInbound = peerInbound;
            this.pump = Thread.ofVirtual().name("group-loopback-pump").start(this::run);
        }

        static LoopbackPair[] pair() {
            var q1 = new LinkedBlockingQueue<byte[]>();
            var q2 = new LinkedBlockingQueue<byte[]>();
            return new LoopbackPair[]{
                    new LoopbackPair(q1, q2),
                    new LoopbackPair(q2, q1)
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
            if (!closed) peerInbound.offer(packet);
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

    // Synthetic CallService-shaped stand-in for the call engine.
    private static final class RecordingEngine extends CallService {
        RecordingEngine() {
            super(null, null);
        }
    }
}
