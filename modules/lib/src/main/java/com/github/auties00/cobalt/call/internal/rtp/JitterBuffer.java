package com.github.auties00.cobalt.call.internal.rtp;

import java.util.TreeMap;

/**
 * A small fixed-capacity reorder + miss-detection buffer for inbound
 * RTP packets, keyed on 16-bit sequence numbers.
 *
 * <p>The buffer holds up to {@link #capacity} most-recent packets in
 * a {@link TreeMap} sorted by sequence number. {@link #poll()} drains
 * the next-in-order packet; {@link #pollMissing()} reports a gap of
 * lost packets so the codec can run packet-loss concealment.
 *
 * <p>Sequence-number wraparound is handled by interpreting the 16-bit
 * sequence number as a signed-distance from the last emitted: a packet
 * whose seq is "before" the last emitted (mod 65536) is rejected as
 * late, while a packet whose seq is "after" by more than the capacity
 * window forces eviction of older packets.
 *
 * <p>Threading: not safe for concurrent use. The receiver drives all
 * mutating calls from a single thread.
 */
public final class JitterBuffer {
    /**
     * Maximum number of packets the buffer holds before it starts
     * dropping the oldest entries to make room.
     */
    private final int capacity;

    /**
     * Packet-loss concealment threshold: if {@link #poll()} sees a
     * gap of more than this many sequence numbers, it surfaces the
     * gap as a missing-packet signal so the decoder can run PLC.
     */
    private final int maxGap;

    /**
     * In-order buffer keyed by extended sequence number (the
     * 32-bit unwrapped seq we get by tracking wraparounds).
     */
    private final TreeMap<Long, RtpPacket> buffered = new TreeMap<>();

    /**
     * The extended sequence number of the last packet emitted by
     * {@link #poll()} or {@link #pollMissing()}, or {@code -1L} if
     * nothing has been emitted yet.
     */
    private long lastEmittedSeq = -1L;

    /**
     * The 16-bit ROC (rollover counter) — number of times the
     * 16-bit sequence has wrapped since the stream started. Combined
     * with the 16-bit seq it gives the "extended" 32-bit seq used
     * internally.
     */
    private int rolloverCounter;

    /**
     * The high-watermark 16-bit sequence number observed —
     * {@code -1} until the first packet arrives.
     */
    private int highestSeq = -1;

    /**
     * Constructs a new jitter buffer.
     *
     * @param capacity the maximum number of buffered packets
     * @param maxGap   the largest run of missing packets the buffer
     *                 will tolerate before {@link #pollMissing()}
     *                 surfaces a PLC trigger
     * @throws IllegalArgumentException if {@code capacity} or
     *                                  {@code maxGap} is &lt; 1
     */
    public JitterBuffer(int capacity, int maxGap) {
        if (capacity < 1) {
            throw new IllegalArgumentException("capacity must be ≥ 1");
        }
        if (maxGap < 1) {
            throw new IllegalArgumentException("maxGap must be ≥ 1");
        }
        this.capacity = capacity;
        this.maxGap = maxGap;
    }

    /**
     * Returns the configured capacity.
     *
     * @return the capacity
     */
    public int capacity() {
        return capacity;
    }

    /**
     * Returns the configured maximum gap.
     *
     * @return the max gap
     */
    public int maxGap() {
        return maxGap;
    }

    /**
     * Returns the current number of buffered packets.
     *
     * @return the size
     */
    public int size() {
        return buffered.size();
    }

    /**
     * Adds one inbound packet to the buffer. The packet is keyed on
     * its extended sequence number (16-bit wire seq + ROC) so
     * wraparounds are handled. Late packets (older than the last
     * emitted) are silently dropped. Packets that would exceed
     * {@link #capacity} evict the oldest buffered entry.
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
     * Drains the next-in-order packet, or returns {@code null} when
     * the buffer is empty or the next-expected sequence number is
     * not yet present.
     *
     * <p>Behaviour:
     *
     * <ul>
     *   <li>If the buffer is empty, returns {@code null}.</li>
     *   <li>If the lowest-buffered seq equals
     *       {@code lastEmittedSeq + 1}, removes and returns it.</li>
     *   <li>If the lowest-buffered seq is &gt;
     *       {@code lastEmittedSeq + 1} (a gap), this method returns
     *       {@code null} — the caller can call
     *       {@link #pollMissing()} to advance over the gap with PLC.</li>
     *   <li>For the very first packet (no prior emission), returns
     *       the lowest buffered packet unconditionally.</li>
     * </ul>
     *
     * @return the next ordered packet, or {@code null} if waiting
     *         for in-order delivery
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
     * Returns the number of consecutive missing packets between
     * {@code lastEmittedSeq} and the lowest buffered packet — or
     * {@code 0} when there's no gap or the gap is larger than
     * {@link #maxGap()}.
     *
     * <p>If the gap exceeds {@link #maxGap()}, the buffer fast-
     * forwards {@code lastEmittedSeq} so the next {@link #poll()}
     * returns the first buffered packet and the lost packets are
     * effectively dropped.
     *
     * @return the gap length, or {@code 0}
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
            // Beyond what we'll conceal — fast-forward, drop them.
            lastEmittedSeq = entry.getKey() - 1;
            return 0;
        }
        // Advance one step into the gap; the caller will run PLC
        // for the missing packet at lastEmittedSeq+1.
        lastEmittedSeq++;
        return (int) gap;
    }

    /**
     * Returns whether the next-in-order packet is immediately
     * available (i.e. {@link #poll()} would return non-null).
     *
     * @return {@code true} if there's an in-order packet ready
     */
    public boolean hasNext() {
        var entry = buffered.firstEntry();
        if (entry == null) {
            return false;
        }
        return lastEmittedSeq < 0 || entry.getKey() == lastEmittedSeq + 1;
    }

    /**
     * Maps a 16-bit wire sequence number to a 32-bit extended
     * sequence number that doesn't wrap, by tracking the rollover
     * counter against the highest-seen seq. RFC 3711 §3.3.1 uses the
     * same scheme for SRTP replay protection.
     *
     * @param wireSeq the 16-bit sequence number from the packet
     * @return the extended sequence
     */
    private long extendSequence(int wireSeq) {
        if (highestSeq < 0) {
            highestSeq = wireSeq;
            return ((long) rolloverCounter << 16) | wireSeq;
        }
        var delta = wireSeq - highestSeq;
        if (delta > 0x8000) {
            // Wire seq jumped backwards by more than half the space
            // — peer wrapped the counter once and we're seeing a
            // packet from before the wrap.
            return ((long) (rolloverCounter - 1) << 16) | wireSeq;
        }
        if (delta < -0x8000) {
            // Wire seq jumped forwards by more than half the space
            // — peer wrapped the counter once.
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
