package com.github.auties00.cobalt.call.internal.transport.dtls;

import com.github.auties00.cobalt.call.internal.rtp.srtp.SrtpRole;
import com.github.auties00.cobalt.call.internal.transport.ice.DatagramTransport;
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
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * End-to-end smoke test for the Phase 3 + Phase 4 path: drives the
 * full DTLS-SRTP handshake through {@link DtlsSrtpDriver} on top of a
 * pair of {@link com.github.auties00.cobalt.call.internal.transport.ice.UdpDatagramTransport}-shaped
 * UDP sockets on the loopback interface.
 *
 * <p>{@link DtlsSrtpDriverTest} already exercises the driver
 * exhaustively over an in-memory {@code LoopbackDatagramPair}; this
 * test adds the real-socket layer to prove the same pipeline works
 * when the bytes actually traverse the kernel — the same shape Cobalt
 * uses post-{@code connectRelay} when DTLS sits on the
 * {@code UdpDatagramTransport} returned by
 * {@code WaRelayConnector}.
 */
@DisplayName("DtlsSrtpDriver — Phase 3+4 smoke test over loopback UDP")
class DtlsSrtpDriverUdpSmokeTest {

    @Test
    @DisplayName("client + server DTLS handshake completes over UDP loopback sockets")
    void udpLoopbackDtlsHandshake() throws Exception {
        // Pre-allocate two ephemeral ports so each side knows the
        // other's port at construction time.
        int portA, portB;
        try (var t1 = new DatagramSocket(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0));
             var t2 = new DatagramSocket(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0))) {
            portA = t1.getLocalPort();
            portB = t2.getLocalPort();
        }
        var addrA = new InetSocketAddress(InetAddress.getLoopbackAddress(), portA);
        var addrB = new InetSocketAddress(InetAddress.getLoopbackAddress(), portB);

        try (var sideA = new TwoEndedUdp(addrA, addrB);
             var sideB = new TwoEndedUdp(addrB, addrA)) {
            var certA = DtlsCertificate.generate();
            var certB = DtlsCertificate.generate();

            try (var clientDriver = new DtlsSrtpDriver(
                            sideA, SrtpRole.CLIENT, certA, certB.sha256Fingerprint());
                 var serverDriver = new DtlsSrtpDriver(
                            sideB, SrtpRole.SERVER, certB, certA.sha256Fingerprint())) {

                clientDriver.start();
                serverDriver.start();

                var clientSrtp = clientDriver.awaitHandshake(20, TimeUnit.SECONDS);
                var serverSrtp = serverDriver.awaitHandshake(20, TimeUnit.SECONDS);

                assertNotNull(clientSrtp);
                assertNotNull(serverSrtp);

                // Prove key derivation via an SRTP round-trip — the
                // same shape as the other DTLS tests.
                var rtp = makeRtpPacket();
                var encrypted = clientSrtp.protectRtp(rtp);
                var decrypted = serverSrtp.unprotectRtp(encrypted);
                assertArrayEquals(rtp, decrypted);
            }
        }
    }

    /**
     * Returns a 24-byte RTP packet that the SRTP layer accepts
     * (12 bytes header + 12 bytes payload).
     */
    private static byte[] makeRtpPacket() {
        var p = new byte[24];
        p[0] = (byte) 0x80;
        p[2] = 0; p[3] = 1;
        p[4] = 0; p[5] = 0; p[6] = 0; p[7] = 1;
        p[8] = (byte) 0xCA; p[9] = (byte) 0xFE;
        p[10] = (byte) 0xBA; p[11] = (byte) 0xBE;
        for (var i = 12; i < 24; i++) p[i] = (byte) (i & 0xFF);
        return p;
    }

    /**
     * Test helper — a Cobalt
     * {@link DatagramTransport} backed by a UDP socket bound to a
     * specific local port (so the two endpoints of the handshake can
     * know each other's ports at construction time). Functionally
     * equivalent to {@link com.github.auties00.cobalt.call.internal.transport.ice.UdpDatagramTransport}
     * but with explicit local-port control.
     */
    private static final class TwoEndedUdp implements DatagramTransport {
        private final DatagramChannel channel;
        private final InetSocketAddress local;
        private final InetSocketAddress remote;
        private final Thread receiver;
        private volatile InboundListener listener;
        private volatile boolean closed;

        TwoEndedUdp(InetSocketAddress local, InetSocketAddress remote) throws IOException {
            this.channel = DatagramChannel.open();
            this.channel.setOption(StandardSocketOptions.SO_REUSEADDR, true);
            this.channel.bind(local);
            this.channel.connect(remote);
            this.channel.configureBlocking(true);
            this.local = (InetSocketAddress) this.channel.getLocalAddress();
            this.remote = remote;
            this.receiver = Thread.ofVirtual()
                    .name("smoke-udp-recv-" + this.local.getPort())
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
