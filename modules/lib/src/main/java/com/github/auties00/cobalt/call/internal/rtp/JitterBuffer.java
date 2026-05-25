package com.github.auties00.cobalt.call.internal.rtp;

import java.util.TreeMap;

/**
 * Reorders inbound {@link RtpPacket}s by sequence number and detects gaps so the codec can run
 * packet-loss concealment.
 *
 * <p>The buffer holds up to {@code capacity} of the most recent packets in a {@link TreeMap} keyed
 * by an extended (32-bit, non-wrapping) sequence number. {@link #poll()} drains the next in-order
 * packet; {@link #pollMissing()} reports a run of lost packets so the decoder can conceal them.
 * Sequence-number wraparound of the 16-bit wire field is absorbed by mapping each wire sequence to
 * an extended sequence via a rollover counter: a packet whose extended sequence is at or below the
 * last emitted is rejected as late, and a packet that would push the buffer past {@code capacity}
 * evicts the oldest buffered entry.
 *
 * <p>This type is not safe for concurrent use; the receiver drives every mutating call from a
 * single thread.
 */
public final class JitterBuffer {
    /**
     * Maximum number of packets retained before the oldest entries are evicted to make room.
     */
    private final int capacity;

    /**
     * Largest run of consecutive missing packets that {@link #pollMissing()} surfaces as a
     * packet-loss-concealment trigger; longer gaps are skipped rather than concealed.
     */
    private final int maxGap;

    /**
     * In-order buffer keyed by extended (32-bit, non-wrapping) sequence number.
     */
    private final TreeMap<Long, RtpPacket> buffered = new TreeMap<>();

    /**
     * Extended sequence number of the last packet emitted by {@link #poll()} or
     * {@link #pollMissing()}, or {@code -1L} before anything has been emitted.
     */
    private long lastEmittedSeq = -1L;

    /**
     * Number of times the 16-bit wire sequence has wrapped since the stream started; combined with
     * the wire sequence it yields the extended 32-bit sequence used as the buffer key.
     */
    private int rolloverCounter;

    /**
     * Highest 16-bit wire sequence number observed, or {@code -1} before the first packet arrives.
     */
    private int highestSeq = -1;

    /**
     * Constructs a jitter buffer with the given capacity and concealment bound.
     *
     * @param capacity the maximum number of buffered packets
     * @param maxGap   the largest run of missing packets surfaced for concealment by
     *                 {@link #pollMissing()} before the gap is skipped instead
     * @throws IllegalArgumentException if {@code capacity} or {@code maxGap} is less than {@code 1}
     */
    public JitterBuffer(int capacity, int maxGap) {
        if (capacity < 1) {
            throw new IllegalArgumentException("capacity must be >= 1");
        }
        if (maxGap < 1) {
            throw new IllegalArgumentException("maxGap must be >= 1");
        }
        this.capacity = capacity;
        this.maxGap = maxGap;
    }

    /**
     * Returns the configured capacity.
     *
     * @return the maximum number of buffered packets
     */
    public int capacity() {
        return capacity;
    }

    /**
     * Returns the configured maximum concealable gap.
     *
     * @return the largest run of missing packets surfaced by {@link #pollMissing()}
     */
    public int maxGap() {
        return maxGap;
    }

    /**
     * Returns the number of packets currently buffered.
     *
     * @return the buffer size
     */
    public int size() {
        return buffered.size();
    }

    /**
     * Adds one inbound packet to the buffer.
     *
     * <p>The packet is keyed on its extended sequence number, so 16-bit wraparound is absorbed. A
     * packet whose extended sequence is at or below {@link #poll() the last emitted sequence} is
     * silently dropped as late. When the buffer would exceed {@code capacity}, the oldest buffered
     * entry is evicted.
     *
     * @param packet the inbound packet
     */
    public void offer(RtpPacket packet) {
        var wireSeq = packet.sequenceNumber();
        var extendedSeq = extendSequence(wireSeq);
        if (lastEmittedSeq >= 0 && extendedSeq <= lastEmittedSeq) {
            return;
        }
        buffered.put(extendedSeq, packet);
        while (buffered.size() > capacity) {
            buffered.pollFirstEntry();
        }
    }

    /**
     * Drains the next in-order packet, or returns {@code null} when one is not yet available.
     *
     * <p>The outcome depends on the lowest buffered sequence relative to the last emitted one:
     *
     * <ul>
     *   <li>An empty buffer returns {@code null}.</li>
     *   <li>A lowest sequence equal to {@code lastEmittedSeq + 1} is removed and returned.</li>
     *   <li>A lowest sequence greater than {@code lastEmittedSeq + 1} (a gap) returns
     *       {@code null}; the caller advances over the gap via {@link #pollMissing()}.</li>
     *   <li>The first packet of the stream (nothing emitted yet) is returned unconditionally.</li>
     * </ul>
     *
     * @return the next in-order packet, or {@code null} while waiting for in-order delivery
     */
    public RtpPacket poll() {
        var entry = buffered.firstEntry();
        if (entry == null) {
            return null;
        }
        if (lastEmittedSeq < 0 || entry.getKey() == lastEmittedSeq + 1) {
            buffered.pollFirstEntry();
            lastEmittedSeq = entry.getKey();
            return entry.getValue();
        }
        return null;
    }

    /**
     * Reports the run of consecutive missing packets between the last emitted sequence and the
     * lowest buffered packet, advancing one step into the gap.
     *
     * <p>Returns the gap length when it is between {@code 1} and {@link #maxGap()} inclusive,
     * after advancing the last-emitted sequence by one so the caller can run concealment for the
     * packet now at that position. Returns {@code 0} when the buffer is empty, nothing has been
     * emitted yet, or there is no gap. When the gap exceeds {@link #maxGap()}, the last-emitted
     * sequence is fast-forwarded to just before the lowest buffered packet so the next
     * {@link #poll()} returns it, the skipped packets are dropped, and {@code 0} is returned.
     *
     * @return the gap length in packets, or {@code 0}
     */
    public int pollMissing() {
        var entry = buffered.firstEntry();
        if (entry == null || lastEmittedSeq < 0) {
            return 0;
        }
        var gap = entry.getKey() - lastEmittedSeq - 1;
        if (gap <= 0) {
            return 0;
        }
        if (gap > maxGap) {
            lastEmittedSeq = entry.getKey() - 1;
            return 0;
        }
        lastEmittedSeq++;
        return (int) gap;
    }

    /**
     * Returns whether {@link #poll()} would currently return a non-null packet.
     *
     * @return {@code true} if an in-order packet is ready
     */
    public boolean hasNext() {
        var entry = buffered.firstEntry();
        if (entry == null) {
            return false;
        }
        return lastEmittedSeq < 0 || entry.getKey() == lastEmittedSeq + 1;
    }

    /**
     * Maps a 16-bit wire sequence number to a non-wrapping 32-bit extended sequence number.
     *
     * <p>The first wire sequence seeds {@link #highestSeq} and is extended with the current
     * rollover counter. Subsequent sequences are compared against {@link #highestSeq}: a backward
     * jump of more than half the 16-bit space is treated as a late packet from before the most
     * recent wrap (extended with {@code rolloverCounter - 1}), a forward jump of more than half the
     * space is treated as a fresh wrap (incrementing {@code rolloverCounter} and {@link #highestSeq}),
     * and any other forward movement advances {@link #highestSeq}. The extended value is the
     * rollover counter shifted left 16 bits, OR-ed with the wire sequence.
     *
     * @param wireSeq the 16-bit sequence number carried by the packet
     * @return the extended, non-wrapping sequence number
     * @implNote This implementation reuses the SRTP rollover-counter scheme of RFC 3711 section
     * 3.3.1: the half-space threshold {@code 0x8000} distinguishes a wrap from ordinary reordering,
     * and the 16-bit left shift packs the rollover counter into the high half of the 32-bit
     * extended sequence.
     */
    private long extendSequence(int wireSeq) {
        if (highestSeq < 0) {
            highestSeq = wireSeq;
            return ((long) rolloverCounter << 16) | wireSeq;
        }
        var delta = wireSeq - highestSeq;
        if (delta > 0x8000) {
            return ((long) (rolloverCounter - 1) << 16) | wireSeq;
        }
        if (delta < -0x8000) {
            rolloverCounter++;
            highestSeq = wireSeq;
            return ((long) rolloverCounter << 16) | wireSeq;
        }
        if (wireSeq > highestSeq) {
            highestSeq = wireSeq;
        }
        return ((long) rolloverCounter << 16) | wireSeq;
    }
}
