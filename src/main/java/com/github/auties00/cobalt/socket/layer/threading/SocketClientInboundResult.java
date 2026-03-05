package com.github.auties00.cobalt.socket.layer.threading;

import java.nio.ByteBuffer;

/**
 * A result returned by {@link SocketClientLayerContext#processInbound(int)}
 * to indicate to the selector what action to take after processing inbound
 * data through a layer context.
 *
 * <p>The selector examines the result and acts accordingly: delivering
 * complete datagrams, flushing handshake data, suspending processing while
 * delegated tasks run, or closing the connection.
 */
public sealed interface SocketClientInboundResult {
    /**
     * The layer chain completed normally.
     *
     * <p>All data was processed and any complete datagrams were delivered
     * to the listener.  The selector should continue its event loop.
     */
    record Continue() implements SocketClientInboundResult {
    }

    /**
     * A layer needs to write data to the channel.
     *
     * <p>This is returned during TLS handshakes when the engine requires
     * a {@code NEED_WRAP} operation, or when a WebSocket control frame
     * (such as a PONG response to a PING) must be sent.
     *
     * @param data the buffers to write to the channel
     */
    record NeedsWrite(ByteBuffer... data) implements SocketClientInboundResult {
    }

    /**
     * The layer does not have enough data to produce output yet.
     *
     * <p>The selector should wait for more data to arrive on the channel
     * before calling {@code processInbound} again.
     */
    record Buffering() implements SocketClientInboundResult {
    }

    /**
     * A layer has delegated CPU-intensive tasks to virtual threads.
     *
     * <p>This is returned during TLS handshakes when the engine returns
     * {@code NEED_TASK}.  The selector should suspend interest ops for
     * this key until the tasks complete and re-register interest.
     */
    record Suspended() implements SocketClientInboundResult {
    }

    /**
     * The connection should be closed.
     *
     * <p>This is returned when the channel reaches end-of-stream, the
     * TLS engine signals {@code CLOSED}, or an unrecoverable protocol
     * error is detected.
     */
    record Close() implements SocketClientInboundResult {
    }
}
