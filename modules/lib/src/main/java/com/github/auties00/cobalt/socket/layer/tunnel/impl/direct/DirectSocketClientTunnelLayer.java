package com.github.auties00.cobalt.socket.layer.tunnel.impl.direct;

import com.github.auties00.cobalt.socket.layer.SocketClientLayer;
import com.github.auties00.cobalt.socket.layer.SocketClientLayerListener;
import com.github.auties00.cobalt.socket.layer.tunnel.SocketClientTunnelLayer;
import com.github.auties00.cobalt.socket.layer.tunnel.impl.SocketTunnelLayerContextImpl;
import com.github.auties00.cobalt.socket.threading.SocketClientLayerContext;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;

/**
 * A direct (no-proxy) tunnel layer that provides the
 * {@link SocketClientTunnelLayerContext} required by all connections for blocking
 * reads during the handshake phase.
 *
 * <p>This layer is a pure passthrough: all I/O methods delegate to
 * the inner layer.  Its only purpose is to register a
 * {@link SocketClientTunnelLayerContext} during {@link #connect(InetSocketAddress,
 * SocketClientLayerListener)} so that {@code readBinary()} calls work
 * before the connection transitions to asynchronous mode.
 */
public final class DirectSocketClientTunnelLayer implements SocketClientTunnelLayer {
    /**
     * The inner layer that provides raw I/O.
     */
    private final SocketClientLayer<?> innerLayer;

    /**
     * Creates a direct tunnel layer wrapping the given inner layer.
     *
     * @param innerLayer the layer below (typically a transport layer)
     */
    public DirectSocketClientTunnelLayer(SocketClientLayer<?> innerLayer) {
        this.innerLayer = innerLayer;
    }

    /**
     * Connects the inner layer and registers a {@link SocketClientTunnelLayerContext}
     * in pre-tunnel mode for blocking reads during handshakes.
     *
     * @param address  the remote endpoint
     * @param listener the callback for events
     * @throws IOException if the connection fails
     */
    @Override
    public void connect(InetSocketAddress address, SocketClientLayerListener listener) throws IOException {
        innerLayer.connect(address, listener);
        innerLayer.registerLayerContext(new SocketTunnelLayerContextImpl());
    }

    @Override
    public void disconnect() {
        innerLayer.disconnect();
    }

    @Override
    public boolean isConnected() {
        return innerLayer.isConnected();
    }

    @Override
    public void sendBinary(ByteBuffer... buffers) throws IOException {
        innerLayer.sendBinary(buffers);
    }

    @Override
    public int readBinary(ByteBuffer buffer, boolean fully) throws IOException {
        return innerLayer.readBinary(buffer, fully);
    }

    @Override
    public void finishConnect() throws IOException {
        innerLayer.finishConnect();
    }

    @Override
    public void finishConnect(ByteBuffer leftover) throws IOException {
        innerLayer.finishConnect(leftover);
    }

    @Override
    public void startHandshake(SocketClientLayerContext tlsContext, long timeout) throws IOException {
        innerLayer.startHandshake(tlsContext, timeout);
    }

    @Override
    public void registerLayerContext(SocketClientLayerContext context) throws IOException {
        innerLayer.registerLayerContext(context);
    }
}
