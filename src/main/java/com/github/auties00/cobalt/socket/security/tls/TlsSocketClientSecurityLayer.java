package com.github.auties00.cobalt.socket.security.tls;

import com.github.auties00.cobalt.socket.SocketClientLayer;
import com.github.auties00.cobalt.socket.SocketClientLayerListener;
import com.github.auties00.cobalt.socket.security.SocketClientTransportSecurityLayer;
import com.github.auties00.cobalt.socket.security.SocketClientTunnelSecurityLayer;
import com.github.auties00.cobalt.socket.threading.SocketClientContext;
import com.github.auties00.cobalt.socket.threading.SocketClientSelector;

import javax.net.ssl.SSLContext;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
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
 */
public final class TlsSocketClientSecurityLayer implements SocketClientTunnelSecurityLayer, SocketClientTransportSecurityLayer {
    /**
     * The inner layer that provides raw I/O.
     */
    private final SocketClientLayer innerLayer;

    /**
     * The channel obtained from the transport layer.
     */
    private SocketChannel channel;

    /**
     * The connection context for registering the TLS layer context.
     */
    private SocketClientContext context;

    /**
     * The TLS layer context that holds the SSLEngine and buffers.
     */
    private TlsLayerContext tlsLayerContext;

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

    /**
     * Initializes the TLS layer context with the given channel, context,
     * and next layer context.
     *
     * <p>Called during layer stack construction.
     *
     * @param channel          the socket channel
     * @param context          the connection context
     * @param tlsLayerContext  the TLS layer context
     */
    public void init(SocketChannel channel, SocketClientContext context, TlsLayerContext tlsLayerContext) {
        this.channel = channel;
        this.context = context;
        this.tlsLayerContext = tlsLayerContext;
    }

    @Override
    public void startHandshake() throws IOException {
        if (channel == null || context == null || tlsLayerContext == null) {
            throw new IOException("TLS layer not initialized");
        }

        try {
            var sslContext = SSLContext.getDefault();
            var engine = sslContext.createSSLEngine(peerAddress.getHostString(), peerAddress.getPort());
            engine.setUseClientMode(true);
            var params = engine.getSSLParameters();
            params.setEndpointIdentificationAlgorithm("HTTPS");
            engine.setSSLParameters(params);
            tlsLayerContext.initSsl(engine);
            context.createLayerContext(TlsSocketClientSecurityLayer.class, tlsLayerContext);
            SocketClientSelector.INSTANCE.startTlsHandshake(channel, 30_000);
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
}
