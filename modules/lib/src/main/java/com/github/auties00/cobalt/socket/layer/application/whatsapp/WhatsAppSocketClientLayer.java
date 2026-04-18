package com.github.auties00.cobalt.socket.layer.application.whatsapp;

import com.github.auties00.cobalt.socket.layer.SocketClientLayer;
import com.github.auties00.cobalt.socket.layer.SocketClientLayerListener;
import com.github.auties00.cobalt.socket.layer.application.SocketClientApplicationLayer;
import com.github.auties00.cobalt.socket.threading.SocketClientLayerContext;
import com.github.auties00.cobalt.socket.threading.SocketClientPendingRead;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.Objects;

/**
 * A WhatsApp application layer that owns a
 * {@link WhatsAppSocketClientLayerContext} for int24-framed datagram
 * reassembly and exposes the handshake-plumbing API used by the Noise
 * driver.
 *
 * <p>This is the topmost layer in the stack.  Its context is created and
 * registered lazily during {@link #connect(InetSocketAddress, SocketClientLayerListener)}
 * with the listener passed by the caller — typically the decrypting
 * listener produced by the Noise driver.
 */
public final class WhatsAppSocketClientLayer implements SocketClientApplicationLayer<WhatsAppSocketClientLayerContext> {
    /**
     * The inner layer that provides raw I/O.
     */
    private final SocketClientLayer<?> innerLayer;

    /**
     * The layer context, created at {@link #connect} time.  Read by the
     * Noise driver for handshake plumbing.
     */
    private WhatsAppSocketClientLayerContext layerContext;

    /**
     * Creates a WhatsApp application layer.
     *
     * @param innerLayer the inner layer (WebSocket for Web, security/transport for Mobile)
     */
    public WhatsAppSocketClientLayer(SocketClientLayer<?> innerLayer) {
        this.innerLayer = Objects.requireNonNull(innerLayer, "innerLayer cannot be null");
    }

    /**
     * Connects the inner layer and registers a fresh layer context with
     * the given listener.  Does <em>not</em> call {@link #finishConnect()}
     * — the Noise driver sequences that explicitly because Web and Mobile
     * need different orderings.
     *
     * @param address  the remote endpoint
     * @param listener the listener for inbound datagrams and close events
     * @throws IOException if the connection fails
     */
    @Override
    public void connect(InetSocketAddress address, SocketClientLayerListener listener) throws IOException {
        innerLayer.connect(address, listener);
        this.layerContext = WhatsAppSocketClientLayerContext.newAppContext(listener);
        innerLayer.registerLayerContext(layerContext);
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
     * Posts a pending blocking read request to the layer context.
     *
     * <p>Used by the Noise driver during its handshake: the driver blocks
     * on this read until the selector thread fulfils it by delivering
     * handshake bytes arriving through the chain.
     *
     * @param read the pending read request
     * @return {@code true} if the read was accepted
     * @throws IllegalStateException if called before {@link #connect}
     */
    public boolean setPendingRead(SocketClientPendingRead read) {
        requireContext();
        return layerContext.setPendingRead(read);
    }

    /**
     * Transitions the layer context out of handshake mode.  After this
     * call, inbound bytes are reassembled as int24-framed datagrams and
     * dispatched to the listener instead of filling blocking reads.
     *
     * @throws IllegalStateException if called before {@link #connect}
     */
    public void markHandshakeComplete() {
        requireContext();
        layerContext.markHandshakeComplete();
    }

    /**
     * Starts the single-threaded virtual executor that serialises
     * listener callbacks.
     *
     * @throws IllegalStateException if called before {@link #connect}
     */
    public void startListenerExecutor() {
        requireContext();
        layerContext.startListenerExecutor();
    }

    private void requireContext() {
        if (layerContext == null) {
            throw new IllegalStateException("WhatsAppSocketClientLayer.connect must be called first");
        }
    }
}
