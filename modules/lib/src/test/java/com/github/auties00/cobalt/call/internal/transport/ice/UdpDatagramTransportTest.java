package com.github.auties00.cobalt.call.internal.transport.ice;

import com.github.auties00.cobalt.exception.WhatsAppCallException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Loopback tests for {@link UdpDatagramTransport}.
 */
@DisplayName("UdpDatagramTransport — loopback I/O")
class UdpDatagramTransportTest {

    @Test
    @DisplayName("send to a loopback DatagramSocket arrives unchanged")
    void sendArrivesAtPeer() throws Exception {
        try (var peer = new DatagramSocket(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0))) {
            peer.setSoTimeout(2000);
            var remote = new InetSocketAddress(InetAddress.getLoopbackAddress(), peer.getLocalPort());
            try (var transport = new UdpDatagramTransport(remote)) {
                assertNotNull(transport.localAddress());
                assertEquals(remote, transport.remoteAddress());

                var payload = new byte[]{1, 2, 3, 4, 5, 6, 7, 8};
                transport.send(payload);

                var buffer = new byte[64];
                var received = new DatagramPacket(buffer, buffer.length);
                peer.receive(received);
                assertEquals(payload.length, received.getLength());
                var actual = new byte[received.getLength()];
                System.arraycopy(buffer, 0, actual, 0, received.getLength());
                assertArrayEquals(payload, actual);
            }
        }
    }

    @Test
    @DisplayName("inbound datagrams fire onDatagram on the registered listener")
    void inboundDatagramsFireListener() throws Exception {
        try (var peer = new DatagramSocket(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0))) {
            var peerAddress = new InetSocketAddress(InetAddress.getLoopbackAddress(), peer.getLocalPort());
            try (var transport = new UdpDatagramTransport(peerAddress)) {
                var queue = new ArrayBlockingQueue<byte[]>(4);
                transport.setInboundListener(queue::offer);

                var local = transport.localAddress();
                var msg = new byte[]{9, 8, 7, 6};
                peer.send(new DatagramPacket(msg, msg.length,
                        new InetSocketAddress(InetAddress.getLoopbackAddress(), local.getPort())));

                var received = queue.poll(2, TimeUnit.SECONDS);
                assertNotNull(received, "inbound listener must fire within 2s");
                assertArrayEquals(msg, received);
            }
        }
    }

    @Test
    @DisplayName("close() is idempotent and rejects subsequent send")
    void closeBehavior() throws Exception {
        try (var peer = new DatagramSocket(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0))) {
            var transport = new UdpDatagramTransport(
                    new InetSocketAddress(InetAddress.getLoopbackAddress(), peer.getLocalPort()));
            transport.close();
            transport.close();
            assertThrows(WhatsAppCallException.Ice.class,
                    () -> transport.send(new byte[]{1}));
        }
    }
}
