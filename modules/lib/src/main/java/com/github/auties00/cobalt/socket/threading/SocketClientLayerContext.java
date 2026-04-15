package com.github.auties00.cobalt.socket.threading;

import com.github.auties00.cobalt.socket.layer.application.SocketClientApplicationLayerContext;
import com.github.auties00.cobalt.socket.layer.security.SocketClientSecurityLayerContext;
import com.github.auties00.cobalt.socket.layer.transport.SocketClientTransportLayerContext;
import com.github.auties00.cobalt.socket.layer.tunnel.SocketClientTunnelLayerContext;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

/**
 * A bidirectional byte processor representing the per-connection state and
 * processing logic for a single layer in the socket layer stack.
 *
 * <p>Each layer context owns its own buffers and protocol state (for example,
 * an {@code SSLEngine} for TLS, or a frame decoder for WebSocket).  The
 * central {@link SocketClientSelector} drives the inbound read path by
 * calling {@link #processInbound(int)} on the bottommost layer context;
 * that layer decodes its data and internally delegates to the next layer
 * up in the chain.
 *
 * <p>Outbound encoding (application framing, WebSocket framing, Noise
 * encryption) happens at {@code sendBinary()} call time on the caller's
 * virtual thread, not on the selector thread.  Only TLS wrapping occurs
 * on the selector thread during write processing.
 */
public sealed interface SocketClientLayerContext
        permits SocketClientApplicationLayerContext, SocketClientSecurityLayerContext, SocketClientTransportLayerContext, SocketClientTunnelLayerContext {
    /**
     * Returns the buffer into which inbound bytes should be placed.
     *
     * <p>For the bottommost layer, this is the buffer that the selector
     * passes to {@code channel.read()}.  For intermediate layers like TLS,
     * this returns the encrypted-data buffer ({@code netInBuffer}); the
     * TLS layer then unwraps directly into the next layer's
     * {@code inboundTarget()} for zero-copy delivery.
     *
     * <p>The returned buffer must be in write mode (ready for
     * {@code put()} or {@code channel.read()}).
     *
     * @return the buffer to read or unwrap into, never {@code null}
     */
    ByteBuffer inboundTarget();

    /**
     * Processes {@code bytesRead} bytes that were placed into this layer's
     * {@link #inboundTarget()}.
     *
     * <p>The layer decodes the data according to its protocol, and when
     * complete data is available, internally calls the next layer's
     * {@code processInbound()} to propagate decoded bytes up the chain.
     *
     * <p>The returned {@link SocketClientInboundResult} tells the selector what to do
     * next: continue processing, wait for more data, flush handshake
     * bytes, or close the connection.
     *
     * @param bytesRead the number of bytes placed into the inbound target,
     *                  or {@code -1} if the channel reached end-of-stream
     * @return the result of processing, never {@code null}
     * @throws IOException if an I/O error occurs during processing
     */
    SocketClientInboundResult processInbound(int bytesRead) throws IOException;

    /**
     * Feeds decoded bytes from an upstream layer into this layer's
     * processing pipeline.
     *
     * <p>This method is used by intermediate layers (such as TLS) that
     * decode data into their own buffers and then need to push the decoded
     * bytes into the next layer.  It performs a bounded copy from the
     * source into the current {@link #inboundTarget()} and advances the
     * layer's state machine.
     *
     * <p>The default implementation copies bytes from the source into
     * {@link #inboundTarget()} in chunks and calls
     * {@link #processInbound(int)} after each chunk.  Layers that can
     * process source data more efficiently (for example by avoiding the
     * intermediate copy) should override this method.
     *
     * @param source the buffer containing decoded bytes, in read mode
     * @return the result of processing, never {@code null}
     * @throws IOException if an I/O error occurs during processing
     */
    default SocketClientInboundResult feedFromSource(ByteBuffer source) throws IOException {
        while (source.hasRemaining()) {
            var target = inboundTarget();
            var count = Math.min(source.remaining(), target.remaining());
            var savedLimit = source.limit();
            source.limit(source.position() + count);
            target.put(source);
            source.limit(savedLimit);

            var result = processInbound(count);
            if (!(result instanceof SocketClientInboundResult.Continue)
                    && !(result instanceof SocketClientInboundResult.Buffering)) {
                return result;
            }
        }
        return new SocketClientInboundResult.Continue();
    }

    /**
     * Sets the next layer context in the inbound processing chain.
     *
     * @param next the next layer context
     */
    default void setNextLayer(SocketClientLayerContext next) {
    }

    /**
     * Called when the connection is being torn down.
     *
     * <p>Implementations should release resources, notify waiting threads,
     * and clean up protocol state.  This method is called by the selector
     * during unregistration, before the channel is closed.
     */
    void onDisconnect();
    /**
     * Returns whether this layer context is currently handshaking.
     *
     * <p>The default implementation returns {@code false}, meaning the
     * layer does not participate in handshake processing.
     *
     * @return {@code true} if handshaking
     */
    default boolean isHandshaking() {
        return false;
    }

    /**
     * Returns whether this layer has delegated tasks pending.
     *
     * <p>The default implementation returns {@code false}.
     *
     * @return {@code true} if tasks are pending
     */
    default boolean isTasksPending() {
        return false;
    }

    /**
     * Drives the handshake state machine with direct channel I/O.
     *
     * <p>The default implementation returns {@link SocketClientInboundResult.Continue},
     * indicating no handshake processing is needed.
     *
     * @param channel the socket channel for direct I/O
     * @return the result indicating what the selector should do next
     * @throws IOException if an I/O error occurs during handshake
     */
    default SocketClientInboundResult driveHandshake(SocketChannel channel) throws IOException {
        return new SocketClientInboundResult.Continue();
    }

    /**
     * Runs delegated tasks and invokes the completion callback.
     *
     * <p>The default implementation immediately invokes the callback.
     *
     * @param onComplete callback to run after tasks finish
     */
    default void runDelegatedTasks(Runnable onComplete) {
        onComplete.run();
    }

    /**
     * Begins the handshake for this layer.
     *
     * <p>The default implementation is a no-op.
     *
     * @throws IOException if the handshake cannot be initiated
     */
    default void beginHandshake() throws IOException {
    }

    /**
     * Returns whether the handshake has completed successfully.
     *
     * <p>The default implementation returns {@code true}, meaning no
     * handshake is required.
     *
     * @return {@code true} if the handshake completed
     */
    default boolean isHandshakeComplete() {
        return true;
    }

    /**
     * Returns the lock object used to synchronize handshake completion.
     *
     * <p>The default implementation returns {@code null}, meaning this
     * layer does not support blocking handshake synchronization.
     *
     * @return the handshake lock object, or {@code null}
     */
    default Object handshakeLock() {
        return null;
    }
    /**
     * Processes outbound data through this layer and writes to the channel.
     *
     * <p>The default implementation writes the buffers directly to the
     * channel without any transformation.
     *
     * @param channel the socket channel to write to
     * @param buffers the data buffers to write
     * @param offset  the offset into the buffers array
     * @param count   the number of buffers to process
     * @return {@code true} if all data was written successfully
     * @throws IOException if an I/O error occurs during writing
     */
    default boolean processOutbound(SocketChannel channel, ByteBuffer[] buffers, int offset, int count) throws IOException {
        channel.write(buffers, offset, count);
        return true;
    }

    /**
     * Returns whether this layer has buffered output data pending.
     *
     * <p>The default implementation returns {@code false}.
     *
     * @return {@code true} if pending output exists
     */
    default boolean hasPendingOutput() {
        return false;
    }
    /**
     * Drains any buffered decoded data into the next layer.
     *
     * <p>The default implementation returns {@code true}, indicating
     * there is nothing to drain.
     *
     * @return {@code true} if draining succeeded or there was nothing
     *         to drain, {@code false} if the next layer signalled close
     * @throws IOException if layer processing fails
     */
    default boolean drainToNextLayer() throws IOException {
        return true;
    }

}
