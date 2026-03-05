package com.github.auties00.cobalt.socket.layer;

import com.github.auties00.cobalt.socket.layer.threading.SocketClientLayerContext;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;

/**
 * A layer in the socket client stack that provides bidirectional byte I/O.
 *
 * <p>Layers are composed bottom-to-top: transport, optional tunnel, optional
 * TLS.  Each layer delegates to an inner layer for raw I/O and may add
 * its own framing, encryption, or tunnelling on top.
 *
 * <p>In addition to the core I/O methods, layers expose transport-control
 * methods that propagate through the stack to the bottommost transport
 * layer.  These methods allow higher-level code (such as protocol
 * handshake logic) to manipulate the connection lifecycle without
 * needing direct access to the NIO channel or selector.
 */
public interface SocketClientLayer {
    /**
     * Connects this layer to the specified address.
     *
     * @param address  the remote endpoint
     * @param listener the callback for events
     * @throws IOException if the connection fails
     */
    void connect(InetSocketAddress address, SocketClientLayerListener listener) throws IOException;

    /**
     * Disconnects this layer and releases resources.
     */
    void disconnect();

    /**
     * Returns whether this layer is connected.
     *
     * @return {@code true} if connected
     */
    boolean isConnected();

    /**
     * Sends one logical binary payload represented by the provided buffers.
     *
     * <p>Implementations may enqueue these buffers for asynchronous write and
     * may transform their content in-place (for example framing, masking, or
     * encryption). Callers should treat each supplied buffer as transferred
     * ownership and avoid mutating it after this call.
     *
     * @param buffers payload buffers in send order
     */
    void sendBinary(ByteBuffer... buffers) throws IOException;

    /**
     * Reads binary bytes into {@code buffer}.
     *
     * <p>The destination buffer stays in write mode after the call; callers
     * that need to read from it should invoke {@link ByteBuffer#flip()}
     * explicitly.
     *
     * @param buffer the destination buffer, in write mode
     * @param fully  {@code true} to fill the buffer, {@code false} to return
     *               after the first successful read
     * @return bytes read, or {@code -1} on end-of-stream
     * @throws IOException if reading fails
     */
    int readBinary(ByteBuffer buffer, boolean fully) throws IOException;

    /**
     * Finishes the connection setup and transitions to asynchronous data
     * flow.
     *
     * <p>This transitions the connection from synchronous handshake mode to
     * the asynchronous post-handshake mode where the selector delivers data
     * through layer contexts.  Starts the listener executor, marks the
     * tunnel as established, and enables read interest.
     *
     * <p>Each wrapper layer delegates to its inner layer.  Only the
     * bottommost transport layer implements this concretely.
     *
     * @throws IOException if the transition fails
     */
    void finishConnect() throws IOException;

    /**
     * Finishes the connection setup, feeds leftover bytes into the
     * pipeline, and drains any buffered TLS application data.
     *
     * <p>This is used after a synchronous protocol upgrade (such as a
     * WebSocket handshake) where the HTTP response parser may have read
     * bytes beyond the end of the response headers.  Those leftover bytes
     * belong to the next protocol layer and must be fed into the selector
     * pipeline before asynchronous processing begins.
     *
     * @param leftover the leftover bytes in read mode, or {@code null}
     * @throws IOException if the transition fails
     */
    void finishConnect(ByteBuffer leftover) throws IOException;

    /**
     * Initiates a TLS handshake and blocks until it completes.
     *
     * <p>Registers the given TLS layer context with the selector pipeline
     * and drives the handshake to completion.
     *
     * @param tlsContext the TLS layer context
     * @param timeout    the handshake timeout in milliseconds
     * @throws IOException if the handshake fails or times out
     */
    void startHandshake(SocketClientLayerContext tlsContext, long timeout) throws IOException;

    /**
     * Registers a layer context in the selector pipeline.
     *
     * <p>Layer contexts are the per-connection processing state for each
     * protocol layer.  They are registered in bottom-to-top order during
     * stack construction so the selector can drive the inbound read path.
     *
     * @param key     the layer class that owns this context
     * @param context the layer context to register
     * @throws IOException if registration fails
     */
    void registerLayerContext(Class<? extends SocketClientLayer> key, SocketClientLayerContext context) throws IOException;
}
