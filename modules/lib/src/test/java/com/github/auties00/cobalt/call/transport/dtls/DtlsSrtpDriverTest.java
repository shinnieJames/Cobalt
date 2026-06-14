package com.github.auties00.cobalt.call.transport.dtls;

import com.github.auties00.cobalt.call.transport.ice.DatagramTransport;
import com.github.auties00.cobalt.call.rtp.srtp.SrtpRole;
import org.junit.jupiter.api.Test;

import java.io.IOException;
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
 * Round-trip coverage for {@link DtlsSrtpDriver}: handshakes a client-side and a server-side
 * driver against each other over a loopback {@link DatagramTransport} pair, verifies the negotiated
 * {@code SrtpEndpoint} on both sides, and exercises the RFC 7983 byte-0 demultiplexer for inbound
 * STUN and SRTP packets. The nested {@code LoopbackDatagramPair} is an in-memory transport whose
 * pump thread dispatches each datagram synchronously to the peer's inbound listener, so no UDP
 * socket is involved.
 */
public class DtlsSrtpDriverTest {

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

    @Test
    public void mismatchedFingerprintFailsHandshake() throws Exception {
        var clientCert = DtlsCertificate.generate();
        var serverCert = DtlsCertificate.generate();
        var bogusCert = DtlsCertificate.generate();
        var transports = LoopbackDatagramPair.pair();

        try (var clientDriver = new DtlsSrtpDriver(transports[0], SrtpRole.CLIENT,
                clientCert, bogusCert.sha256Fingerprint());
             var serverDriver = new DtlsSrtpDriver(transports[1], SrtpRole.SERVER,
                     serverCert, clientCert.sha256Fingerprint())) {
            serverDriver.start();
            clientDriver.start();

            assertThrows(IOException.class,
                    () -> clientDriver.awaitHandshake(10, TimeUnit.SECONDS));
            // Server surfaces the failure too: the peer's TLS alert closes the connection.
            assertThrows(IOException.class,
                    () -> serverDriver.awaitHandshake(10, TimeUnit.SECONDS));
        }
    }

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

            // byte 0 = 0x00 is a STUN binding request, in the RFC 7983 STUN range 0..3.
            var stunPacket = new byte[]{0x00, 0x01, 0x00, 0x00, 0x21, 0x12, (byte) 0xA4, 0x42};
            transports[1].send(stunPacket);

            // byte 0 = 0x80 (RTP version 2, high bit set) falls in the RFC 7983 SRTP range 128..191.
            var srtpPacket = new byte[]{(byte) 0x80, 0x00, 0x00, 0x01};
            transports[1].send(srtpPacket);

            // The pump dispatches asynchronously via the inbound queue, so wait for both handlers.
            for (var i = 0; i < 50 && (stunCaptured.get() == null || srtpCaptured.get() == null); i++) {
                Thread.sleep(10);
            }

            assertNotNull(stunCaptured.get(), "STUN handler must fire for byte-0=0x00");
            assertNotNull(srtpCaptured.get(), "SRTP handler must fire for byte-0=0x80");
            assertEquals(0x00, stunCaptured.get()[0] & 0xFF);
            assertEquals(0x80, srtpCaptured.get()[0] & 0xFF);
        }
    }

    @Test
    public void closeUnblocksAwaitHandshake() throws Exception {
        var localCert = DtlsCertificate.generate();
        var peerCert = DtlsCertificate.generate();
        var transports = LoopbackDatagramPair.pair();

        var driver = new DtlsSrtpDriver(transports[0], SrtpRole.CLIENT,
                localCert, peerCert.sha256Fingerprint());
        driver.start();
        // No peer is started, so the handshake blocks indefinitely until close() unblocks it.
        Thread.ofVirtual().start(() -> {
            try {
                Thread.sleep(200);
            } catch (InterruptedException _) {
            }
            driver.close();
        });
        assertThrows(IOException.class,
                () -> driver.awaitHandshake(5, TimeUnit.SECONDS));
        assertNull(driver.srtpEndpoint());
    }

    @Test
    public void wrongFingerprintLengthRejected() {
        var cert = DtlsCertificate.generate();
        var transports = LoopbackDatagramPair.pair();
        assertThrows(IllegalArgumentException.class, () -> new DtlsSrtpDriver(
                transports[0], SrtpRole.CLIENT, cert, new byte[16]));
    }

    /**
     * In-memory {@link DatagramTransport} pair for driving the handshake without a UDP socket: each
     * side's {@link #send(byte[])} enqueues onto the peer's inbound queue, and a per-side virtual
     * pump thread drains that queue into the registered {@code InboundListener}.
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
            this.pump = Thread.ofVirtual().name("loopback-pump").start(this::run);
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
