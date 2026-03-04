package com.github.auties00.cobalt.socket.threading;

import java.io.IOException;
import java.nio.ByteBuffer;

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
 * <p>Layer contexts are inserted into a {@link SocketClientContext} in
 * bottom-to-top order during stack construction.  The selector reads
 * raw bytes from the channel into the bottommost context's
 * {@link #inboundTarget()}, then calls {@link #processInbound(int)} to
 * propagate data through the chain.
 *
 * <p>Outbound encoding (application framing, WebSocket framing, Noise
 * encryption) happens at {@code sendBinary()} call time on the caller's
 * virtual thread, not on the selector thread.  Only TLS wrapping occurs
 * on the selector thread during write processing.
 */
public interface SocketClientLayerContext {
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
     * Called when the connection is being torn down.
     *
     * <p>Implementations should release resources, notify waiting threads,
     * and clean up protocol state.  This method is called by the selector
     * during unregistration, before the channel is closed.
     */
    void onDisconnect();
}
