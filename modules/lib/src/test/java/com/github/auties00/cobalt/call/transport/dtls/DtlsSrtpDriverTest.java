package com.github.auties00.cobalt.call.transport.dtls;

import com.github.auties00.cobalt.call.transport.ice.DatagramTransport;
import com.github.auties00.cobalt.call.rtp.srtp.SrtpRole;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Round-trip tests for {@link DtlsSrtpDriver} — handshakes a
 * client-side and a server-side driver against each other over a
 * loopback {@link DatagramTransport} pair, verifies the negotiated
 * {@code SrtpEndpoint}s on both sides, and exercises the RFC 7983
 * demux for inbound STUN / SRTP packets.
 */
public class DtlsSrtpDriverTest {

    /**
     * Drives both sides of a DTLS-SRTP handshake to completion over a
     * loopback transport and verifies both sides obtain a valid
     * {@code SrtpEndpoint}.
     */
    @Test
    public void handshakeRoundTrip() throws Exception {
        var clientCert = DtlsCertificate.generate();
        var serverCert = DtlsCertificate.generate();
        var transports = LoopbackDatagramPair.pair();

        try (var clientDriver = new DtlsSrtpDriver(transports[0], SrtpRole.CLIENT,
                clientCert, serverCert.sha256Fingerprint());
             var serverDriver = new DtlsSrtpDriver(transports[1], SrtpRole.SERVER,
                     serverCert, clientCert.sha256Fingerprint())) {
            serverDriver.start();
            clientDriver.start();

            var clientSrtp = clientDriver.awaitHandshake(10, TimeUnit.SECONDS);
            var serverSrtp = serverDriver.awaitHandshake(10, TimeUnit.SECONDS);

            assertNotNull(clientSrtp, "client should obtain an SrtpEndpoint");
            assertNotNull(serverSrtp, "server should obtain an SrtpEndpoint");
            assertSame(clientSrtp, clientDriver.srtpEndpoint());
            assertSame(serverSrtp, serverDriver.srtpEndpoint());
        }
    }

    /**
     * A handshake against a peer whose fingerprint doesn't match the
     * advertised one fails on both sides. (Simulates an MITM that
     * substituted its own cert.)
     */
    @Test
    public void mismatchedFingerprintFailsHandshake() throws Exception {
        var clientCert = DtlsCertificate.generate();
        var serverCert = DtlsCertificate.generate();
        var bogusCert = DtlsCertificate.generate();
        var transports = LoopbackDatagramPair.pair();

        try (var clientDriver = new DtlsSrtpDriver(transports[0], SrtpRole.CLIENT,
                clientCert, bogusCert.sha256Fingerprint());  // wrong!
             var serverDriver = new DtlsSrtpDriver(transports[1], SrtpRole.SERVER,
                     serverCert, clientCert.sha256Fingerprint())) {
            serverDriver.start();
            clientDriver.start();

            assertThrows(java.io.IOException.class,
                    () -> clientDriver.awaitHandshake(10, TimeUnit.SECONDS));
            // The server side surfaces the failure too — peer's TLS
            // alert closes the connection.
            assertThrows(java.io.IOException.class,
                    () -> serverDriver.awaitHandshake(10, TimeUnit.SECONDS));
        }
    }

    /**
     * The RFC 7983 byte-0 demultiplexer routes STUN (0..3), DTLS
     * (20..63), and SRTP (128..191) to the right handler. Verified by
     * synthesising packets directly into a paired transport and
     * checking which handler fires.
     */
    @Test
    public void rfc7983DemuxRoutesByLeadingByte() throws Exception {
        var localCert = DtlsCertificate.generate();
        var peerCert = DtlsCertificate.generate();
        var transports = LoopbackDatagramPair.pair();

        try (var driver = new DtlsSrtpDriver(transports[0], SrtpRole.CLIENT,
                localCert, peerCert.sha256Fingerprint())) {
            var stunCaptured = new AtomicReference<byte[]>();
            var srtpCaptured = new AtomicReference<byte[]>();
            driver.setStunHandler(stunCaptured::set);
            driver.setSrtpHandler(srtpCaptured::set);

            // Inject a STUN-shaped packet (byte 0 = 0x00 — STUN
            // binding request).
            var stunPacket = new byte[]{0x00, 0x01, 0x00, 0x00, 0x21, 0x12, (byte) 0xA4, 0x42};
            transports[1].send(stunPacket);

            // Inject an SRTP-shaped packet (byte 0 with version=2
            // — high bit set — value 0x80 falls in 128..191).
            var srtpPacket = new byte[]{(byte) 0x80, 0x00, 0x00, 0x01};
            transports[1].send(srtpPacket);

            // Polling — the inbound listener fires synchronously
            // when the LoopbackDatagramPair pumps, so by the time
            // sendInverse returns the handler has been invoked.
            // But sendInverse is async via a queue; small wait.
            for (int i = 0; i < 50 && (stunCaptured.get() == null || srtpCaptured.get() == null); i++) {
                Thread.sleep(10);
            }

            assertNotNull(stunCaptured.get(), "STUN handler must fire for byte-0=0x00");
            assertNotNull(srtpCaptured.get(), "SRTP handler must fire for byte-0=0x80");
            assertEquals(0x00, stunCaptured.get()[0] & 0xFF);
            assertEquals(0x80, srtpCaptured.get()[0] & 0xFF);
        }
    }

    /**
     * {@link DtlsSrtpDriver#close()} unblocks an in-flight
     * {@link DtlsSrtpDriver#awaitHandshake} with an
     * {@link java.io.IOException}.
     */
    @Test
    public void closeUnblocksAwaitHandshake() throws Exception {
        var localCert = DtlsCertificate.generate();
        var peerCert = DtlsCertificate.generate();
        var transports = LoopbackDatagramPair.pair();

        var driver = new DtlsSrtpDriver(transports[0], SrtpRole.CLIENT,
                localCert, peerCert.sha256Fingerprint());
        driver.start();
        // Don't start a peer — handshake will block forever, so
        // close() must unblock it.
        Thread.ofVirtual().start(() -> {
            try {
                Thread.sleep(200);
            } catch (InterruptedException _) {
            }
            driver.close();
        });
        assertThrows(java.io.IOException.class,
                () -> driver.awaitHandshake(5, TimeUnit.SECONDS));
        assertNull(driver.srtpEndpoint());
    }

    /**
     * Constructing with a wrong-sized fingerprint is rejected.
     */
    @Test
    public void wrongFingerprintLengthRejected() {
        var cert = DtlsCertificate.generate();
        var transports = LoopbackDatagramPair.pair();
        assertThrows(IllegalArgumentException.class, () -> new DtlsSrtpDriver(
                transports[0], SrtpRole.CLIENT, cert, new byte[16]));
    }

    /**
     * In-memory pair of {@link DatagramTransport}s for unit-testing
     * the driver without a real UDP socket. What one side sends, the
     * other side's inbound listener receives.
     */
    private static final class LoopbackDatagramPair implements DatagramTransport {
        /**
         * The inbound queue this side reads from (filled by the
         * peer's outbound).
         */
        private final LinkedBlockingQueue<byte[]> inbound;

        /**
         * The peer's inbound queue, which we feed via
         * {@link #send(byte[])}.
         */
        private final LinkedBlockingQueue<byte[]> peerInbound;

        /**
         * Listener registered by the driver — fires synchronously
         * from the pump thread.
         */
        private volatile InboundListener listener;

        /**
         * Pump thread that drains {@link #inbound} into the
         * registered listener.
         */
        private final Thread pump;

        /**
         * Closed flag.
         */
        private volatile boolean closed;

        /**
         * Constructs a paired transport.
         *
         * @param inbound     this side's inbound queue
         * @param peerInbound the peer side's inbound queue
         */
        private LoopbackDatagramPair(LinkedBlockingQueue<byte[]> inbound,
                                     LinkedBlockingQueue<byte[]> peerInbound) {
            this.inbound = inbound;
            this.peerInbound = peerInbound;
            this.pump = Thread.ofVirtual().name("loopback-pump").start(this::run);
        }

        /**
         * Constructs a paired set of two loopback transports.
         *
         * @return a 2-element array with cross-wired queues
         */
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
            if (closed) {
                return;
            }
            peerInbound.offer(packet);
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

        /**
         * Drains the inbound queue, dispatching each packet to the
         * registered listener.
         */
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
