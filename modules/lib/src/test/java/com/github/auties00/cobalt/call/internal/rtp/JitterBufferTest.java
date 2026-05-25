package com.github.auties00.cobalt.call.internal.rtp;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Covers {@link JitterBuffer} in-order delivery, reordering, gap detection that drives packet
 * loss concealment, drop-on-late, capacity overflow eviction, and 16-bit sequence-number
 * wraparound. Fixtures carry only a sequence number; timestamp, SSRC, and payload are zeroed
 * because the buffer orders solely on sequence.
 */
public class JitterBufferTest {

    private static RtpPacket pkt(int seq) {
        return new RtpPacket(false, 0, seq & 0xFFFF, 0L, 0, new byte[0]);
    }

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

    @Test
    public void smallGapTriggersPlc() {
        var buf = new JitterBuffer(8, 3);
        buf.offer(pkt(100));
        buf.offer(pkt(103));  // gap of 2 (101, 102 missing)
        assertEquals(100, buf.poll().sequenceNumber());
        assertNull(buf.poll());
        assertEquals(2, buf.pollMissing());
        assertNull(buf.poll());
        assertEquals(1, buf.pollMissing());
        assertEquals(103, buf.poll().sequenceNumber());
    }

    @Test
    public void oversizedGapFastForwards() {
        var buf = new JitterBuffer(64, 3);
        buf.offer(pkt(100));
        buf.offer(pkt(200));  // gap of 99 exceeds maxGap=3, so the buffer fast-forwards
        assertEquals(100, buf.poll().sequenceNumber());
        assertEquals(0, buf.pollMissing());
        assertEquals(200, buf.poll().sequenceNumber());
    }

    @Test
    public void latePacketsAreDropped() {
        var buf = new JitterBuffer(8, 3);
        buf.offer(pkt(100));
        buf.offer(pkt(101));
        buf.poll();
        buf.poll();
        buf.offer(pkt(99));  // late: precedes the last emitted sequence
        assertNull(buf.poll());
    }

    @Test
    public void overflowEvictsOldest() {
        var buf = new JitterBuffer(2, 3);
        buf.offer(pkt(1));
        buf.offer(pkt(2));
        buf.offer(pkt(3));  // capacity 2: evicts the oldest entry, seq 1
        assertEquals(2, buf.size());
        assertEquals(2, buf.poll().sequenceNumber());
        assertEquals(3, buf.poll().sequenceNumber());
    }

    @Test
    public void sequenceWraparoundIsHandled() {
        var buf = new JitterBuffer(8, 3);
        buf.offer(pkt(65534));
        buf.offer(pkt(65535));
        buf.offer(pkt(0));      // 65535 rolls over to 0
        buf.offer(pkt(1));
        assertEquals(65534, buf.poll().sequenceNumber());
        assertEquals(65535, buf.poll().sequenceNumber());
        assertEquals(0, buf.poll().sequenceNumber());
        assertEquals(1, buf.poll().sequenceNumber());
    }

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
