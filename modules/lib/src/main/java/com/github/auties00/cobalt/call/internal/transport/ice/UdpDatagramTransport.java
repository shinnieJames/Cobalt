package com.github.auties00.cobalt.call.internal.transport.ice;

import com.github.auties00.cobalt.exception.WhatsAppCallException;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.StandardSocketOptions;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Implements {@link DatagramTransport} over an NIO {@link DatagramChannel} connected to a single
 * remote peer.
 *
 * <p>The channel is bound to an ephemeral local port and connected to the elected WA relay
 * endpoint, so every {@link #send(byte[]) send} and every inbound datagram flows over the same NAT
 * pinhole; {@link com.github.auties00.cobalt.call.internal.transport.relay.WaRelayConnector}
 * constructs an instance after a successful relay Allocate handshake. A dedicated virtual thread
 * services the channel's receive side and dispatches each datagram synchronously to the registered
 * {@link InboundListener}, exiting when {@link #close()} is called or when the channel closes
 * underneath it.
 */
public final class UdpDatagramTransport implements DatagramTransport {
    /**
     * The receive-buffer size in bytes.
     *
     * @implNote This implementation uses {@code 64 KiB}, sized to hold a maximal SCTP-over-DTLS
     * record: the WebRTC {@code maxMessageSize} is 16 KiB, and the value leaves headroom for DTLS
     * and SCTP framing overhead on top of it.
     */
    private static final int RECV_BUFFER_BYTES = 64 * 1024;

    /**
     * The underlying connected datagram channel.
     */
    private final DatagramChannel channel;

    /**
     * The local-side bound transport address.
     */
    private final InetSocketAddress local;

    /**
     * The remote-side connected transport address.
     */
    private final InetSocketAddress remote;

    /**
     * The virtual thread running the receive loop.
     */
    private final Thread receiver;

    /**
     * The currently registered inbound listener, or {@code null} when none is registered.
     */
    private final AtomicReference<InboundListener> listener = new AtomicReference<>();

    /**
     * Whether {@link #close()} has been invoked, used by the receive loop as its exit signal.
     */
    private final AtomicBoolean closed = new AtomicBoolean(false);

    /**
     * Opens a datagram channel bound to an ephemeral local port and connected to
     * {@code remoteAddress}, then starts the receive thread.
     *
     * <p>The channel is configured blocking with address reuse enabled, and the resolved local and
     * remote addresses are captured for {@link #localAddress()} and {@link #remoteAddress()}. The
     * receive thread is a daemon virtual thread named after the local port.
     *
     * @param remoteAddress the relay endpoint to connect to
     * @throws WhatsAppCallException.Ice if the channel cannot be opened, bound, or connected
     * @throws NullPointerException      if {@code remoteAddress} is {@code null}
     */
    public UdpDatagramTransport(InetSocketAddress remoteAddress) {
        Objects.requireNonNull(remoteAddress, "remoteAddress cannot be null");
        try {
            this.channel = DatagramChannel.open();
            this.channel.setOption(StandardSocketOptions.SO_REUSEADDR, true);
            this.channel.bind(new InetSocketAddress(0));
            this.channel.connect(remoteAddress);
            this.channel.configureBlocking(true);
            this.local = (InetSocketAddress) this.channel.getLocalAddress();
            this.remote = (InetSocketAddress) this.channel.getRemoteAddress();
        } catch (IOException e) {
            throw new WhatsAppCallException.Ice("UDP datagram transport open failed for " + remoteAddress, e);
        }
        this.receiver = Thread.ofVirtual()
                .name("udp-dgram-recv-" + this.local.getPort())
                .unstarted(this::receiveLoop);
        this.receiver.setDaemon(true);
        this.receiver.start();
    }

    @Override
    public InetSocketAddress localAddress() {
        return local;
    }

    @Override
    public InetSocketAddress remoteAddress() {
        return remote;
    }

    @Override
    public void send(byte[] packet) {
        Objects.requireNonNull(packet, "packet cannot be null");
        if (closed.get()) {
            throw new WhatsAppCallException.Ice("UDP datagram transport already closed");
        }
        try {
            channel.write(ByteBuffer.wrap(packet));
        } catch (IOException e) {
            throw new WhatsAppCallException.Ice("UDP send failed", e);
        }
    }

    @Override
    public void setInboundListener(InboundListener listener) {
        this.listener.set(listener);
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        try {
            channel.close();
        } catch (IOException _) {
        }
        receiver.interrupt();
    }

    /**
     * Runs the receive loop, blocking on {@link DatagramChannel#read(ByteBuffer)} and dispatching
     * each datagram synchronously to the current {@link InboundListener}.
     *
     * <p>The loop runs until the transport is closed or the thread is interrupted, and exits on a
     * read error or an end-of-stream read. A zero-length read is ignored. An exception thrown by
     * the listener is swallowed so that one bad datagram does not stop reception.
     */
    private void receiveLoop() {
        var buffer = ByteBuffer.allocate(RECV_BUFFER_BYTES);
        while (!closed.get() && !Thread.currentThread().isInterrupted()) {
            buffer.clear();
            int read;
            try {
                read = channel.read(buffer);
            } catch (IOException _) {
                return;
            }
            if (read < 0) {
                return;
            }
            if (read == 0) {
                continue;
            }
            var bytes = new byte[read];
            buffer.flip();
            buffer.get(bytes);
            var l = listener.get();
            if (l != null) {
                try {
                    l.onDatagram(bytes);
                } catch (RuntimeException _) {
                }
            }
        }
    }
}
