package com.github.auties00.cobalt.call.internal.rtp;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Tests for {@link JitterBuffer} — verifies in-order delivery, gap
 * detection (PLC trigger), drop-on-late, and 16-bit sequence-number
 * wraparound handling per RFC 3711 §3.3.1.
 */
public class JitterBufferTest {

    /**
     * Builds a fixture {@link RtpPacket} with the given seq —
     * timestamp/SSRC/payload don't matter for the buffer.
     */
    private static RtpPacket pkt(int seq) {
        return new RtpPacket(false, 0, seq & 0xFFFF, 0L, 0, new byte[0]);
    }

    /**
     * Packets arriving in order are emitted in order.
     */
    @Test
    public void inOrderArrivalEmitsInOrder() {
        var buf = new JitterBuffer(8, 3);
        buf.offer(pkt(100));
        buf.offer(pkt(101));
        buf.offer(pkt(102));
        assertEquals(100, buf.poll().sequenceNumber());
        assertEquals(101, buf.poll().sequenceNumber());
        assertEquals(102, buf.poll().sequenceNumber());
        assertNull(buf.poll());
    }

    /**
     * Out-of-order arrival is reordered into in-order delivery.
     */
    @Test
    public void outOfOrderArrivalIsReordered() {
        var buf = new JitterBuffer(8, 3);
        buf.offer(pkt(102));
        buf.offer(pkt(100));
        buf.offer(pkt(101));
        assertEquals(100, buf.poll().sequenceNumber());
        assertEquals(101, buf.poll().sequenceNumber());
        assertEquals(102, buf.poll().sequenceNumber());
    }

    /**
     * A small gap of missing packets is reported via
     * {@link JitterBuffer#pollMissing()}, advancing the buffer one
     * step into the gap so the caller can run PLC.
     */
    @Test
    public void smallGapTriggersPlc() {
        var buf = new JitterBuffer(8, 3);
        buf.offer(pkt(100));
        buf.offer(pkt(103));  // gap of 2 (101, 102 missing)
        assertEquals(100, buf.poll().sequenceNumber());
        // 101 missing → poll() returns null, pollMissing() reports 2.
        assertNull(buf.poll());
        assertEquals(2, buf.pollMissing());
        // After pollMissing advances over 101, poll() still gapped at 102.
        assertNull(buf.poll());
        assertEquals(1, buf.pollMissing());
        // Gap closed — next poll returns 103.
        assertEquals(103, buf.poll().sequenceNumber());
    }

    /**
     * A gap larger than {@code maxGap} causes the buffer to
     * fast-forward and drop the lost packets without firing PLC.
     */
    @Test
    public void oversizedGapFastForwards() {
        var buf = new JitterBuffer(64, 3);
        buf.offer(pkt(100));
        buf.offer(pkt(200));  // gap of 99 (way larger than maxGap=3)
        assertEquals(100, buf.poll().sequenceNumber());
        // pollMissing returns 0 because the gap exceeded the
        // tolerance — the buffer fast-forwarded.
        assertEquals(0, buf.pollMissing());
        // Next poll returns the 200 directly.
        assertEquals(200, buf.poll().sequenceNumber());
    }

    /**
     * Late packets (seq before the last emitted one) are silently
     * dropped.
     */
    @Test
    public void latePacketsAreDropped() {
        var buf = new JitterBuffer(8, 3);
        buf.offer(pkt(100));
        buf.offer(pkt(101));
        buf.poll();  // emits 100
        buf.poll();  // emits 101
        buf.offer(pkt(99));  // late
        assertNull(buf.poll());
    }

    /**
     * The buffer evicts oldest entries when capacity is exceeded.
     */
    @Test
    public void overflowEvictsOldest() {
        var buf = new JitterBuffer(2, 3);
        buf.offer(pkt(1));
        buf.offer(pkt(2));
        buf.offer(pkt(3));  // evicts 1
        assertEquals(2, buf.size());
        assertEquals(2, buf.poll().sequenceNumber());
        assertEquals(3, buf.poll().sequenceNumber());
    }

    /**
     * The 16-bit sequence number rollover at 65535 → 0 is handled
     * via the extended-sequence + ROC scheme; ordering stays
     * consistent across the wrap.
     */
    @Test
    public void sequenceWraparoundIsHandled() {
        var buf = new JitterBuffer(8, 3);
        buf.offer(pkt(65534));
        buf.offer(pkt(65535));
        buf.offer(pkt(0));      // wraps
        buf.offer(pkt(1));
        assertEquals(65534, buf.poll().sequenceNumber());
        assertEquals(65535, buf.poll().sequenceNumber());
        assertEquals(0, buf.poll().sequenceNumber());
        assertEquals(1, buf.poll().sequenceNumber());
    }

    /**
     * {@link JitterBuffer#hasNext()} reflects whether
     * {@link JitterBuffer#poll()} would return non-null.
     */
    @Test
    public void hasNextReflectsPollAvailability() {
        var buf = new JitterBuffer(8, 3);
        assertFalse(buf.hasNext());
        buf.offer(pkt(50));
        assertEquals(true, buf.hasNext());
        var packet = buf.poll();
        assertNotNull(packet);
        assertSame(50, packet.sequenceNumber());
        assertFalse(buf.hasNext());
    }
}
