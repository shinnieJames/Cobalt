package com.github.auties00.cobalt.call.internal.transport.sctp.datachannel;

import org.junit.jupiter.api.Test;

import java.util.OptionalInt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Validation tests for {@link DataChannelOptions}, covering the factory methods, the
 * mutually-exclusive reliability rule, the negotiated/stream-id pairing, and the with* copy
 * helpers.
 */
public class DataChannelOptionsTest {

    @Test
    public void reliableDefaultsAreOrderedAndFullyReliable() {
        var options = DataChannelOptions.reliable();
        assertTrue(options.ordered());
        assertTrue(options.maxRetransmits().isEmpty());
        assertTrue(options.maxLifetimeMs().isEmpty());
        assertEquals("", options.protocol());
        assertFalse(options.negotiated());
        assertTrue(options.streamId().isEmpty());
        assertEquals(DataChannelOptions.DEFAULT_PRIORITY, options.priority());
    }

    @Test
    public void reliableUnorderedFlipsOrderedFlag() {
        var options = DataChannelOptions.reliableUnordered();
        assertFalse(options.ordered());
        assertTrue(options.maxRetransmits().isEmpty());
        assertTrue(options.maxLifetimeMs().isEmpty());
    }

    @Test
    public void partialReliableByRetransmitSetsRetransmits() {
        var options = DataChannelOptions.partialReliableByRetransmit(3, true);
        assertEquals(OptionalInt.of(3), options.maxRetransmits());
        assertTrue(options.maxLifetimeMs().isEmpty());
        assertTrue(options.ordered());
    }

    @Test
    public void partialReliableByLifetimeSetsLifetime() {
        var options = DataChannelOptions.partialReliableByLifetime(500, false);
        assertEquals(OptionalInt.of(500), options.maxLifetimeMs());
        assertTrue(options.maxRetransmits().isEmpty());
        assertFalse(options.ordered());
    }

    @Test
    public void mutuallyExclusiveReliabilityIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new DataChannelOptions(
                true, OptionalInt.of(3), OptionalInt.of(500),
                "", false, OptionalInt.empty(), DataChannelOptions.DEFAULT_PRIORITY));
    }

    @Test
    public void negativeReliabilityIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new DataChannelOptions(
                true, OptionalInt.of(-1), OptionalInt.empty(),
                "", false, OptionalInt.empty(), DataChannelOptions.DEFAULT_PRIORITY));
        assertThrows(IllegalArgumentException.class, () -> new DataChannelOptions(
                true, OptionalInt.empty(), OptionalInt.of(-1),
                "", false, OptionalInt.empty(), DataChannelOptions.DEFAULT_PRIORITY));
    }

    @Test
    public void negotiatedWithoutStreamIdIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new DataChannelOptions(
                true, OptionalInt.empty(), OptionalInt.empty(),
                "", true, OptionalInt.empty(), DataChannelOptions.DEFAULT_PRIORITY));
    }

    @Test
    public void streamIdOutOfRangeIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new DataChannelOptions(
                true, OptionalInt.empty(), OptionalInt.empty(),
                "", true, OptionalInt.of(-1), DataChannelOptions.DEFAULT_PRIORITY));
        assertThrows(IllegalArgumentException.class, () -> new DataChannelOptions(
                true, OptionalInt.empty(), OptionalInt.empty(),
                "", true, OptionalInt.of(65535), DataChannelOptions.DEFAULT_PRIORITY));
    }

    @Test
    public void priorityOutOfRangeIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new DataChannelOptions(
                true, OptionalInt.empty(), OptionalInt.empty(),
                "", false, OptionalInt.empty(), -1));
        assertThrows(IllegalArgumentException.class, () -> new DataChannelOptions(
                true, OptionalInt.empty(), OptionalInt.empty(),
                "", false, OptionalInt.empty(), 0x10000));
    }

    @Test
    public void withNegotiatedStreamIdFlipsNegotiated() {
        var options = DataChannelOptions.reliable().withNegotiatedStreamId(7);
        assertTrue(options.negotiated());
        assertEquals(OptionalInt.of(7), options.streamId());
    }

    @Test
    public void withProtocolCopies() {
        var options = DataChannelOptions.reliable().withProtocol("v1");
        assertEquals("v1", options.protocol());
    }

    @Test
    public void withPriorityCopies() {
        var options = DataChannelOptions.reliable().withPriority(1024);
        assertEquals(1024, options.priority());
    }
}
