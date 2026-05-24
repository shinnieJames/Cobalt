package com.github.auties00.cobalt.call.internal.transport.sctp;

import com.github.auties00.cobalt.call.internal.rtp.srtp.SrtpRole;
import com.github.auties00.cobalt.call.internal.transport.dtls.DtlsCertificate;
import com.github.auties00.cobalt.call.internal.transport.dtls.DtlsSrtpDriver;
import com.github.auties00.cobalt.call.internal.transport.ice.DatagramTransport;
import com.github.auties00.cobalt.call.internal.transport.sctp.datachannel.DataChannel;
import com.github.auties00.cobalt.call.internal.transport.sctp.datachannel.DataChannelOptions;
import com.github.auties00.cobalt.call.internal.transport.sctp.datachannel.DataChannelTransport;
import com.github.auties00.cobalt.exception.WhatsAppCallException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.StandardSocketOptions;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end Phase 5 test: drives the full stack —
 * UDP → Cobalt {@link DatagramTransport} → {@link DtlsSrtpDriver} →
 * BC DTLS application-data transport → {@link SctpDtlsBridge} +
 * {@link DataChannelTransport} → SCTP INIT/INIT-ACK handshake →
 * DCEP data channel open — on loopback UDP sockets.
 *
 * <p>Proves the entire transport pipeline assembles cleanly and a
 * binary message round-trips through the negotiated data channel.
 */
@DisplayName("SCTP-over-DTLS — Phase 5 loopback")
class SctpOverDtlsLoopbackTest {

    @Test
    @DisplayName("two peers complete DTLS, SCTP, DCEP, and round-trip a binary message")
    void fullStackRoundTrip() throws Exception {
        int portA, portB;
        try (var t1 = new DatagramSocket(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0));
             var t2 = new DatagramSocket(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0))) {
            portA = t1.getLocalPort();
            portB = t2.getLocalPort();
        }
        var addrA = new InetSocketAddress(InetAddress.getLoopbackAddress(), portA);
        var addrB = new InetSocketAddress(InetAddress.getLoopbackAddress(), portB);

        var sideAUdp = new BoundUdp(addrA, addrB);
        var sideBUdp = new BoundUdp(addrB, addrA);

        var certA = DtlsCertificate.generate();
        var certB = DtlsCertificate.generate();

        try (var driverA = new DtlsSrtpDriver(sideAUdp, SrtpRole.CLIENT, certA, certB.sha256Fingerprint());
             var driverB = new DtlsSrtpDriver(sideBUdp, SrtpRole.SERVER, certB, certA.sha256Fingerprint())) {
            driverA.start();
            driverB.start();
            assertNotNull(driverA.awaitHandshake(20, TimeUnit.SECONDS));
            assertNotNull(driverB.awaitHandshake(20, TimeUnit.SECONDS));

            var dtlsA = driverA.dtlsTransport();
            var dtlsB = driverB.dtlsTransport();
            assertNotNull(dtlsA, "client must expose DTLS app-data transport");
            assertNotNull(dtlsB, "server must expose DTLS app-data transport");

            // Build the SCTP transports + bridges. Each side's
            // outbound SCTP packet → DTLS encrypted record on its
            // own DTLS transport.
            var sctpA = new DataChannelTransport(true /* dtlsClient */, p -> {
                try { dtlsA.send(p, 0, p.length); } catch (IOException _) { }
            });
            var sctpB = new DataChannelTransport(false /* dtlsClient */, p -> {
                try { dtlsB.send(p, 0, p.length); } catch (IOException _) { }
            });

            try (var bridgeA = new SctpDtlsBridge(dtlsA, sctpA);
                 var bridgeB = new SctpDtlsBridge(dtlsB, sctpB)) {
                // WebRTC standard: bind local 5000, connect remote 5000.
                sctpA.bind(5000);
                sctpB.bind(5000);

                // SCTP COOKIE-ECHO/COOKIE-ACK requires both sides to
                // start their handshakes concurrently. WebRTC uses
                // the "simultaneous open" pattern.
                var connectLatch = new CountDownLatch(2);
                var connectA = Thread.ofVirtual().name("sctp-connect-A").start(() -> {
                    try { sctpA.connect(5000, 10, TimeUnit.SECONDS); }
                    catch (WhatsAppCallException.Sctp _) { /* surfaces below */ }
                    finally { connectLatch.countDown(); }
                });
                var connectB = Thread.ofVirtual().name("sctp-connect-B").start(() -> {
                    try { sctpB.connect(5000, 10, TimeUnit.SECONDS); }
                    catch (WhatsAppCallException.Sctp _) { /* surfaces below */ }
                    finally { connectLatch.countDown(); }
                });
                assertTrue(connectLatch.await(15, TimeUnit.SECONDS),
                        "SCTP simultaneous-open handshake must complete within 15s");

                // Open a data channel from side A; side B observes it
                // via the peer-open listener.
                var bMessage = new AtomicReference<byte[]>();
                var bChannelLatch = new CountDownLatch(1);
                sctpB.setPeerOpenListener(channel -> {
                    channel.setMessageListener(msg -> {
                        if (msg instanceof DataChannel.Message.Binary bin) {
                            bMessage.set(bin.data());
                            bChannelLatch.countDown();
                        }
                    });
                });

                var channel = sctpA.open("test-channel",
                        DataChannelOptions.reliable());
                // Wait for the channel to reach OPEN — DCEP ACK from B.
                var openLatch = new CountDownLatch(1);
                channel.setOpenListener(openLatch::countDown);
                assertTrue(openLatch.await(5, TimeUnit.SECONDS),
                        "data channel must reach OPEN within 5s");

                channel.send(new byte[]{0x11, 0x22, 0x33, 0x44, 0x55});
                assertTrue(bChannelLatch.await(5, TimeUnit.SECONDS),
                        "binary message must arrive on side B within 5s");
                assertArrayEquals(new byte[]{0x11, 0x22, 0x33, 0x44, 0x55}, bMessage.get());

                connectA.join(1000);
                connectB.join(1000);
            }
        }
    }

    /**
     * Test helper — a {@link DatagramTransport} backed by a
     * {@link DatagramChannel} bound to a specific local port (so two
     * endpoints can know each other's port at construction time).
     */
    private static final class BoundUdp implements DatagramTransport, AutoCloseable {
        private final DatagramChannel channel;
        private final InetSocketAddress local;
        private final InetSocketAddress remote;
        private final Thread receiver;
        private volatile InboundListener listener;
        private volatile boolean closed;

        BoundUdp(InetSocketAddress local, InetSocketAddress remote) throws IOException {
            this.channel = DatagramChannel.open();
            this.channel.setOption(StandardSocketOptions.SO_REUSEADDR, true);
            this.channel.bind(local);
            this.channel.connect(remote);
            this.channel.configureBlocking(true);
            this.local = (InetSocketAddress) this.channel.getLocalAddress();
            this.remote = remote;
            this.receiver = Thread.ofVirtual()
                    .name("sctp-loop-recv-" + this.local.getPort())
                    .unstarted(this::receiveLoop);
            this.receiver.setDaemon(true);
            this.receiver.start();
        }

        @Override public InetSocketAddress localAddress()  { return local; }
        @Override public InetSocketAddress remoteAddress() { return remote; }
        @Override public void send(byte[] packet) {
            if (closed) throw new WhatsAppCallException.Ice("closed");
            try { channel.write(ByteBuffer.wrap(packet)); }
            catch (IOException e) { throw new WhatsAppCallException.Ice("send", e); }
        }
        @Override public void setInboundListener(InboundListener l) { this.listener = l; }
        @Override public void close() {
            closed = true;
            try { channel.close(); } catch (IOException _) { }
            receiver.interrupt();
        }

        private void receiveLoop() {
            var buf = ByteBuffer.allocate(64 * 1024);
            while (!closed && !Thread.currentThread().isInterrupted()) {
                buf.clear();
                int read;
                try { read = channel.read(buf); }
                catch (IOException _) { return; }
                if (read <= 0) continue;
                var bytes = new byte[read];
                buf.flip();
                buf.get(bytes);
                var l = listener;
                if (l != null) {
                    try { l.onDatagram(bytes); } catch (RuntimeException _) { }
                }
            }
        }
    }
}
