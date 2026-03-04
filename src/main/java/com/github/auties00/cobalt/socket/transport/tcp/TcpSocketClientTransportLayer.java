package com.github.auties00.cobalt.socket.transport.tcp;

import com.github.auties00.cobalt.socket.SocketClientLayerListener;
import com.github.auties00.cobalt.socket.threading.SocketClientContext;
import com.github.auties00.cobalt.socket.threading.SocketClientSelector;
import com.github.auties00.cobalt.socket.transport.SocketClientTransportLayer;
import com.github.auties00.cobalt.socket.transport.SocketClientTransportLayerContext;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;

/**
 * A transport layer implementation that manages a non-blocking TCP
 * {@link SocketChannel} registered with the central
 * {@link SocketClientSelector}.
 *
 * <p>This is the only layer that directly interacts with NIO types.
 * All other layers delegate to this one for raw I/O.
 */
public final class TcpSocketClientTransportLayer implements SocketClientTransportLayer {
    /**
     * The underlying NIO socket channel.
     */
    private SocketChannel channel;

    /**
     * The per-connection transport context.
     */
    private SocketClientTransportLayerContext transportContext;

    @Override
    public void connect(InetSocketAddress address, SocketClientLayerListener listener) throws IOException {
        this.transportContext = new SocketClientTransportLayerContext();
        var context = new SocketClientContext(transportContext);
        this.channel = SocketChannel.open();
        channel.configureBlocking(false);
        channel.connect(address);
        SocketClientSelector.INSTANCE.register(channel, SelectionKey.OP_CONNECT, context);
        synchronized (transportContext.connectionLock) {
            while (!channel.isConnected() && channel.isOpen()) {
                try {
                    transportContext.connectionLock.wait(30_000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Connect interrupted", e);
                }
            }
        }
        if (!channel.isConnected()) {
            throw new IOException("Connection failed");
        }
        transportContext.connected.set(true);
    }

    @Override
    public void disconnect() {
        SocketClientSelector.INSTANCE.unregister(channel);
    }

    @Override
    public boolean isConnected() {
        return SocketClientSelector.INSTANCE.isConnected(channel);
    }

    @Override
    public void sendBinary(ByteBuffer... buffers) {
        SocketClientSelector.INSTANCE.addWrite(channel, buffers);
    }

    @Override
    public int readBinary(ByteBuffer buffer, boolean fully) throws IOException {
        var read = new SocketClientTransportLayerContext.PendingRead(buffer, fully);
        if (!SocketClientSelector.INSTANCE.addRead(channel, read)) {
            throw new IOException("Failed to post read request");
        }
        synchronized (read.lock) {
            while (shouldWaitForRead(read, fully, transportContext.connected.get())) {
                try {
                    read.lock.wait(30_000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Read interrupted", e);
                }
            }
        }
        return read.length;
    }

    static boolean shouldWaitForRead(
            SocketClientTransportLayerContext.PendingRead read,
            boolean fully,
            boolean connected
    ) {
        if (!connected) {
            return false;
        }
        return read.length == -1 || (fully && read.length >= 0 && read.buffer.hasRemaining());
    }
}
