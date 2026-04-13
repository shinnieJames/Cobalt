package com.github.auties00.cobalt.socket.layer.security.tls;

import com.github.auties00.cobalt.socket.layer.SocketClientLayer;
import com.github.auties00.cobalt.socket.layer.SocketClientLayerListener;
import com.github.auties00.cobalt.socket.layer.security.SocketClientTransportSecurityLayer;
import com.github.auties00.cobalt.socket.layer.security.SocketClientTunnelSecurityLayer;
import com.github.auties00.cobalt.socket.layer.threading.SocketClientLayerContext;

import javax.net.ssl.SSLContext;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.security.NoSuchAlgorithmException;

/**
 * A security layer that provides TLS encryption over an existing
 * connection.
 *
 * <p>This layer wraps an inner layer (typically a transport or tunnel
 * layer) and adds TLS encryption.  It can be used for both transport-level
 * TLS (secure WebSocket) and tunnel-level TLS (HTTPS proxy).
 *
 * <p>The TLS handshake is driven by the selector thread through the
 * {@link TlsLayerContext}.  The {@code startHandshake()} method blocks
 * the calling virtual thread until the handshake completes.
 *
 * <p>No direct references to {@code SocketChannel} or
 * {@code SocketClientSelector} are needed — all transport-control
 * operations delegate through the inner layer stack.
 */
public final class TlsSocketClientSecurityLayer implements SocketClientTunnelSecurityLayer, SocketClientTransportSecurityLayer {
    /**
     * A reasonable amount of time in ms before the handshake times out.
     */
    private static final int HANDSHAKE_TIMEOUT = 30_000;

    /**
     * The inner layer that provides raw I/O.
     */
    private final SocketClientLayer innerLayer;

    /**
     * The peer address captured during {@link #connect(InetSocketAddress,
     * SocketClientLayerListener)} for hostname verification.
     */
    private InetSocketAddress peerAddress;

    /**
     * Creates a TLS security layer wrapping the given inner layer.
     *
     * @param innerLayer the layer below TLS
     */
    public TlsSocketClientSecurityLayer(SocketClientLayer innerLayer) {
        this.innerLayer = innerLayer;
    }

    @Override
    public void startHandshake() throws IOException {
        try {
            var sslContext = SSLContext.getDefault();
            var engine = sslContext.createSSLEngine(peerAddress.getHostString(), peerAddress.getPort());
            engine.setUseClientMode(true);
            var params = engine.getSSLParameters();
            params.setEndpointIdentificationAlgorithm("HTTPS");
            engine.setSSLParameters(params);
            var tlsLayerContext = new TlsLayerContext(null);
            tlsLayerContext.initSsl(engine);
            innerLayer.registerLayerContext(TlsSocketClientSecurityLayer.class, tlsLayerContext);
            innerLayer.startHandshake(tlsLayerContext, HANDSHAKE_TIMEOUT);
        } catch (NoSuchAlgorithmException e) {
            throw new IOException("Failed to create SSL context", e);
        }
    }

    @Override
    public void connect(InetSocketAddress address, SocketClientLayerListener listener) throws IOException {
        this.peerAddress = address;
        innerLayer.connect(address, listener);
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
    public void registerLayerContext(Class<? extends SocketClientLayer> key, SocketClientLayerContext context) throws IOException {
        innerLayer.registerLayerContext(key, context);
    }
}
