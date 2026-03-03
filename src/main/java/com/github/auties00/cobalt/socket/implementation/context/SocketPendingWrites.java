
package com.github.auties00.cobalt.socket.implementation.context;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.nio.ByteBuffer;
import java.nio.channels.GatheringByteChannel;

/**
 * An unbounded thread-safe queue of {@link ByteBuffer} instances designed
 * for multiple-producer, single-consumer (MPSC) use.
 *
 * <p> This queue orders elements FIFO (first-in-first-out) with respect to
 * each individual producer.  The consumer retrieves elements in the global
 * order in which producers' stores became visible, which preserves per-producer
 * ordering.
 *
 * <p> Unlike most concurrent collections, this queue does not expose its
 * elements through iteration or bulk copy.  Instead, the consumer calls
 * {@link #claim()} to obtain a zero-copy view of the underlying array, then
 * {@link #release(int)} to advance past consumed elements.  The claimed
 * array can be passed directly to
 * {@link GatheringByteChannel#write(ByteBuffer[], int, int)
 * GatheringByteChannel.write} without intermediate copying.
 *
 * <p> The {@link #offer(ByteBuffer)} method is lock-free.  The common path
 * executes a single atomic increment and a single release-store with no
 * allocation.  When the internal storage fills, a new segment is allocated
 * and linked; this occurs at most once per {@code chunkCapacity} offers.
 *
 * <p> <em>Memory consistency effects:</em> actions in a producer thread
 * prior to placing a buffer into the queue
 * <i>happen-before</i> actions subsequent to the retrieval of that buffer
 * via {@link #claim()} in the consumer thread.
 *
 * @implNote This implementation uses a singly-linked chain of fixed-size
 * array segments (chunks).  Each chunk holds a {@code ByteBuffer[]} of
 * {@code chunkCapacity} elements and a monotonically increasing slot counter.
 * When a chunk fills, a successor is allocated and CAS-linked to it.
 * Producers advance a shared pointer to the latest chunk; the consumer
 * traverses the chain from oldest to newest.  Consumed chunks are unlinked
 * from the consumer side and become eligible for garbage collection.
 * Under steady state, where the consumer keeps up with producers, at most
 * two chunks are live at any time.
 */
public final class SocketPendingWrites {
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
                    SocketPendingWrites.class, "producerChunk", Chunk.class
            );
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    /**
     * A fixed-size array segment in the linked chain.
     *
     * <p> Each chunk holds up to {@code data.length} buffers.  The
     * {@code index} field records how many slots have been claimed by
     * producers; it may temporarily exceed the array length when multiple
     * producers race at the chunk boundary.  The {@code next} field
     * transitions from {@code null} to a successor chunk exactly once.
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
     * {@link SocketPendingWrites#claim()}.
     *
     * <p> The array reference points directly to the chunk's internal
     * storage; no elements are copied.  The region
     * {@code array[offset .. offset + count)} is guaranteed to contain
     * non-null, fully published buffers.
     *
     * @param array  the chunk's internal array, not a copy
     * @param offset index of the first available element
     * @param count  number of available elements starting at {@code offset}
     */
    public record Claim(ByteBuffer[] array, int offset, int count) {
        private static final Claim EMPTY = new Claim(new ByteBuffer[0], 0, 0);

        public boolean isEmpty() {
            return count == 0;
        }
    }

    private final int chunkCapacity;

    @SuppressWarnings({"unused", "FieldMayBeFinal"})
    private volatile Chunk producerChunk;

    // Consumer-only state; no synchronization needed.
    private Chunk consumerChunk;
    private int consumerOffset;

    /**
     * Creates a queue with the specified chunk capacity.
     *
     * @param chunkCapacity the number of elements per chunk, must be a
     *                      power of two
     * @throws IllegalArgumentException if {@code chunkCapacity} is not a
     *         power of two
     */
    public SocketPendingWrites(int chunkCapacity) {
        if (Integer.bitCount(chunkCapacity) != 1) {
            throw new IllegalArgumentException("Chunk capacity must be a power of 2");
        }
        this.chunkCapacity = chunkCapacity;
        var initial = new Chunk(chunkCapacity);
        this.producerChunk = initial;
        this.consumerChunk = initial;
    }

    /**
     * Inserts the specified buffer at the tail of this queue.
     *
     * <p> On the common path, where the current chunk has room, this method
     * executes one atomic increment to claim a slot and one release-store
     * to publish the buffer, with no allocation and no CAS retry.
     *
     * <p> When the current chunk is full, one racing producer allocates a
     * successor chunk and CAS-links it; the remaining producers observe
     * the successor and retry.  This path executes at most once per
     * {@code chunkCapacity} insertions.
     *
     * @param buffer the buffer to insert
     */
    public void offer(ByteBuffer buffer) {
        while (true) {
            var chunk = producerChunk;

            // Claim the next available slot.  Producers that overshoot
            // chunkCapacity fall through to the slow path; the consumer
            // caps its reads at chunkCapacity via Math.min.
            var idx = (int) CHUNK_INDEX.getAndAdd(chunk, 1);

            if (idx < chunkCapacity) {
                // Publish the buffer with release semantics so the
                // consumer's getAcquire observes both the reference
                // and any prior stores into the buffer's content.
                ELEMENT.setRelease(chunk.data, idx, buffer);
                return;
            }

            // Chunk full.  Link a successor if none exists yet.
            // Exactly one CAS wins; losers see the winner's chunk.
            if (chunk.next == null) {
                CHUNK_NEXT.compareAndSet(chunk, null, new Chunk(chunkCapacity));
            }

            // Advance the global producer pointer.  One CAS wins;
            // losers re-read on the next iteration.
            PRODUCER_CHUNK.compareAndSet(this, chunk, chunk.next);
        }
    }

    /**
     * Returns {@code true} if this queue contains no elements available
     * for consumption.
     *
     * <p> This is a best-effort snapshot.  A concurrent {@link #offer}
     * may invalidate the result immediately after it is observed.
     *
     * @apiNote This method is intended for the consumer thread, for
     * example to decide whether to clear a write-interest registration.
     *
     * @return {@code true} if no elements are currently available
     */
    public boolean isEmpty() {
        var chunk = consumerChunk;
        var produced = Math.min((int) CHUNK_INDEX.getAcquire(chunk), chunkCapacity);
        return consumerOffset >= produced && chunk.next == null;
    }

    /**
     * Claims a contiguous slice of the current chunk's backing array for
     * zero-copy consumption.
     *
     * <p> The returned {@link Claim} exposes the chunk's internal
     * {@code ByteBuffer[]} directly.  The caller may pass it to
     * {@link GatheringByteChannel#write(ByteBuffer[], int, int)
     * GatheringByteChannel.write} without copying.
     *
     * <p> Each invocation covers at most one chunk.  If elements span
     * multiple chunks, the caller should loop over claim, write, and
     * release until this method returns {@code null}.
     *
     * <p> After processing, the caller must invoke {@link #release(int)}
     * with the number of elements that were fully consumed.  Elements
     * that were not released remain at the same position and are returned
     * by the next call to this method.
     *
     * @implNote If a producer has claimed a slot but has not yet published
     * its buffer (the window between the atomic increment and the
     * release-store), this method spins via {@link Thread#onSpinWait()}
     * until the element becomes visible.  This window is typically
     * single-digit nanoseconds.
     *
     * @return a zero-copy view into the current chunk
     */
    public Claim claim() {
        while (true) {
            var chunk = consumerChunk;

            // Cap at chunkCapacity because racing producers may have
            // pushed the index beyond the array length.
            var produced = Math.min((int) CHUNK_INDEX.getAcquire(chunk), chunkCapacity);

            if (consumerOffset < produced) {
                // Spin until every slot in the range has been published.
                // The store that completes the publication is a setRelease
                // in offer(); our getAcquire establishes the happens-before.
                for (var i = consumerOffset; i < produced; i++) {
                    while (ELEMENT.getAcquire(chunk.data, i) == null) {
                        Thread.onSpinWait();
                    }
                }
                return new Claim(chunk.data, consumerOffset, produced - consumerOffset);
            }

            // Current chunk fully consumed.  Advance to the successor
            // if one exists; the old chunk becomes eligible for GC.
            if (consumerOffset == chunkCapacity && chunk.next != null) {
                consumerChunk = chunk.next;
                consumerOffset = 0;
                continue;
            }

            return Claim.EMPTY;
        }
    }

    /**
     * Releases {@code consumed} elements from the head of the last
     * {@linkplain #claim() claimed} region, advancing the consumer past them.
     *
     * <p> Released slots are nulled so their {@code ByteBuffer} references
     * become eligible for garbage collection immediately, rather than being
     * retained until the entire chunk is reclaimed.  If the current chunk
     * is fully consumed and a successor exists, the consumer advances to
     * it automatically.
     *
     * @param consumed the number of elements to release, must satisfy
     *        {@code 0 <= consumed <= claim.count()}
     */
    public void release(int consumed) {
        // Null out consumed slots with plain stores.  Only the consumer
        // writes to these positions, and producers never read from them.
        for (var i = consumerOffset; i < consumerOffset + consumed; i++) {
            consumerChunk.data[i] = null;
        }
        consumerOffset += consumed;

        if (consumerOffset == chunkCapacity && consumerChunk.next != null) {
            consumerChunk = consumerChunk.next;
            consumerOffset = 0;
        }
    }
}