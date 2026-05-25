package com.github.auties00.cobalt.call.internal.transport.sctp.datachannel;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end loopback tests for the {@link DataChannelTransport}, spinning up two transports
 * back-to-back through their outbound sinks and exercising in-band DCEP open/ack, bidirectional
 * message routing, and channel teardown. The harness in {@link LoopbackPair} cross-wires the two
 * transports through queue-backed virtual-thread pumps to reproduce the SCTP simultaneous-open
 * handshake. The tests fail loudly if libusrsctp is not loadable, since silently skipping would
 * let a broken native bundle ship green.
 */
public class DataChannelTransportTest {

    // Fresh SCTP port per LoopbackPair: the process-singleton usrsctp stack would otherwise see a
    // bind collision across tests.
    private static final AtomicInteger NEXT_PORT = new AtomicInteger(5000);

    @Test
    public void inBandOpenHandshakeRoundTrips() throws Exception {
        try (var pair = new LoopbackPair()) {
            var peerOpened = new AtomicReference<DataChannel>();
            var peerOpenLatch = new CountDownLatch(1);
            pair.server.setPeerOpenListener(channel -> {
                peerOpened.set(channel);
                peerOpenLatch.countDown();
            });

            pair.connect();

            var clientOpenLatch = new CountDownLatch(1);
            var clientChannel = pair.client.open("chat",
                    DataChannelOptions.reliable().withProtocol("v1"));
            clientChannel.setOpenListener(clientOpenLatch::countDown);

            assertTrue(peerOpenLatch.await(5, TimeUnit.SECONDS),
                    "server side did not observe peer-open within 5s");
            assertTrue(clientOpenLatch.await(5, TimeUnit.SECONDS),
                    "client side did not transition to OPEN within 5s");

            var serverChannel = peerOpened.get();
            assertEquals("chat", serverChannel.label());
            assertEquals("v1", serverChannel.protocol());
            assertEquals(clientChannel.streamId(), serverChannel.streamId());
            assertEquals(DataChannelState.OPEN, clientChannel.state());
            assertEquals(DataChannelState.OPEN, serverChannel.state());
        }
    }

    @Test
    public void bidirectionalMessagesRoundTrip() throws Exception {
        try (var pair = new LoopbackPair()) {
            BlockingQueue<DataChannel.Message> serverInbox = new ArrayBlockingQueue<>(16);
            var serverChannelRef = new AtomicReference<DataChannel>();
            var peerLatch = new CountDownLatch(1);
            pair.server.setPeerOpenListener(channel -> {
                channel.setMessageListener(serverInbox::offer);
                serverChannelRef.set(channel);
                peerLatch.countDown();
            });

            pair.connect();

            BlockingQueue<DataChannel.Message> clientInbox = new ArrayBlockingQueue<>(16);
            var clientChannel = pair.client.open("data", DataChannelOptions.reliable());
            clientChannel.setMessageListener(clientInbox::offer);

            assertTrue(peerLatch.await(5, TimeUnit.SECONDS));
            assertTrue(awaitState(clientChannel, DataChannelState.OPEN, 5_000));

            var server = serverChannelRef.get();
            assertNotNull(server);

            clientChannel.send("hello");
            clientChannel.send(new byte[]{1, 2, 3, 4});
            clientChannel.send("");
            clientChannel.send(new byte[0]);

            var first = serverInbox.poll(5, TimeUnit.SECONDS);
            var second = serverInbox.poll(5, TimeUnit.SECONDS);
            var third = serverInbox.poll(5, TimeUnit.SECONDS);
            var fourth = serverInbox.poll(5, TimeUnit.SECONDS);

            assertEquals("hello", ((DataChannel.Message.Text) first).value());
            assertArrayEquals(new byte[]{1, 2, 3, 4},
                    ((DataChannel.Message.Binary) second).data());
            assertEquals("", ((DataChannel.Message.Text) third).value());
            assertArrayEquals(new byte[0], ((DataChannel.Message.Binary) fourth).data());

            server.send("pong");
            var reply = clientInbox.poll(5, TimeUnit.SECONDS);
            assertEquals("pong", ((DataChannel.Message.Text) reply).value());
        }
    }

    @Test
    public void streamIdParityFollowsDtlsRole() throws Exception {
        try (var pair = new LoopbackPair()) {
            List<DataChannel> peerChannels = new ArrayList<>();
            var latch = new CountDownLatch(2);
            pair.server.setPeerOpenListener(channel -> {
                peerChannels.add(channel);
                latch.countDown();
            });

            pair.connect();

            var first = pair.client.open("a", DataChannelOptions.reliable());
            var second = pair.client.open("b", DataChannelOptions.reliable());
            assertEquals(1, first.streamId(), "first client-allocated id is 1 (odd)");
            assertEquals(3, second.streamId(), "second is 3, advancing by 2 within parity");

            assertTrue(latch.await(5, TimeUnit.SECONDS));
        }
    }

    @Test
    public void negotiatedChannelSkipsDcep() throws Exception {
        try (var pair = new LoopbackPair()) {
            var peerOpenCount = new ArrayList<DataChannel>();
            pair.server.setPeerOpenListener(peerOpenCount::add);

            pair.connect();

            BlockingQueue<DataChannel.Message> serverInbox = new ArrayBlockingQueue<>(4);
            var serverNegotiated = pair.server.open("oob",
                    DataChannelOptions.reliable().withNegotiatedStreamId(123));
            serverNegotiated.setMessageListener(serverInbox::offer);

            var clientNegotiated = pair.client.open("oob",
                    DataChannelOptions.reliable().withNegotiatedStreamId(123));

            assertEquals(DataChannelState.OPEN, serverNegotiated.state());
            assertEquals(DataChannelState.OPEN, clientNegotiated.state());

            clientNegotiated.send("via-oob");
            var msg = serverInbox.poll(5, TimeUnit.SECONDS);
            assertEquals("via-oob", ((DataChannel.Message.Text) msg).value());
            assertTrue(peerOpenCount.isEmpty(),
                    "peer-open listener must not fire for out-of-band negotiated channels");
        }
    }

    @Test
    public void closeIsIdempotentAndFiresListenerOnce() throws Exception {
        try (var pair = new LoopbackPair()) {
            pair.connect();

            var channel = pair.client.open("disposable", DataChannelOptions.reliable());
            var fireCount = new int[1];
            channel.setCloseListener(() -> fireCount[0]++);

            channel.close();
            channel.close();
            channel.close();

            assertEquals(1, fireCount[0], "close listener fires exactly once");
            assertEquals(DataChannelState.CLOSED, channel.state());
            assertTrue(pair.client.channel(channel.streamId()).isEmpty(),
                    "transport must drop the closed channel from its registry");
        }
    }

    @Test
    public void sendOnConnectingChannelRejected() {
        try (var pair = new LoopbackPair()) {
            var fresh = new DataChannel(pair.client, 0, "x", "",
                    true, false, OptionalInt.empty(), OptionalInt.empty(),
                    DataChannelState.CONNECTING);
            var ex = assertThrows(IllegalStateException.class, () -> fresh.send("noop"));
            assertTrue(ex.getMessage().contains("CONNECTING"));
        }
    }

    private static boolean awaitState(DataChannel channel, DataChannelState target, long timeoutMs)
            throws InterruptedException {
        var deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs);
        while (channel.state() != target) {
            if (System.nanoTime() > deadline) {
                return false;
            }
            Thread.sleep(10);
        }
        return true;
    }

    /**
     * A pair of {@link DataChannelTransport} instances cross-wired through their outbound sinks:
     * each side's outbound packets are enqueued and drained on a dedicated virtual thread that
     * feeds the other side. The pumps gate on both sides having posted their first packet, which
     * is what makes RFC 4960 5.2.4 simultaneous-open work; both sockets reach COOKIE_WAIT before
     * either INIT is delivered, so each peer's INIT-collision handler merges the two pending
     * associations into one.
     */
    private static final class LoopbackPair implements AutoCloseable {
        // client uses odd stream ids, server uses even, per the DTLS role split in RFC 8832.
        final DataChannelTransport client;
        final DataChannelTransport server;

        private final int port = NEXT_PORT.getAndIncrement();
        private final LinkedBlockingQueue<byte[]> clientToServer = new LinkedBlockingQueue<>();
        private final LinkedBlockingQueue<byte[]> serverToClient = new LinkedBlockingQueue<>();
        // Tripped on the first outbound packet, i.e. once the side has entered COOKIE_WAIT.
        private final CountDownLatch clientPosted = new CountDownLatch(1);
        private final CountDownLatch serverPosted = new CountDownLatch(1);
        private final Thread clientPump;
        private final Thread serverPump;

        LoopbackPair() {
            this.client = new DataChannelTransport(true, packet -> {
                clientPosted.countDown();
                clientToServer.offer(packet);
            });
            this.server = new DataChannelTransport(false, packet -> {
                serverPosted.countDown();
                serverToClient.offer(packet);
            });
            this.clientPump = startPump("client→server-pump", clientToServer, server);
            this.serverPump = startPump("server→client-pump", serverToClient, client);
        }

        private Thread startPump(String name, LinkedBlockingQueue<byte[]> source,
                                 DataChannelTransport dest) {
            return Thread.ofVirtual().name(name).start(() -> {
                try {
                    clientPosted.await();
                    serverPosted.await();
                    while (!Thread.currentThread().isInterrupted()) {
                        var packet = source.take();
                        try {
                            dest.feedInboundPacket(packet);
                        } catch (Throwable _) {
                        }
                    }
                } catch (InterruptedException _) {
                }
            });
        }

        void connect() throws InterruptedException {
            server.bind(port);
            client.bind(port);
            var clientFailure = new AtomicReference<Throwable>();
            var serverFailure = new AtomicReference<Throwable>();
            var clientThread = Thread.ofVirtual().start(() -> {
                try {
                    client.connect(port, 5, TimeUnit.SECONDS);
                } catch (Throwable t) {
                    clientFailure.set(t);
                }
            });
            var serverThread = Thread.ofVirtual().start(() -> {
                try {
                    server.connect(port, 5, TimeUnit.SECONDS);
                } catch (Throwable t) {
                    serverFailure.set(t);
                }
            });
            clientThread.join();
            serverThread.join();
            if (clientFailure.get() != null) {
                throw new AssertionError("client handshake failed", clientFailure.get());
            }
            if (serverFailure.get() != null) {
                throw new AssertionError("server handshake failed", serverFailure.get());
            }
        }

        @Override
        public void close() {
            clientPump.interrupt();
            serverPump.interrupt();
            try {
                client.close();
            } catch (Throwable _) {
            }
            try {
                server.close();
            } catch (Throwable _) {
            }
        }
    }
}
