package com.github.auties00.cobalt.calls2.platform;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.VarHandle;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Wire-exact off-heap single-producer single-consumer ring carrying outbound datagrams
 * from the SCTP engine to the Java sender.
 *
 * <p>The buffer is the Java side of the WhatsApp call transport's outgoing-packet plane:
 * the engine enqueues a self-describing datagram record with {@link #write(MemorySegment, int, MemorySegment)}
 * instead of issuing a synchronous send for every packet, and a host drain loop later pulls
 * each record back out with {@link #poll(FrameSink)} or {@link #drain(FrameSink)} and forwards
 * it to a socket. The producer thread owns the write index and the consumer thread owns the read
 * index, so the two sides never block each other; the only mutual exclusion is a single-bit spinlock
 * that brackets a record write so a record is enqueued atomically with respect to other producers.
 *
 * <p>The backing store is one off-heap segment laid out exactly as the native ring expects, so the
 * same segment could be handed to a native usrsctp output callback and observed byte-for-byte. The
 * first word holds the producer head ({@code writeIdx}), the second word the consumer tail
 * ({@code readIdx}), and the remainder is the circular data region of {@code dataSize} bytes:
 * {@snippet :
 * // base segment, 4-byte aligned, total size = 8 + dataSize
 * // [ writeIdx u32 @0 ][ readIdx u32 @4 ][ data u8[dataSize] @8.. ]
 * }
 * Each datagram is stored as a self-describing record so the drain side can split the byte stream
 * back into individual sends without any external length table:
 * {@snippet :
 * // offset 0      : u8  addrLen        address length / family marker
 * // offset 1      : u8  port_lo        low byte of the 16-bit address/port index
 * // offset 2      : u8  port_hi        high byte of the address/port index
 * // offset 3      : u8  totalLen_lo    low byte of (payloadLen + addrLen + 5)
 * // offset 4      : u8  totalLen_hi    high byte; totalLen is 16-bit little-endian
 * // offset 5      : u8  addr[addrLen]  raw sockaddr bytes
 * // offset 5+addrLen : u8 payload[payloadLen]  datagram bytes
 * }
 * Records wrap across the end of the data region; the header, address, and payload are each split at
 * the boundary so a single record may straddle the wrap point. The ring never overwrites unread data:
 * one byte of the data region is always left free to disambiguate full from empty, so the largest
 * record that can ever be enqueued is {@code dataSize - 1} bytes and a write that does not fit is
 * rejected rather than clobbering the consumer. A rejected write returns {@code false} so the producer
 * can apply backpressure rather than overwriting unread data.
 *
 * <p>Instances allocate their backing segment in a private {@link Arena} and release it through
 * {@link #close()}; the buffer is unusable after closing. The ring is single-producer single-consumer:
 * at most one thread may call {@link #write(MemorySegment, int, MemorySegment)} concurrently with at
 * most one thread calling {@link #poll(FrameSink)} or {@link #drain(FrameSink)}. The lifecycle and
 * accessor methods ({@link #isInitialized()}, {@link #capacity()}, {@link #segment()}) may be called
 * from any thread.
 *
 * @apiNote Treat a {@code false} return from {@link #write(MemorySegment, int, MemorySegment)} as a
 * routine backpressure signal, not an error: the producer is expected to retry the record once the
 * consumer frees space. There is no notification when the consumer frees space; the producer observes
 * the freed space only on its next write attempt, so a drain loop should run continuously rather than
 * waiting to be woken.
 * @implNote This implementation ports the WASM transport's outgoing-packet ring
 * ({@code WasmSctpRingBuffer}, {@code sys/WasmSctpRingBuffer.cpp}) and its writer
 * {@code wasm_voip_sendto} (fn9329) plus the wrap-aware copy helper {@code ring_buffer_write}
 * (fn9325) from the wa-voip engine (WASM module {@code ff-tScznZ8P}). The native layout keeps the
 * write and read indices in the first eight bytes of the shared segment (pointed to by
 * {@code DAT_150658}/{@code DAT_15065c}), the data region after them ({@code DAT_150660}, capacity
 * {@code DAT_150664}), and the spinlock ({@code DAT_150669}) as a side global rather than inside the
 * shared ring; this port reproduces the segment layout exactly and keeps the spinlock as a separate
 * off-heap cell so a native co-producer would see the identical ring, widening it from the native
 * single byte to a 4-byte integer only because the foreign-memory API offers atomic compare-and-set
 * solely on integer-or-wider layouts. The free-space computation is
 * the native {@code free = (readIdx + ~writeIdx + cap) % cap}, which leaves one byte unused so an
 * empty ring reports {@code cap - 1} free; the write guard
 * ({@code initialized && 1 <= payloadLen < 0x10000 && totalLen < 0x2001}), the five-byte header with
 * a 16-bit little-endian total length, the three wrap-aware copies, and the
 * {@code writeIdx = (writeIdx + totalLen) % cap} advance are reproduced byte-for-byte. The native
 * spinlock plus plain stores are mapped to acquire/release ordering on the index handles so the
 * Java memory model publishes a record's bytes before the index that exposes them, which the WASM
 * threaded memory model achieved through the spinlock alone.
 */
public final class SctpRingBuffer implements AutoCloseable {
    /**
     * Byte offset of the producer head ({@code writeIdx}) within the backing segment.
     *
     * <p>The first 32-bit word holds the index at which the next record will be written;
     * it is advanced only by the producer and read by the consumer to bound a drain.
     */
    private static final long WRITE_INDEX_OFFSET = 0L;

    /**
     * Byte offset of the consumer tail ({@code readIdx}) within the backing segment.
     *
     * <p>The second 32-bit word holds the index of the oldest unread byte; it is advanced
     * only by the consumer and read by the producer to compute free space.
     */
    private static final long READ_INDEX_OFFSET = 4L;

    /**
     * Byte offset of the circular data region within the backing segment.
     *
     * <p>The data region follows the two index words, so the total segment size is this
     * offset plus the configured capacity.
     */
    private static final long DATA_OFFSET = 8L;

    /**
     * Size in bytes of the self-describing record header that precedes each datagram.
     *
     * <p>The header is {@code addrLen}, the two port bytes, and the two total-length bytes,
     * laid out as documented on the class; the total record length counts these bytes.
     */
    private static final int HEADER_SIZE = 5;

    /**
     * Exclusive upper bound on a datagram payload length, in bytes.
     *
     * <p>A payload must be at least one byte and strictly less than this value; the bound is
     * the largest value the 16-bit total-length field could otherwise be made to misrepresent
     * and matches the native {@code maxPayload} guard.
     */
    private static final int MAX_PAYLOAD = 0x10000;

    /**
     * Exclusive upper bound on the total record length, in bytes.
     *
     * <p>The total record is the header, the address bytes, and the payload; it must be strictly
     * less than this value, matching the native {@code maxRecord} guard that caps a single ring
     * record so it can never approach the 16-bit length field's range.
     */
    private static final int MAX_RECORD = 0x2001;

    /**
     * Value marking the spinlock as free.
     *
     * <p>The lock is acquired by transitioning the lock cell from this value to
     * {@link #LOCK_HELD} and released by storing this value back.
     */
    private static final int LOCK_FREE = 0;

    /**
     * Value marking the spinlock as held by a producer.
     *
     * <p>A producer spins on a compare-and-set from {@link #LOCK_FREE} to this value before
     * mutating the ring and stores {@link #LOCK_FREE} when the record write completes.
     */
    private static final int LOCK_HELD = 1;

    /**
     * Handle reading and writing the 32-bit index words with plain and ordered access modes.
     *
     * <p>Bound to a native-order 4-byte integer; the two index words sit at fixed, naturally
     * aligned offsets in the segment, so a single handle services both. Ordered access modes
     * provide the acquire/release publication the producer and consumer rely on.
     */
    private static final VarHandle INDEX_HANDLE =
            ValueLayout.JAVA_INT.varHandle();

    /**
     * Handle performing the compare-and-set that implements the spinlock.
     *
     * <p>Bound to a native-order 4-byte integer in the lock segment; the compare-and-set transitions
     * the cell between {@link #LOCK_FREE} and {@link #LOCK_HELD} to bracket a record write. The cell
     * is an integer rather than a byte because the foreign-memory API provides atomic compare-and-set
     * only on integer-or-wider layouts.
     */
    private static final VarHandle LOCK_HANDLE =
            ValueLayout.JAVA_INT.varHandle();

    /**
     * Arena owning the backing segment and the lock byte for this ring's lifetime.
     *
     * <p>Both off-heap allocations are made in this arena and freed together when the ring is
     * closed; the arena is confined to no thread so the producer and consumer may access the
     * segment from different threads.
     */
    private final Arena arena;

    /**
     * Off-heap segment holding the two index words followed by the circular data region.
     *
     * <p>Laid out exactly as the native ring expects so it could be shared with a native
     * producer; its size is {@link #DATA_OFFSET} plus {@link #capacity}.
     */
    private final MemorySegment segment;

    /**
     * Off-heap integer cell backing the producer spinlock.
     *
     * <p>Kept separate from {@link #segment} to mirror the native side global rather than embedding
     * the lock in the shared ring layout; widened from the native single byte to an integer because
     * compare-and-set is supported only on integer-or-wider foreign layouts.
     */
    private final MemorySegment lock;

    /**
     * Capacity of the circular data region in bytes.
     *
     * <p>All index arithmetic is taken modulo this value; one byte is always left free, so the
     * largest enqueueable record is {@code capacity - 1} bytes.
     */
    private final int capacity;

    /**
     * Whether the ring is installed and accepting writes.
     *
     * <p>Set true at construction and cleared by {@link #close()}; a write attempted while this
     * is false is rejected.
     */
    private volatile boolean initialized;

    /**
     * Guards {@link #close()} so the backing arena is released at most once.
     *
     * <p>Flipped from {@code false} to {@code true} by a single {@link AtomicBoolean#compareAndSet}
     * in {@link #close()}; only the winning caller runs the arena teardown, so two concurrent closes
     * cannot both invoke {@link Arena#close()} and make the second throw on an already-closed arena.
     */
    private final AtomicBoolean closed = new AtomicBoolean();

    /**
     * Constructs a ring with the given data-region capacity in bytes.
     *
     * <p>Allocates a single off-heap segment of {@code capacity + 8} bytes for the two index words
     * and the circular data region, and a separate one-byte segment for the spinlock, both in a
     * fresh shared {@link Arena}. The indices and the lock are zeroed, leaving the ring empty,
     * unlocked, and initialized.
     *
     * @param capacity the size of the circular data region in bytes; must be at least two so that
     *                 at least one payload byte can be stored after the mandatory free byte
     * @throws IllegalArgumentException if {@code capacity} is less than two
     */
    public SctpRingBuffer(int capacity) {
        if (capacity < 2) {
            throw new IllegalArgumentException("capacity must be at least 2: " + capacity);
        }
        this.capacity = capacity;
        this.arena = Arena.ofShared();
        this.segment = arena.allocate(DATA_OFFSET + capacity, 4);
        this.lock = arena.allocate(ValueLayout.JAVA_INT);
        INDEX_HANDLE.set(segment, WRITE_INDEX_OFFSET, 0);
        INDEX_HANDLE.set(segment, READ_INDEX_OFFSET, 0);
        LOCK_HANDLE.set(lock, 0L, LOCK_FREE);
        this.initialized = true;
    }

    /**
     * Enqueues one outbound datagram as a self-describing record, returning whether it was stored.
     *
     * <p>Rejects the datagram, returning {@code false}, when the ring is closed, when the payload is
     * empty or at least {@link #MAX_PAYLOAD} bytes, when the whole record reaches {@link #MAX_RECORD}
     * bytes, or when the free space is smaller than the record. A rejected datagram is the producer's
     * cue to send it by a direct path. Otherwise the producer acquires the spinlock, recomputes the
     * free space against the current consumer position, and if the record fits writes the five-byte
     * header, the address bytes, and the payload, each split across the data-region wrap boundary as
     * needed, then publishes the advanced write index and releases the lock, returning {@code true}.
     *
     * <p>The address length is taken from the size of {@code address} and stored in the header's first
     * byte, so the address segment must be at most {@code 255} bytes. The port index occupies the two
     * port bytes of the header as a 16-bit little-endian value; only its low sixteen bits are stored.
     *
     * @param address the raw sockaddr bytes to record ahead of the payload; never {@code null}, at
     *                most {@code 255} bytes
     * @param port    the 16-bit address or port index to record; only the low sixteen bits are used
     * @param payload the datagram bytes to enqueue; never {@code null}, between one and
     *                {@link #MAX_PAYLOAD} minus one bytes inclusive
     * @return {@code true} if the record was stored, {@code false} if it was rejected and the caller
     * must retry it once the consumer frees space
     * @throws NullPointerException     if {@code address} or {@code payload} is {@code null}
     * @throws IllegalArgumentException if {@code address} exceeds {@code 255} bytes
     */
    public boolean write(MemorySegment address, int port, MemorySegment payload) {
        Objects.requireNonNull(address, "address cannot be null");
        Objects.requireNonNull(payload, "payload cannot be null");
        var addrLen = (int) address.byteSize();
        if (addrLen > 0xFF) {
            throw new IllegalArgumentException("address cannot exceed 255 bytes: " + addrLen);
        }
        var payloadLen = (int) payload.byteSize();
        var totalLen = payloadLen + addrLen + HEADER_SIZE;
        if (!initialized || payloadLen < 1 || payloadLen >= MAX_PAYLOAD || totalLen >= MAX_RECORD) {
            return false;
        }
        acquireLock();
        try {
            var writeIdx = Integer.toUnsignedLong((int) INDEX_HANDLE.get(segment, WRITE_INDEX_OFFSET));
            var readIdx = Integer.toUnsignedLong((int) INDEX_HANDLE.getAcquire(segment, READ_INDEX_OFFSET));
            var free = freeSpace(writeIdx, readIdx);
            if (free < totalLen) {
                return false;
            }
            var headerOffset = writeIdx;
            putByte(headerOffset, (byte) addrLen);
            putByte(advance(headerOffset, 1), (byte) (port & 0xFF));
            putByte(advance(headerOffset, 2), (byte) ((port >>> 8) & 0xFF));
            putByte(advance(headerOffset, 3), (byte) (totalLen & 0xFF));
            putByte(advance(headerOffset, 4), (byte) ((totalLen >>> 8) & 0xFF));
            writeWrapped(advance(headerOffset, HEADER_SIZE), address, addrLen);
            writeWrapped(advance(headerOffset, HEADER_SIZE + addrLen), payload, payloadLen);
            var nextWrite = advance(writeIdx, totalLen);
            INDEX_HANDLE.setRelease(segment, WRITE_INDEX_OFFSET, (int) nextWrite);
            return true;
        } finally {
            releaseLock();
        }
    }

    /**
     * Removes the oldest record from the ring and delivers its address and payload to the sink.
     *
     * <p>Reads the five-byte header at the consumer position to recover the address length and the
     * total record length, copies the address bytes and the payload bytes out of the data region into
     * fresh heap arrays (reassembling any record that wraps the boundary), advances the consumer
     * position past the whole record, and hands the address, port, and payload to {@code sink}. The
     * port is decoded from the header's two little-endian port bytes. Returns {@code true} when a
     * record was delivered and {@code false} when the ring was empty. The bytes are copied before the
     * read index advances, so the producer may immediately reuse the freed space once this returns.
     *
     * @param sink the sink receiving the decoded datagram; never {@code null}
     * @return {@code true} if a record was delivered, {@code false} if the ring was empty
     * @throws NullPointerException if {@code sink} is {@code null}
     */
    public boolean poll(FrameSink sink) {
        Objects.requireNonNull(sink, "sink cannot be null");
        var readIdx = Integer.toUnsignedLong((int) INDEX_HANDLE.get(segment, READ_INDEX_OFFSET));
        var writeIdx = Integer.toUnsignedLong((int) INDEX_HANDLE.getAcquire(segment, WRITE_INDEX_OFFSET));
        if (readIdx == writeIdx) {
            return false;
        }
        var addrLen = getByte(readIdx) & 0xFF;
        var port = (getByte(advance(readIdx, 1)) & 0xFF) | ((getByte(advance(readIdx, 2)) & 0xFF) << 8);
        var totalLen = (getByte(advance(readIdx, 3)) & 0xFF) | ((getByte(advance(readIdx, 4)) & 0xFF) << 8);
        var payloadLen = totalLen - addrLen - HEADER_SIZE;
        var address = readWrapped(advance(readIdx, HEADER_SIZE), addrLen);
        var payload = readWrapped(advance(readIdx, HEADER_SIZE + addrLen), payloadLen);
        var nextRead = advance(readIdx, totalLen);
        INDEX_HANDLE.setRelease(segment, READ_INDEX_OFFSET, (int) nextRead);
        sink.accept(address, port, payload);
        return true;
    }

    /**
     * Drains every currently available record from the ring, delivering each to the sink in order.
     *
     * <p>Repeatedly calls {@link #poll(FrameSink)} until the ring is empty, returning the number of
     * records delivered. Records the producer enqueues while this method runs may or may not be
     * drained in the same call; the loop stops the first time the ring is observed empty.
     *
     * @param sink the sink receiving each decoded datagram; never {@code null}
     * @return the number of records delivered to {@code sink}
     * @throws NullPointerException if {@code sink} is {@code null}
     */
    public int drain(FrameSink sink) {
        Objects.requireNonNull(sink, "sink cannot be null");
        var drained = 0;
        while (poll(sink)) {
            drained++;
        }
        return drained;
    }

    /**
     * Returns whether the ring is installed and accepting writes.
     *
     * <p>True from construction until {@link #close()} clears it; a write attempted while this is
     * false is rejected.
     *
     * @return {@code true} if the ring is initialized, {@code false} once it has been closed
     */
    public boolean isInitialized() {
        return initialized;
    }

    /**
     * Returns the capacity of the circular data region in bytes.
     *
     * <p>The largest enqueueable record is one byte smaller than this value, since the ring always
     * leaves one byte free to distinguish a full ring from an empty one.
     *
     * @return the data-region capacity in bytes
     */
    public int capacity() {
        return capacity;
    }

    /**
     * Returns the off-heap segment backing the ring.
     *
     * <p>The segment is laid out exactly as the native ring expects, with the write and read indices
     * in its first eight bytes and the data region after them, so it may be handed to a native
     * producer that writes records in the same format this class reads.
     *
     * @return the backing segment
     */
    public MemorySegment segment() {
        return segment;
    }

    /**
     * Releases the off-heap backing store and marks the ring uninstalled.
     *
     * <p>Clears the initialized flag so further writes are rejected, then closes the owning
     * {@link Arena}, freeing the ring segment and the lock byte. After this returns the segment must
     * not be accessed; calling close again has no effect.
     */
    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        initialized = false;
        arena.close();
    }

    /**
     * Computes the number of free bytes in the data region for the given index positions.
     *
     * <p>Evaluates the native expression {@code (readIdx + ~writeIdx + cap) % cap}, which equals
     * {@code (readIdx - writeIdx - 1 + cap) mod cap}; the subtracted one keeps a single byte free so
     * that an empty ring (equal indices) reports {@code cap - 1} free rather than a full {@code cap},
     * disambiguating full from empty. The sum is masked to thirty-two bits before the modulo so the
     * computation matches the native unsigned 32-bit arithmetic for any capacity: without the mask the
     * {@code ~writeIdx} term ({@code 0xFFFFFFFF} when {@code writeIdx} is zero) would reduce to the
     * wrong residue for a capacity that does not divide {@code 2^32}, that is, any non-power-of-two
     * capacity.
     *
     * @param writeIdx the producer head, as an unsigned value in {@code [0, cap)}
     * @param readIdx  the consumer tail, as an unsigned value in {@code [0, cap)}
     * @return the number of bytes that may be written without overwriting unread data
     */
    private long freeSpace(long writeIdx, long readIdx) {
        var notWrite = (~writeIdx) & 0xFFFFFFFFL;
        var sum = (readIdx + notWrite + capacity) & 0xFFFFFFFFL;
        return Long.remainderUnsigned(sum, capacity);
    }

    /**
     * Advances a data-region index by a delta, wrapping modulo the capacity.
     *
     * <p>Used to step over header bytes, address bytes, and payload bytes and to move the write and
     * read indices to the start of the next record.
     *
     * @param index the current index into the data region, in {@code [0, cap)}
     * @param delta the number of bytes to advance by, non-negative
     * @return the advanced index, in {@code [0, cap)}
     */
    private long advance(long index, long delta) {
        var sum = index + delta;
        return sum < capacity ? sum : sum % capacity;
    }

    /**
     * Writes a single byte into the data region at the given index.
     *
     * <p>The index is relative to the start of the data region; the method offsets it past the two
     * index words to address the byte within the backing segment.
     *
     * @param index the data-region index to write, in {@code [0, cap)}
     * @param value the byte to store
     */
    private void putByte(long index, byte value) {
        segment.set(ValueLayout.JAVA_BYTE, DATA_OFFSET + index, value);
    }

    /**
     * Reads a single byte from the data region at the given index.
     *
     * <p>The index is relative to the start of the data region; the method offsets it past the two
     * index words to address the byte within the backing segment.
     *
     * @param index the data-region index to read, in {@code [0, cap)}
     * @return the byte stored at the index
     */
    private byte getByte(long index) {
        return segment.get(ValueLayout.JAVA_BYTE, DATA_OFFSET + index);
    }

    /**
     * Copies bytes from a source segment into the data region starting at the given index, wrapping
     * at the boundary.
     *
     * <p>When the run from {@code index} to the end of the data region is long enough, the bytes are
     * copied in one block; otherwise the copy is split into the run up to the boundary and the
     * remainder from index zero. The source is read from its own offset zero.
     *
     * @param index  the data-region index at which to start writing, in {@code [0, cap)}
     * @param source the segment to copy bytes from, read from offset zero
     * @param length the number of bytes to copy
     */
    private void writeWrapped(long index, MemorySegment source, int length) {
        var firstRun = Math.min((long) length, capacity - index);
        MemorySegment.copy(source, 0L, segment, DATA_OFFSET + index, firstRun);
        if (firstRun < length) {
            MemorySegment.copy(source, firstRun, segment, DATA_OFFSET, length - firstRun);
        }
    }

    /**
     * Copies bytes out of the data region starting at the given index into a fresh heap array,
     * wrapping at the boundary.
     *
     * <p>When the run from {@code index} to the end of the data region covers the whole length, the
     * bytes are copied in one block; otherwise the copy is split into the run up to the boundary and
     * the remainder from index zero. The returned array is detached from the off-heap ring, so the
     * producer may reuse the bytes once the read index advances.
     *
     * @param index  the data-region index at which to start reading, in {@code [0, cap)}
     * @param length the number of bytes to copy
     * @return a new array of {@code length} bytes read from the data region
     */
    private byte[] readWrapped(long index, int length) {
        var out = new byte[length];
        var firstRun = (int) Math.min((long) length, capacity - index);
        MemorySegment.copy(segment, ValueLayout.JAVA_BYTE, DATA_OFFSET + index, out, 0, firstRun);
        if (firstRun < length) {
            MemorySegment.copy(segment, ValueLayout.JAVA_BYTE, DATA_OFFSET, out, firstRun, length - firstRun);
        }
        return out;
    }

    /**
     * Acquires the producer spinlock, busy-waiting until the lock byte is free.
     *
     * <p>Repeatedly attempts a compare-and-set of the lock cell from {@link #LOCK_FREE} to
     * {@link #LOCK_HELD}, issuing a {@link Thread#onSpinWait()} spin hint on each failed attempt;
     * returns once the lock is held. The lock is held only across a single record write, so
     * contention is brief.
     */
    private void acquireLock() {
        while (!LOCK_HANDLE.compareAndSet(lock, 0L, LOCK_FREE, LOCK_HELD)) {
            Thread.onSpinWait();
        }
    }

    /**
     * Releases the producer spinlock.
     *
     * <p>Stores {@link #LOCK_FREE} into the lock cell with release ordering so the record bytes and
     * the advanced write index written under the lock are visible to the next producer that acquires
     * it.
     */
    private void releaseLock() {
        LOCK_HANDLE.setRelease(lock, 0L, LOCK_FREE);
    }

    /**
     * Receives one datagram drained from the ring.
     *
     * <p>A sink is handed the address bytes, the port index, and the payload bytes of a single
     * record by {@link SctpRingBuffer#poll(FrameSink)} and {@link SctpRingBuffer#drain(FrameSink)}.
     * Both arrays are freshly allocated and owned by the sink. The typical sink forwards the payload
     * to a datagram socket addressed by the recovered address and port.
     *
     * @apiNote A sink runs on the consumer thread inside the drain call, so it should hand the
     * datagram off promptly rather than block; a slow sink delays draining and lets the ring fill.
     */
    @FunctionalInterface
    public interface FrameSink {
        /**
         * Accepts one drained datagram.
         *
         * <p>The address and payload arrays are owned by the sink and may be retained or mutated. The
         * port carries the 16-bit index recorded by the producer.
         *
         * @param address the raw sockaddr bytes recorded ahead of the payload; never {@code null}
         * @param port    the 16-bit address or port index recorded with the datagram
         * @param payload the datagram bytes; never {@code null}
         */
        void accept(byte[] address, int port, byte[] payload);
    }
}
