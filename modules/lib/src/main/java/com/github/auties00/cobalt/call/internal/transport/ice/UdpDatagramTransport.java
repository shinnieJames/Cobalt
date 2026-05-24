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
 * Concrete {@link DatagramTransport} backed by an NIO
 * {@link DatagramChannel} connected to one remote peer.
 *
 * <p>Created by {@link com.github.auties00.cobalt.call.internal.transport.relay.WaRelayConnector}
 * after a successful Allocate handshake — the channel is bound to the
 * ephemeral source port used during the handshake and connected to the
 * elected WA relay endpoint, so every subsequent
 * {@link #send(byte[]) send} and inbound datagram flows over the same
 * NAT pinhole.
 *
 * <p>A dedicated virtual thread services the channel's receive side
 * and dispatches synchronously to the registered
 * {@link InboundListener}. The receive thread shuts down when
 * {@link #close()} is called or when the channel closes underneath it.
 */
public final class UdpDatagramTransport implements DatagramTransport {
    /**
     * Receive buffer size — large enough for a maximal SCTP-over-DTLS
     * record (the WebRTC maxMessageSize is 16 KB, plus DTLS and SCTP
     * overhead).
     */
    private static final int RECV_BUFFER_BYTES = 64 * 1024;

    /**
     * The underlying channel.
     */
    private final DatagramChannel channel;

    /**
     * The local-side bound address.
     */
    private final InetSocketAddress local;

    /**
     * The remote-side connected address.
     */
    private final InetSocketAddress remote;

    /**
     * The receive-side virtual thread.
     */
    private final Thread receiver;

    /**
     * Application listener for inbound datagrams.
     */
    private final AtomicReference<InboundListener> listener = new AtomicReference<>();

    /**
     * Set when {@link #close()} has been invoked; the receiver loop
     * uses it as the exit signal.
     */
    private final AtomicBoolean closed = new AtomicBoolean(false);

    /**
     * Constructs and starts the transport on a fresh
     * {@link DatagramChannel} bound to an ephemeral local port and
     * connected to {@code remoteAddress}.
     *
     * @param remoteAddress the relay endpoint
     * @throws WhatsAppCallException.Ice if the channel cannot be opened, bound,
     *                                   or connected
     * @throws NullPointerException      if {@code remoteAddress} is null
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
        try { channel.close(); } catch (IOException _) { /* swallow */ }
        receiver.interrupt();
    }

    /**
     * Body of the receive thread. Blocks on {@link DatagramChannel#read}
     * until the channel closes or the thread is interrupted, dispatching
     * each datagram synchronously to the current
     * {@link InboundListener}.
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
                try { l.onDatagram(bytes); } catch (RuntimeException _) { /* keep looping */ }
            }
        }
    }
}
