package com.github.auties00.cobalt.store.linked.protobuf.persistent.mdbx;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Unbounded multiple-producer single-consumer queue of {@link MdbxWriteOp} instances backing the
 * {@code PersistentMessageStore} group-commit writer.
 *
 * <p>This is the chunked-array MPSC originally written for the socket stack's outbound writes,
 * recovered and specialised to {@link MdbxWriteOp}. Producers reserve a slot with a single wait-free
 * {@link VarHandle#getAndAdd atomic increment} on the tail chunk's index and publish the element
 * with one release store, so concurrent producers never contend on a lock and never allocate on the
 * common path. The single consumer drains a contiguous slice of the head chunk through
 * {@link #claim()} and advances past it with {@link #release(int)}; the slice maps directly onto the
 * batch the writer commits in one transaction.
 *
 * @implNote
 * This implementation links fixed-capacity chunks into a forward chain. Producers atomically claim
 * an index in the tail chunk and hop to (or lazily allocate) the next chunk once a chunk fills. The
 * consumer owns {@link #consumerChunk} and {@link #consumerOffset} exclusively, so its reads need no
 * locking. Wakeup of a parked consumer is the caller's responsibility (the store uses
 * {@link java.util.concurrent.locks.LockSupport}); this class only provides {@link #isEmpty()} so
 * the consumer can decide to park.
 */
public final class MdbxWriteQueue {
    private static final VarHandle ELEMENT;
    private static final VarHandle CHUNK_INDEX;
    private static final VarHandle CHUNK_NEXT;
    private static final VarHandle PRODUCER_CHUNK;

    static {
        try {
            var lookup = MethodHandles.lookup();
            ELEMENT = MethodHandles.arrayElementVarHandle(MdbxWriteOp[].class);
            CHUNK_INDEX = lookup.findVarHandle(Chunk.class, "index", int.class);
            CHUNK_NEXT = lookup.findVarHandle(Chunk.class, "next", Chunk.class);
            PRODUCER_CHUNK = lookup.findVarHandle(MdbxWriteQueue.class, "producerChunk", Chunk.class);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    /**
     * One fixed-size segment in the linked chain of chunks.
     */
    static final class Chunk {
        /**
         * Backing array for this chunk's slots; entries are published by producers via the
         * {@link #ELEMENT} VarHandle and cleared by the consumer in {@link #release(int)}.
         */
        final MdbxWriteOp[] data;

        /**
         * Producer claim counter, incremented atomically by {@link #offer(MdbxWriteOp)} to reserve the
         * next slot.
         */
        @SuppressWarnings("unused") volatile int index;

        /**
         * Forward pointer to the next chunk, lazily allocated when {@link #index} reaches the chunk
         * capacity.
         */
        @SuppressWarnings("unused") volatile Chunk next;

        /**
         * Creates a chunk with the given slot capacity.
         *
         * @param capacity the number of slots in this chunk
         */
        Chunk(int capacity) {
            this.data = new MdbxWriteOp[capacity];
        }
    }

    /**
     * Zero-copy view into a chunk's backing array returned by {@link MdbxWriteQueue#claim()}.
     *
     * @param array  the chunk's internal array, shared with the queue
     * @param offset index of the first available element
     * @param count  number of available elements starting at {@code offset}
     */
    public record Claim(MdbxWriteOp[] array, int offset, int count) {
        /**
         * Sentinel claim used when the queue has nothing to deliver.
         */
        private static final Claim EMPTY = new Claim(new MdbxWriteOp[0], 0, 0);

        /**
         * Returns whether this claim contains no elements.
         *
         * @return {@code true} if empty
         */
        public boolean isEmpty() {
            return count == 0;
        }
    }

    /**
     * Number of slots per chunk, fixed at construction time; also the maximum size of a single
     * {@link #claim()} slice and therefore of one committed batch.
     */
    private final int chunkCapacity;

    /**
     * Backpressure threshold; {@link #offer(MdbxWriteOp)} returns {@code false} once the queue holds this
     * many unreleased ops.
     */
    private final int maxPending;

    /**
     * Counter of ops currently in flight, used to enforce {@link #maxPending}.
     */
    private final AtomicInteger pendingCount;

    /**
     * Tail chunk, where producers reserve slots; updated atomically via {@link #PRODUCER_CHUNK} when
     * a chunk fills up.
     */
    @SuppressWarnings({"unused", "FieldMayBeFinal"})
    private volatile Chunk producerChunk;

    /**
     * Head chunk, owned by the consumer thread.
     */
    private Chunk consumerChunk;

    /**
     * Index of the next element the consumer will read from {@link #consumerChunk}.
     */
    private int consumerOffset;

    /**
     * Creates a queue with the given chunk capacity and backpressure limit.
     *
     * @param chunkCapacity the number of ops per chunk and per committed batch; must be a power of
     *                      two
     * @param maxPending    the maximum number of in-flight ops before {@link #offer(MdbxWriteOp)} returns
     *                      {@code false}
     * @throws IllegalArgumentException if {@code chunkCapacity} is not a power of two
     */
    public MdbxWriteQueue(int chunkCapacity, int maxPending) {
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
     * Inserts {@code op} at the tail of this queue.
     *
     * @apiNote
     * Returns {@code false} when accepting the op would push the number of in-flight ops above
     * {@link #maxPending}; the caller decides whether to spin or apply backpressure.
     *
     * @implNote
     * This implementation reserves a slot index with a single {@link VarHandle#getAndAdd} and a
     * release store of the element. When a chunk fills it lazily links and hops to the next chunk.
     *
     * @param op the op to enqueue
     * @return {@code true} if the op was enqueued, {@code false} if the queue is at capacity
     */
    public boolean offer(MdbxWriteOp op) {
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
                ELEMENT.setRelease(chunk.data, idx, op);
                return true;
            }

            if (chunk.next == null) {
                CHUNK_NEXT.compareAndSet(chunk, null, new Chunk(chunkCapacity));
            }

            PRODUCER_CHUNK.compareAndSet(this, chunk, chunk.next);
        }
    }

    /**
     * Returns whether this queue currently has no ops available to the consumer.
     *
     * @return {@code true} if the queue is empty from the consumer's point of view
     */
    public boolean isEmpty() {
        var chunk = consumerChunk;
        var produced = Math.min((int) CHUNK_INDEX.getAcquire(chunk), chunkCapacity);
        return consumerOffset >= produced && chunk.next == null;
    }

    /**
     * Claims a contiguous slice of the current chunk's backing array for the consumer to drain.
     *
     * @apiNote
     * The returned view shares the queue's backing storage; the consumer must call
     * {@link #release(int)} once it has finished with the slice so the entries can be cleared and the
     * {@link #pendingCount} accounting updated.
     *
     * @implNote
     * This implementation spin-waits on any reserved-but-not-yet-published slot so the returned slice
     * holds no {@code null} holes, then hops to the next chunk when the current one is fully drained.
     *
     * @return a zero-copy view into the current chunk, possibly {@link Claim#isEmpty() empty}
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
     * Releases the first {@code consumed} elements of the most recently claimed region.
     *
     * @apiNote
     * Clears the entries, advances {@link #consumerOffset}, hops to the next chunk when the current
     * one is fully drained, and decrements {@link #pendingCount} so producers blocked on backpressure
     * can proceed.
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
