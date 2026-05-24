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
 * End-to-end loopback tests for the {@link DataChannelTransport} —
 * spins up two transports back-to-back through their outbound sinks,
 * exercises in-band DCEP open/ack, bidirectional message routing,
 * and channel teardown.
 *
 * <p>The tests fail loudly if libusrsctp is not loadable on the
 * running platform — silently skipping would let a broken native
 * bundle ship green.
 */
public class DataChannelTransportTest {

    /**
     * Counter handing out a fresh SCTP port to each
     * {@link LoopbackPair} so successive tests don't collide on
     * port binds inside the process-singleton {@code usrsctp}
     * stack.
     */
    private static final AtomicInteger NEXT_PORT = new AtomicInteger(5000);

    /**
     * In-band channel open/ack handshake: the "client" side opens a
     * channel; the "server" side observes a peer-open with the same
     * label and protocol; the client side observes its channel
     * transition to {@link DataChannelState#OPEN}.
     */
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

    /**
     * Once both sides are OPEN, string and binary messages flow in
     * both directions and PPID 56/57 (empty string/binary) round-trip
     * cleanly.
     */
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

    /**
     * Stream-id parity follows RFC 8832 §6 — the client allocates
     * odd, the server allocates even.
     */
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

    /**
     * An out-of-band negotiated channel skips DCEP entirely and is
     * usable immediately on both sides once they've each
     * {@link DataChannelTransport#open opened} with the same stream
     * id.
     */
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

    /**
     * {@link DataChannel#close()} unregisters the channel from the
     * transport and fires the close listener exactly once.
     */
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

    /**
     * Sending on a {@link DataChannelState#CONNECTING} channel raises
     * {@link IllegalStateException} naming the offending state.
     */
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

    /**
     * Polls the channel's state until it reaches {@code target} or the
     * timeout elapses.
     *
     * @param channel   the channel to observe
     * @param target    the state to wait for
     * @param timeoutMs the timeout in milliseconds
     * @return {@code true} if {@code target} was observed in time
     * @throws InterruptedException if the test thread is interrupted
     */
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
     * A pair of {@link DataChannelTransport} instances cross-wired
     * through their outbound sinks. Each side's outbound packets are
     * enqueued in a {@link LinkedBlockingQueue} and drained on a
     * dedicated virtual thread that calls the other side's
     * {@link DataChannelTransport#feedInboundPacket}.
     *
     * <p>Decoupling the outbound conn-output upcall from the inbound
     * delivery is what makes RFC 4960 §5.2.4 simultaneous-open
     * actually work: both sides can post their INIT and reach
     * {@code COOKIE_WAIT} before either INIT is processed by the
     * peer, so each peer's INIT-collision handler resolves the two
     * pending associations into one.
     */
    private static final class LoopbackPair implements AutoCloseable {
        /**
         * The "DTLS client" side — uses odd stream ids.
         */
        final DataChannelTransport client;

        /**
         * The "DTLS server" side — uses even stream ids.
         */
        final DataChannelTransport server;

        /**
         * Per-pair SCTP port — fresh for each test so the process
         * singleton {@code usrsctp} stack does not see a collision
         * across tests.
         */
        private final int port = NEXT_PORT.getAndIncrement();

        /**
         * Outbound packets posted by the client; the
         * {@link #clientPump} virtual thread drains this and pushes
         * the bytes into {@code server.feedInboundPacket}.
         */
        private final LinkedBlockingQueue<byte[]> clientToServer = new LinkedBlockingQueue<>();

        /**
         * Outbound packets posted by the server; the
         * {@link #serverPump} virtual thread drains this and pushes
         * the bytes into {@code client.feedInboundPacket}.
         */
        private final LinkedBlockingQueue<byte[]> serverToClient = new LinkedBlockingQueue<>();

        /**
         * Latch fired the first time the client posts an outbound
         * packet — indicates the client has entered
         * {@code COOKIE_WAIT}.
         */
        private final CountDownLatch clientPosted = new CountDownLatch(1);

        /**
         * Latch fired the first time the server posts an outbound
         * packet — indicates the server has entered
         * {@code COOKIE_WAIT}.
         */
        private final CountDownLatch serverPosted = new CountDownLatch(1);

        /**
         * Pump draining {@link #clientToServer} into {@code server}.
         */
        private final Thread clientPump;

        /**
         * Pump draining {@link #serverToClient} into {@code client}.
         */
        private final Thread serverPump;

        /**
         * Builds the pair and starts both pump threads. The pumps
         * block until both sides have posted their INIT — that's
         * what makes RFC 4960 §5.2.4 simultaneous-open work, since
         * each peer's INIT only reaches the other peer after both
         * sockets are in {@code COOKIE_WAIT}.
         */
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

        /**
         * Spawns a virtual thread that waits until both sides have
         * posted their first outbound packet, then drains
         * {@code source} into {@code dest.feedInboundPacket}.
         *
         * @param name   the thread name
         * @param source the queue to drain
         * @param dest   the transport to feed
         * @return the started thread
         */
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

        /**
         * Drives the SCTP simultaneous-open handshake — both peers
         * bind, then call {@link DataChannelTransport#connect(int, long, TimeUnit)}
         * concurrently on dedicated virtual threads. The pumps in
         * the constructor are gated on both sides having posted, so
         * each INIT only reaches the peer after both are in
         * {@code COOKIE_WAIT}, allowing the SCTP INIT-collision rule
         * to merge them into one association.
         *
         * @throws InterruptedException if the test thread is
         *                              interrupted while waiting for
         *                              the connect threads
         * @throws AssertionError       if either side reports a
         *                              handshake failure
         */
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
