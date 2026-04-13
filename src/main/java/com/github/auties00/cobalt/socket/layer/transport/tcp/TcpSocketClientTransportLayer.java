package com.github.auties00.cobalt.socket.layer.transport.tcp;

import com.github.auties00.cobalt.socket.layer.SocketClientLayer;
import com.github.auties00.cobalt.socket.layer.SocketClientLayerListener;
import com.github.auties00.cobalt.socket.layer.threading.SocketClientContext;
import com.github.auties00.cobalt.socket.layer.threading.SocketClientLayerContext;
import com.github.auties00.cobalt.socket.layer.threading.SocketClientSelector;
import com.github.auties00.cobalt.socket.layer.transport.SocketClientTransportLayer;
import com.github.auties00.cobalt.socket.layer.transport.SocketClientTransportLayerContext;

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
 * All other layers delegate to this one for raw I/O and transport-control
 * operations.
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

    /**
     * The per-connection context attached to the selection key.
     */
    private SocketClientContext context;

    @Override
    public void connect(InetSocketAddress address, SocketClientLayerListener listener) throws IOException {
        this.transportContext = new SocketClientTransportLayerContext();
        this.context = new SocketClientContext(transportContext);
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
        transportContext.setConnected(true);
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
    public void sendBinary(ByteBuffer... buffers) throws IOException {
        if (!SocketClientSelector.INSTANCE.addWrite(channel, buffers)) {
            throw new IOException("Failed to enqueue write: channel not registered or closed");
        }
    }

    @Override
    public int readBinary(ByteBuffer buffer, boolean fully) throws IOException {
        var read = new SocketClientTransportLayerContext.PendingRead(buffer, fully);
        if (!SocketClientSelector.INSTANCE.addRead(channel, read)) {
            throw new IOException("Failed to post read request");
        }
        synchronized (read.lock) {
            while (transportContext.isConnected() && (read.length == -1 || (fully && read.length >= 0 && read.buffer.hasRemaining()))) {
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

    @Override
    public void finishConnect() throws IOException {
        if (!SocketClientSelector.INSTANCE.finishConnect(channel)) {
            throw new IOException("Failed to finish connect: channel not registered or closed");
        }
    }

    @Override
    public void finishConnect(ByteBuffer leftover) throws IOException {
        if (!SocketClientSelector.INSTANCE.finishConnect(channel, leftover)) {
            throw new IOException("Failed to finish connect: channel not registered or closed");
        }
    }

    @Override
    public void startHandshake(SocketClientLayerContext tlsContext, long timeout) throws IOException {
        SocketClientSelector.INSTANCE.startTlsHandshake(channel, tlsContext, timeout);
    }

    @Override
    public void registerLayerContext(Class<? extends SocketClientLayer> key, SocketClientLayerContext layerContext) throws IOException {
        if (context == null) {
            throw new IOException("Transport layer not connected");
        }
        context.createLayerContext(key, layerContext);
    }
}
