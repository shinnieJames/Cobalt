package com.github.auties00.cobalt.socket.layer.application.whatsapp;

import com.github.auties00.cobalt.socket.layer.SocketClientLayer;
import com.github.auties00.cobalt.socket.layer.SocketClientLayerListener;
import com.github.auties00.cobalt.socket.layer.application.SocketClientApplicationLayer;
import com.github.auties00.cobalt.socket.threading.SocketClientLayerContext;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;

/**
 * An application layer that provides WhatsApp int24-framed datagram
 * reassembly.
 *
 * <p>This layer wraps an inner layer (which may be a WebSocket layer
 * for Web connections, or a transport/security layer for Mobile
 * connections) and registers a {@link WhatsAppSocketClientLayerContext} for
 * inbound datagram framing.
 */
public final class WhatsAppSocketClientLayer implements SocketClientApplicationLayer<WhatsAppSocketClientLayerContext> {
    /**
     * The inner layer that provides raw I/O.
     */
    private final SocketClientLayer<?> innerLayer;

    /**
     * The layer listener for datagram delivery.
     */
    private final SocketClientLayerListener listener;

    /**
     * The WhatsApp layer context created during connect.
     */
    private WhatsAppSocketClientLayerContext layerContext;

    /**
     * Creates a WhatsApp application layer.
     *
     * @param innerLayer the inner layer (WebSocket for Web, transport for Mobile)
     * @param listener   the listener for datagram delivery and close events
     */
    public WhatsAppSocketClientLayer(SocketClientLayer<?> innerLayer, SocketClientLayerListener listener) {
        this.innerLayer = innerLayer;
        this.listener = listener;
    }

    @Override
    public void connect(InetSocketAddress address, SocketClientLayerListener listener) throws IOException {
        innerLayer.connect(address, listener);
        this.layerContext = WhatsAppSocketClientLayerContext.newAppContext(this.listener);
        innerLayer.registerLayerContext(layerContext);
        innerLayer.finishConnect();
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

    /**
     * Returns the WhatsApp layer context.
     *
     * @return the layer context, or {@code null} if not yet connected
     */
    public WhatsAppSocketClientLayerContext layerContext() {
        return layerContext;
    }
}
