package com.github.auties00.cobalt.socket.context;

import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

/**
 * A pending read request submitted to the selector during the pre-tunnel
 * phase of a proxied connection.
 *
 * <p> The requesting thread constructs a {@code SocketPendingRead}, posts it
 * to the {@link AbstractSocketClientContext#pendingBinaryRead} field, and blocks on
 * {@link #lock}.  The selector thread reads bytes from the channel into
 * {@link #buffer}, updates {@link #length}, and notifies the lock when the
 * request is satisfied.
 *
 * <p> A request is considered satisfied when either:
 * <ul>
 *   <li>{@link #fullRead} is {@code false} and at least one
 *       {@link SocketChannel#read(ByteBuffer) read}
 *       operation has completed, or</li>
 *   <li>{@link #fullRead} is {@code true} and the buffer has no
 *       remaining capacity.</li>
 * </ul>
 *
 * <p> If the channel reaches end-of-stream before the request is satisfied,
 * the selector sets {@link #length} to {@code -1} and notifies the lock.
 *
 * <p> <em>Thread safety:</em> instances are created by the requesting thread
 * and then handed off to the selector thread through a volatile write to
 * {@link AbstractSocketClientContext#pendingBinaryRead}.  After the handoff, only the
 * selector thread mutates {@link #length}.  The requesting thread reads
 * {@link #length} only after being notified through {@link #lock}, which
 * establishes the necessary happens-before edge via the monitor's release
 * and acquire.
 *
 * @see AbstractSocketClientContext#pendingBinaryRead
 */
public final class SocketPendingRead {
    /**
     * The destination buffer.  The selector thread reads channel bytes
     * directly into this buffer.
     */
    public final ByteBuffer buffer;

    /**
     * If {@code true}, the selector continues reading until
     * {@code buffer.hasRemaining()} returns {@code false}, performing
     * as many read operations as necessary to fill the buffer completely.
     * If {@code false}, the selector completes the request after a
     * single successful read, regardless of how many bytes were
     * transferred.
     */
    public final boolean fullRead;

    /**
     * Monitor used to block the requesting thread until the selector
     * has satisfied or failed this read request.
     *
     * <p> The requesting thread calls {@code lock.wait()}; the selector
     * thread calls {@code lock.notifyAll()} when the request completes
     * or the channel reaches end-of-stream.  The monitor entry and exit
     * provide the memory visibility guarantee between the selector's
     * write to {@link #length} and the requesting thread's subsequent
     * read.
     */
    public final Object lock;

    /**
     * The total number of bytes read, or {@code -1} if the channel
     * reached end-of-stream before any data was transferred.
     *
     * <p> This field is initialized to {@code -1}.  The selector thread
     * accumulates successful reads by adding the return value of each
     * {@link SocketChannel#read(ByteBuffer) read} call.
     * After the lock is notified, the requesting thread inspects this
     * field to determine the outcome: a non-negative value indicates
     * success; {@code -1} indicates end-of-stream.
     */
    public int length;

    /**
     * Creates a pending read request.
     *
     * @param buffer   the destination buffer into which bytes will be read
     * @param fullRead {@code true} to fill the buffer completely;
     *                 {@code false} to return after a single read
     */
    public SocketPendingRead(ByteBuffer buffer, boolean fullRead) {
        this.buffer = buffer;
        this.fullRead = fullRead;
        this.lock = new Object();
        this.length = -1;
    }
}