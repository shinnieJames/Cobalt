package com.github.auties00.cobalt.socket.layer.security.impl;

import com.github.auties00.cobalt.socket.layer.SocketClientLayer;
import com.github.auties00.cobalt.socket.layer.SocketClientLayerListener;
import com.github.auties00.cobalt.socket.threading.SocketClientLayerContext;
import com.github.auties00.cobalt.socket.WhatsAppSslEngineFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;

/**
 * A sealed security layer that provides TLS encryption over an existing
 * connection.
 *
 * <p>Two concrete subclasses distinguish transport-level TLS (secure
 * WebSocket, end-to-end to the target) from tunnel-level TLS (HTTPS
 * proxy, client-to-proxy encryption).  The subclass determines which
 * {@link TlsSocketClientLayerContext} variant is created.
 */
public abstract class TlsSocketClientSecurityLayer {
    private static final int HANDSHAKE_TIMEOUT = 30_000;

    private final SocketClientLayer<?> innerLayer;
    private final WhatsAppSslEngineFactory engineFactory;
    private InetSocketAddress peerAddress;

    protected TlsSocketClientSecurityLayer(SocketClientLayer<?> innerLayer, WhatsAppSslEngineFactory engineFactory) {
        this.innerLayer = innerLayer;
        this.engineFactory = engineFactory;
    }

    /**
     * Creates the appropriate TLS layer context subclass.
     *
     * @return a new TLS layer context
     */
    protected abstract TlsSocketClientLayerContext createLayerContext();

    public void startHandshake() throws IOException {
        var ctx = createLayerContext();
        ctx.initSsl(engineFactory.createSSLEngine(peerAddress));
        innerLayer.registerLayerContext(ctx);
        innerLayer.startHandshake(ctx, HANDSHAKE_TIMEOUT);
    }

    public void connect(InetSocketAddress address, SocketClientLayerListener listener) throws IOException {
        this.peerAddress = address;
        innerLayer.connect(address, listener);
        startHandshake();
    }

    public void disconnect() {
        innerLayer.disconnect();
    }

    public boolean isConnected() {
        return innerLayer.isConnected();
    }

    public void sendBinary(ByteBuffer... buffers) throws IOException {
        innerLayer.sendBinary(buffers);
    }

    public int readBinary(ByteBuffer buffer, boolean fully) throws IOException {
        return innerLayer.readBinary(buffer, fully);
    }

    public void finishConnect() throws IOException {
        innerLayer.finishConnect();
    }

    public void finishConnect(ByteBuffer leftover) throws IOException {
        innerLayer.finishConnect(leftover);
    }

    public void startHandshake(SocketClientLayerContext tlsContext, long timeout) throws IOException {
        innerLayer.startHandshake(tlsContext, timeout);
    }

    public void registerLayerContext(SocketClientLayerContext context) throws IOException {
        innerLayer.registerLayerContext(context);
    }
}
