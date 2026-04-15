package com.github.auties00.cobalt.socket.layer.transport.impl.tcp;

import com.github.auties00.cobalt.socket.threading.SocketClientInboundResult;
import com.github.auties00.cobalt.socket.layer.transport.SocketClientTransportLayerContext;
import com.github.auties00.cobalt.socket.threading.SocketClientLayerContext;
import com.github.auties00.cobalt.socket.threading.SocketClientPendingWrites;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

/**
 * TCP implementation of {@link SocketClientTransportLayerContext}.
 *
 * <p>This context holds the connection lifecycle state, the outbound
 * write queue, and implements {@link SocketClientLayerContext} so the
 * selector reads raw channel bytes into its
 * {@link #inboundTarget() inbound buffer} and calls
 * {@link #processInbound(int)} to propagate data up through the layer
 * chain.
 */
final class TcpSocketClientTransportLayerContext implements SocketClientTransportLayerContext {
    private static final int WRITES_CHUNK_CAPACITY = 64;
    private static final VarHandle CONNECTED;

    static {
        try {
            var lookup = MethodHandles.lookup();
            CONNECTED = lookup.findVarHandle(TcpSocketClientTransportLayerContext.class, "connected", boolean.class);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    /**
     * The buffer into which raw bytes are read from the channel.
     *
     * <p>This is the very first buffer in the inbound processing chain.
     * The selector reads channel data into this buffer and then
     * {@link #processInbound(int)} propagates the data to the next layer.
     */
    private final ByteBuffer inboundBuffer = ByteBuffer.allocateDirect(16384);

    /**
     * The next layer context in the inbound processing chain.
     *
     * <p>Set by {@link #setNextLayer(SocketClientLayerContext)} when the
     * layer chain is rebuilt.  May be {@code null} during early
     * connection setup before any processing layers are registered.
     */
    private volatile SocketClientLayerContext nextLayer;

    /**
     * Whether the underlying channel is connected and registered with the
     * selector.
     *
     * <p>Set to {@code true} by the connecting thread after the handshake
     * completes; set to {@code false} by the selector thread on disconnect
     * or I/O failure.  Accessed via the {@link #CONNECTED} VarHandle.
     */
    @SuppressWarnings("unused")
    private volatile boolean connected;

    /**
     * Monitor used to block the connecting thread until the selector
     * completes the non-blocking connect operation.
     */
    private final Object connectionLock;

    /**
     * Lock-free MPSC queue of outbound buffers waiting to be written to
     * the channel.
     */
    private final SocketClientPendingWrites pendingWrites;

    /**
     * Creates a transport layer context for a new connection.
     */
    TcpSocketClientTransportLayerContext() {
        this.connectionLock = new Object();
        this.pendingWrites = new SocketClientPendingWrites(WRITES_CHUNK_CAPACITY);
    }

    @Override
    public boolean isConnected() {
        return (boolean) CONNECTED.getVolatile(this);
    }

    @Override
    public void setConnected(boolean value) {
        CONNECTED.setVolatile(this, value);
    }

    @Override
    public boolean compareAndSetConnected(boolean expected, boolean newValue) {
        return CONNECTED.compareAndSet(this, expected, newValue);
    }

    @Override
    public Object connectionLock() {
        return connectionLock;
    }

    @Override
    public SocketClientPendingWrites pendingWrites() {
        return pendingWrites;
    }

    /**
     * Returns the buffer into which the selector reads raw bytes from the
     * channel.
     *
     * @return the inbound buffer, never {@code null}
     */
    @Override
    public ByteBuffer inboundTarget() {
        return inboundBuffer;
    }

    /**
     * Processes raw bytes that were read from the channel into the
     * {@link #inboundTarget() inbound buffer}.
     *
     * <p>If the channel reached end-of-stream ({@code bytesRead == -1}),
     * a {@link SocketClientInboundResult.Close} is returned.  Otherwise
     * the bytes are flipped and fed into the next layer via
     * {@link SocketClientLayerContext#feedFromSource(ByteBuffer)}.
     *
     * @param bytesRead the number of bytes read, or {@code -1} on EOF
     * @return the result of processing
     * @throws IOException if an I/O error occurs during processing
     */
    @Override
    public SocketClientInboundResult processInbound(int bytesRead) throws IOException {
        if (bytesRead == -1) {
            return new SocketClientInboundResult.Close();
        }
        if (nextLayer == null) {
            return new SocketClientInboundResult.Buffering();
        }
        inboundBuffer.flip();
        var result = nextLayer.feedFromSource(inboundBuffer);
        inboundBuffer.compact();
        return result;
    }

    /**
     * Sets the next layer context in the inbound processing chain.
     *
     * @param next the next layer context
     */
    @Override
    public void setNextLayer(SocketClientLayerContext next) {
        this.nextLayer = next;
    }

    /**
     * Called when the connection is being torn down.
     *
     * <p>Propagates the disconnect notification to the next layer in the
     * chain, if one is set.
     */
    @Override
    public void onDisconnect() {
        if (nextLayer != null) {
            nextLayer.onDisconnect();
        }
    }

    /**
     * Processes outbound data through this layer and writes to the channel.
     *
     * <p>If a next layer is set, delegates to it for processing (e.g. TLS
     * wrapping).  Otherwise writes the buffers directly to the channel.
     *
     * @param channel the socket channel to write to
     * @param buffers the data buffers to write
     * @param offset  the offset into the buffers array
     * @param count   the number of buffers to process
     * @return {@code true} if all data was written successfully
     * @throws IOException if an I/O error occurs during writing
     */
    @Override
    public boolean processOutbound(SocketChannel channel, ByteBuffer[] buffers, int offset, int count) throws IOException {
        if (nextLayer != null) {
            return nextLayer.processOutbound(channel, buffers, offset, count);
        }
        channel.write(buffers, offset, count);
        return true;
    }
}
