package com.github.auties00.cobalt.socket.layer.transport;

import com.github.auties00.cobalt.socket.layer.threading.SocketClientContext;
import com.github.auties00.cobalt.socket.layer.threading.SocketClientLayerContext;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.nio.ByteBuffer;
import java.nio.channels.GatheringByteChannel;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Transport-level per-connection state.
 *
 * <p>This context holds the connection lifecycle state and the outbound
 * write queue.  It does not implement
 * {@link SocketClientLayerContext}
 * because the transport layer is the byte source/sink, not a byte
 * processor.  Processing contexts (TLS, tunnel, WebSocket, application)
 * are stored separately in the
 * {@link SocketClientContext}
 * layer context map.
 *
 * <p>The {@link PendingRead} inner class is used during the pre-tunnel
 * phase for blocking proxy handshake reads.  The {@link PendingWrites}
 * inner class is a lock-free MPSC queue for outbound buffers, used in
 * both phases.
 */
public final class SocketClientTransportLayerContext {
    private static final int WRITES_CHUNK_CAPACITY = 64;
    private static final VarHandle CONNECTED;

    static {
        try {
            var lookup = MethodHandles.lookup();
            CONNECTED = lookup.findVarHandle(SocketClientTransportLayerContext.class, "connected", boolean.class);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

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
    public final Object connectionLock;

    /**
     * Lock-free MPSC queue of outbound buffers waiting to be written to
     * the channel.
     *
     * <p>Producers (any thread calling {@code sendBinary}) enqueue buffers
     * via {@link PendingWrites#offer offer}.  The selector thread drains
     * the queue via {@link PendingWrites#claim() claim} and
     * {@link PendingWrites#release(int) release}, passing the backing
     * array directly to
     * {@link GatheringByteChannel#write(ByteBuffer[], int, int)
     * GatheringByteChannel.write}.
     */
    public final PendingWrites pendingWrites;

    /**
     * Creates a transport layer context for a new connection.
     */
    public SocketClientTransportLayerContext() {
        this.connectionLock = new Object();
        this.pendingWrites = new PendingWrites(WRITES_CHUNK_CAPACITY);
    }

    /**
     * Returns whether the connection is currently active.
     *
     * @return {@code true} if connected
     */
    public boolean isConnected() {
        return (boolean) CONNECTED.getVolatile(this);
    }

    /**
     * Sets the connection state.
     *
     * @param value {@code true} to mark as connected, {@code false} to
     *              mark as disconnected
     */
    public void setConnected(boolean value) {
        CONNECTED.setVolatile(this, value);
    }

    /**
     * Atomically sets the connection state if it currently has the expected
     * value.
     *
     * @param expected the expected current value
     * @param newValue the new value
     * @return {@code true} if the compare-and-set succeeded
     */
    public boolean compareAndSetConnected(boolean expected, boolean newValue) {
        return CONNECTED.compareAndSet(this, expected, newValue);
    }

    /**
     * A pending read request submitted to the selector during the pre-tunnel
     * phase of a proxied connection.
     *
     * <p>The requesting thread constructs a {@code PendingRead}, posts it
     * to the tunnel layer context, and blocks on {@link #lock}.  The
     * selector thread reads bytes from the channel into {@link #buffer},
     * updates {@link #length}, and notifies the lock when the request is
     * satisfied.
     *
     * <p>A request is considered satisfied when either:
     * <ul>
     * <li>{@link #fullRead} is {@code false} and at least one read
     *     operation has completed, or
     * <li>{@link #fullRead} is {@code true} and the buffer has no
     *     remaining capacity.
     * </ul>
     *
     * <p>If the channel reaches end-of-stream before the request is
     * satisfied, the selector sets {@link #length} to {@code -1} and
     * notifies the lock.
     */
    public static final class PendingRead {
        /**
         * The destination buffer.
         */
        public final ByteBuffer buffer;

        /**
         * If {@code true}, the selector continues reading until the buffer
         * is completely filled.  If {@code false}, the selector completes
         * the request after a single successful read.
         */
        public final boolean fullRead;

        /**
         * Monitor used to block the requesting thread until the selector
         * has satisfied or failed this read request.
         */
        public final Object lock;

        /**
         * The total number of bytes read, or {@code -1} if the channel
         * reached end-of-stream before any data was transferred.
         */
        public int length;

        /**
         * Creates a pending read request.
         *
         * @param buffer   the destination buffer
         * @param fullRead {@code true} to fill the buffer completely;
         *                 {@code false} to return after a single read
         */
        public PendingRead(ByteBuffer buffer, boolean fullRead) {
            this.buffer = buffer;
            this.fullRead = fullRead;
            this.lock = new Object();
            this.length = -1;
        }
    }

    /**
     * An unbounded thread-safe queue of {@link ByteBuffer} instances designed
     * for multiple-producer, single-consumer (MPSC) use.
     *
     * <p>This queue orders elements FIFO with respect to each individual
     * producer.  The consumer retrieves elements in the global order in
     * which producers' stores became visible.
     *
     * <p>The {@link #offer(ByteBuffer)} method is lock-free.  The common
     * path executes a single atomic increment and a single release-store
     * with no allocation.
     *
     * <p><em>Memory consistency effects:</em> actions in a producer thread
     * prior to placing a buffer into the queue <i>happen-before</i> actions
     * subsequent to the retrieval of that buffer via {@link #claim()} in
     * the consumer thread.
     */
    public static final class PendingWrites {
        private static final VarHandle ELEMENT;
        private static final VarHandle CHUNK_INDEX;
        private static final VarHandle CHUNK_NEXT;
        private static final VarHandle PRODUCER_CHUNK;

        static {
            try {
                var lookup = MethodHandles.lookup();
                ELEMENT = MethodHandles.arrayElementVarHandle(ByteBuffer[].class);
                CHUNK_INDEX = lookup.findVarHandle(Chunk.class, "index", int.class);
                CHUNK_NEXT = lookup.findVarHandle(Chunk.class, "next", Chunk.class);
                PRODUCER_CHUNK = lookup.findVarHandle(
                        PendingWrites.class, "producerChunk", Chunk.class
                );
            } catch (ReflectiveOperationException e) {
                throw new ExceptionInInitializerError(e);
            }
        }

        /**
         * A fixed-size array segment in the linked chain.
         */
        static final class Chunk {
            final ByteBuffer[] data;
            @SuppressWarnings("unused") volatile int index;
            @SuppressWarnings("unused") volatile Chunk next;

            Chunk(int capacity) {
                this.data = new ByteBuffer[capacity];
            }
        }

        /**
         * A zero-copy view into a chunk's backing array returned by
         * {@link PendingWrites#claim()}.
         *
         * @param array  the chunk's internal array, not a copy
         * @param offset index of the first available element
         * @param count  number of available elements starting at offset
         */
        public record Claim(ByteBuffer[] array, int offset, int count) {
            private static final Claim EMPTY = new Claim(new ByteBuffer[0], 0, 0);

            /**
             * Returns whether this claim contains no elements.
             *
             * @return {@code true} if empty
             */
            public boolean isEmpty() {
                return count == 0;
            }
        }

        private final int chunkCapacity;
        private final int maxPending;
        private final AtomicInteger pendingCount;

        @SuppressWarnings({"unused", "FieldMayBeFinal"})
        private volatile Chunk producerChunk;

        private Chunk consumerChunk;
        private int consumerOffset;

        /**
         * Creates a queue with the specified chunk capacity and a default
         * backpressure limit of {@code chunkCapacity * 32}.
         *
         * @param chunkCapacity the number of elements per chunk, must be a
         *                      power of two
         * @throws IllegalArgumentException if chunkCapacity is not a power of two
         */
        public PendingWrites(int chunkCapacity) {
            this(chunkCapacity, chunkCapacity * 32);
        }

        /**
         * Creates a queue with the specified chunk capacity and backpressure
         * limit.
         *
         * @param chunkCapacity the number of elements per chunk, must be a
         *                      power of two
         * @param maxPending    the maximum number of pending buffers before
         *                      {@link #offer(ByteBuffer)} returns {@code false}
         * @throws IllegalArgumentException if chunkCapacity is not a power of two
         */
        public PendingWrites(int chunkCapacity, int maxPending) {
            if (Integer.bitCount(chunkCapacity) != 1) {
                throw new IllegalArgumentException("Chunk capacity must be a power of 2");
            }
            this.chunkCapacity = chunkCapacity;
            this.maxPending = maxPending;
            this.pendingCount = new AtomicInteger();
            var initial = new Chunk(chunkCapacity);
            this.producerChunk = initial;
            this.consumerChunk = initial;
        }

        /**
         * Inserts the specified buffer at the tail of this queue.
         *
         * <p>If the number of pending (not yet released) buffers would
         * exceed the configured maximum, the buffer is rejected and this
         * method returns {@code false}.
         *
         * @param buffer the buffer to insert
         * @return {@code true} if the buffer was enqueued, {@code false} if
         *         the queue is at capacity
         */
        public boolean offer(ByteBuffer buffer) {
            var current = pendingCount.get();
            while (true) {
                if (current >= maxPending) {
                    return false;
                }
                var witness = pendingCount.compareAndExchange(current, current + 1);
                if (witness == current) {
                    break;
                }
                current = witness;
            }

            while (true) {
                var chunk = producerChunk;
                var idx = (int) CHUNK_INDEX.getAndAdd(chunk, 1);

                if (idx < chunkCapacity) {
                    ELEMENT.setRelease(chunk.data, idx, buffer);
                    return true;
                }

                if (chunk.next == null) {
                    CHUNK_NEXT.compareAndSet(chunk, null, new Chunk(chunkCapacity));
                }

                PRODUCER_CHUNK.compareAndSet(this, chunk, chunk.next);
            }
        }

        /**
         * Returns {@code true} if this queue contains no elements available
         * for consumption.
         *
         * @return {@code true} if no elements are currently available
         */
        public boolean isEmpty() {
            var chunk = consumerChunk;
            var produced = Math.min((int) CHUNK_INDEX.getAcquire(chunk), chunkCapacity);
            return consumerOffset >= produced && chunk.next == null;
        }

        /**
         * Claims a contiguous slice of the current chunk's backing array.
         *
         * @return a zero-copy view into the current chunk
         */
        public Claim claim() {
            while (true) {
                var chunk = consumerChunk;
                var produced = Math.min((int) CHUNK_INDEX.getAcquire(chunk), chunkCapacity);

                if (consumerOffset < produced) {
                    for (var i = consumerOffset; i < produced; i++) {
                        while (ELEMENT.getAcquire(chunk.data, i) == null) {
                            Thread.onSpinWait();
                        }
                    }
                    return new Claim(chunk.data, consumerOffset, produced - consumerOffset);
                }

                if (consumerOffset == chunkCapacity && chunk.next != null) {
                    consumerChunk = chunk.next;
                    consumerOffset = 0;
                    continue;
                }

                return Claim.EMPTY;
            }
        }

        /**
         * Releases consumed elements from the head of the last claimed
         * region.
         *
         * @param consumed the number of elements to release
         */
        public void release(int consumed) {
            for (var i = consumerOffset; i < consumerOffset + consumed; i++) {
                consumerChunk.data[i] = null;
            }
            consumerOffset += consumed;
            pendingCount.addAndGet(-consumed);

            if (consumerOffset == chunkCapacity && consumerChunk.next != null) {
                consumerChunk = consumerChunk.next;
                consumerOffset = 0;
            }
        }
    }
}
