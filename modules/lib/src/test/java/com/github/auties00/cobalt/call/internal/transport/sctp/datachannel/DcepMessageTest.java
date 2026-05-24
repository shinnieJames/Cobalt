package com.github.auties00.cobalt.call.internal.transport.sctp.datachannel;

import com.github.auties00.cobalt.exception.WhatsAppCallException;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.OptionalInt;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pure-Java unit tests for the {@link DcepMessage} codec — verifies
 * RFC 8832 wire-format compliance for both
 * {@link DcepMessage.Open DATA_CHANNEL_OPEN} and
 * {@link DcepMessage.Ack DATA_CHANNEL_ACK} across all six channel
 * types.
 */
public class DcepMessageTest {

    /**
     * The canonical {@code DATA_CHANNEL_ACK} wire form is exactly one
     * byte 0x02.
     */
    @Test
    public void ackEncodesAsSingleByte() {
        var encoded = DcepMessage.Ack.INSTANCE.encode();
        assertArrayEquals(new byte[]{0x02}, encoded);
    }

    /**
     * Decoding a single 0x02 byte yields the {@link DcepMessage.Ack}
     * singleton (record equality holds across {@code new Ack()}
     * instances).
     */
    @Test
    public void ackDecodesFromSingleByte() {
        var decoded = DcepMessage.decode(new byte[]{0x02});
        assertInstanceOf(DcepMessage.Ack.class, decoded);
        assertEquals(DcepMessage.Ack.INSTANCE, decoded);
    }

    /**
     * The {@link DcepMessage.Ack#INSTANCE} singleton is shared across
     * every {@link DcepMessage#decode(byte[])} call — by record
     * equality, not by reference.
     */
    @Test
    public void ackEqualsAcrossInstances() {
        var first = DcepMessage.decode(new byte[]{0x02});
        var second = DcepMessage.decode(new byte[]{0x02});
        assertEquals(first, second);
        assertEquals(first.hashCode(), second.hashCode());
    }

    /**
     * A reliable, ordered open round-trips with the expected
     * channel_type 0x00.
     */
    @Test
    public void reliableOrderedOpenRoundTrips() {
        var original = new DcepMessage.Open(DcepMessage.CHANNEL_RELIABLE,
                256, 0L, "chat", "subprot");
        var encoded = original.encode();
        assertEquals((byte) 0x03, encoded[0]);
        assertEquals((byte) 0x00, encoded[1]);

        var decoded = (DcepMessage.Open) DcepMessage.decode(encoded);
        assertEquals(original, decoded);
        assertFalse(decoded.unordered());
        assertTrue(decoded.maxRetransmits().isEmpty());
        assertTrue(decoded.maxLifetimeMs().isEmpty());
    }

    /**
     * A reliable, unordered open uses channel_type 0x80 and reports
     * unordered.
     */
    @Test
    public void reliableUnorderedOpenChannelTypeIs0x80() {
        var original = new DcepMessage.Open(DcepMessage.CHANNEL_RELIABLE_UNORDERED,
                256, 0L, "x", "");
        var encoded = original.encode();
        assertEquals((byte) 0x80, encoded[1]);
        var decoded = (DcepMessage.Open) DcepMessage.decode(encoded);
        assertTrue(decoded.unordered());
    }

    /**
     * A partial-reliable rexmit channel propagates the
     * reliability parameter as max-retransmits.
     */
    @Test
    public void partialReliableRexmitOpenExposesMaxRetransmits() {
        var original = new DcepMessage.Open(DcepMessage.CHANNEL_PARTIAL_RELIABLE_REXMIT,
                256, 7L, "lossy", "");
        var decoded = (DcepMessage.Open) DcepMessage.decode(original.encode());
        assertEquals(OptionalInt.of(7), decoded.maxRetransmits());
        assertEquals(OptionalInt.empty(), decoded.maxLifetimeMs());
        assertFalse(decoded.unordered());
    }

    /**
     * A partial-reliable timed channel propagates the reliability
     * parameter as max-lifetime, in ms, and the high bit propagates
     * unordered.
     */
    @Test
    public void partialReliableTimedUnorderedOpenExposesLifetime() {
        var original = new DcepMessage.Open(
                DcepMessage.CHANNEL_PARTIAL_RELIABLE_TIMED_UNORDERED,
                256, 250L, "telemetry", "");
        var decoded = (DcepMessage.Open) DcepMessage.decode(original.encode());
        assertEquals(OptionalInt.of(250), decoded.maxLifetimeMs());
        assertEquals(OptionalInt.empty(), decoded.maxRetransmits());
        assertTrue(decoded.unordered());
    }

    /**
     * The {@link DcepMessage#channelType(DataChannelOptions)} mapper
     * picks each of the six wire bytes correctly.
     */
    @Test
    public void channelTypeMappingIsCompleteForAllSixVariants() {
        assertEquals(DcepMessage.CHANNEL_RELIABLE,
                DcepMessage.channelType(DataChannelOptions.reliable()));
        assertEquals(DcepMessage.CHANNEL_RELIABLE_UNORDERED,
                DcepMessage.channelType(DataChannelOptions.reliableUnordered()));
        assertEquals(DcepMessage.CHANNEL_PARTIAL_RELIABLE_REXMIT,
                DcepMessage.channelType(DataChannelOptions.partialReliableByRetransmit(3, true)));
        assertEquals(DcepMessage.CHANNEL_PARTIAL_RELIABLE_REXMIT_UNORDERED,
                DcepMessage.channelType(DataChannelOptions.partialReliableByRetransmit(3, false)));
        assertEquals(DcepMessage.CHANNEL_PARTIAL_RELIABLE_TIMED,
                DcepMessage.channelType(DataChannelOptions.partialReliableByLifetime(500, true)));
        assertEquals(DcepMessage.CHANNEL_PARTIAL_RELIABLE_TIMED_UNORDERED,
                DcepMessage.channelType(DataChannelOptions.partialReliableByLifetime(500, false)));
    }

    /**
     * UTF-8 multi-byte labels and protocols round-trip without
     * mojibake.
     */
    @Test
    public void utf8LabelAndProtocolRoundTrip() {
        var label = "café-streaming";
        var protocol = "v1.protocol-α";
        var original = new DcepMessage.Open(DcepMessage.CHANNEL_RELIABLE,
                512, 0L, label, protocol);
        var encoded = original.encode();
        var decoded = (DcepMessage.Open) DcepMessage.decode(encoded);
        assertEquals(label, decoded.label());
        assertEquals(protocol, decoded.protocol());

        var labelBytes = label.getBytes(StandardCharsets.UTF_8);
        var protocolBytes = protocol.getBytes(StandardCharsets.UTF_8);
        assertEquals(12 + labelBytes.length + protocolBytes.length, encoded.length);
    }

    /**
     * Empty label and empty protocol round-trip — neither length
     * field is required to be non-zero by RFC 8832.
     */
    @Test
    public void emptyLabelAndProtocolRoundTrip() {
        var original = new DcepMessage.Open(DcepMessage.CHANNEL_RELIABLE,
                256, 0L, "", "");
        var decoded = (DcepMessage.Open) DcepMessage.decode(original.encode());
        assertEquals("", decoded.label());
        assertEquals("", decoded.protocol());
    }

    /**
     * Decoding an empty buffer raises a {@link WhatsAppCallException.DataChannel}
     * with a useful message.
     */
    @Test
    public void decodeEmptyPayloadThrows() {
        var ex = assertThrows(WhatsAppCallException.DataChannel.class,
                () -> DcepMessage.decode(new byte[0]));
        assertTrue(ex.getMessage().contains("empty"));
    }

    /**
     * Decoding a buffer with an unknown leading byte raises
     * {@link WhatsAppCallException.DataChannel} naming the byte.
     */
    @Test
    public void decodeUnknownTypeThrows() {
        var ex = assertThrows(WhatsAppCallException.DataChannel.class,
                () -> DcepMessage.decode(new byte[]{(byte) 0xAB}));
        assertTrue(ex.getMessage().contains("ab"));
    }

    /**
     * A header that declares more label/protocol bytes than the
     * payload contains is rejected as truncated.
     */
    @Test
    public void decodeTruncatedOpenThrows() {
        var truncated = new byte[]{
                0x03, 0x00,             // type, channel type
                0x00, 0x00,             // priority
                0x00, 0x00, 0x00, 0x00, // reliability param
                0x00, 0x05,             // labelLen=5, but no bytes follow
                0x00, 0x00              // protocolLen=0
        };
        assertThrows(WhatsAppCallException.DataChannel.class, () -> DcepMessage.decode(truncated));
    }

    /**
     * A type=0x03 buffer shorter than the 12-byte fixed header is
     * rejected.
     */
    @Test
    public void decodeShortOpenHeaderThrows() {
        var tooShort = new byte[]{0x03, 0x00, 0x00, 0x00};
        assertThrows(WhatsAppCallException.DataChannel.class, () -> DcepMessage.decode(tooShort));
    }

    /**
     * The reliability parameter survives values up to 0xFFFFFFFF
     * (treated as unsigned per RFC 8832).
     */
    @Test
    public void reliabilityParameterPreservesFullUnsigned32() {
        var original = new DcepMessage.Open(DcepMessage.CHANNEL_PARTIAL_RELIABLE_REXMIT,
                0, 0xFFFFFFFFL, "", "");
        var decoded = (DcepMessage.Open) DcepMessage.decode(original.encode());
        assertEquals(0xFFFFFFFFL, decoded.reliabilityParameter());
    }

    /**
     * The priority field survives values up to 0xFFFF.
     */
    @Test
    public void priorityPreservesFullUnsigned16() {
        var original = new DcepMessage.Open(DcepMessage.CHANNEL_RELIABLE,
                0xFFFF, 0L, "p", "");
        var decoded = (DcepMessage.Open) DcepMessage.decode(original.encode());
        assertEquals(0xFFFF, decoded.priority());
    }

    /**
     * {@link DcepMessage.Open#from} maps options ↔ channel type +
     * reliability faithfully for the rexmit case.
     */
    @Test
    public void openFromOptionsEncodesRexmit() {
        var options = DataChannelOptions.partialReliableByRetransmit(5, false)
                .withProtocol("rexmit-test");
        var open = DcepMessage.Open.from("losschannel", options);
        assertEquals(DcepMessage.CHANNEL_PARTIAL_RELIABLE_REXMIT_UNORDERED, open.channelType());
        assertEquals(5L, open.reliabilityParameter());
        assertEquals("losschannel", open.label());
        assertEquals("rexmit-test", open.protocol());
    }

    /**
     * {@link DcepMessage.Open#from} sets {@code reliabilityParameter}
     * to 0 for fully reliable channels.
     */
    @Test
    public void openFromOptionsZeroesReliabilityForReliable() {
        var open = DcepMessage.Open.from("plain", DataChannelOptions.reliable());
        assertEquals(0L, open.reliabilityParameter());
        assertEquals(DcepMessage.CHANNEL_RELIABLE, open.channelType());
    }

    /**
     * {@link DcepMessage#decode(byte[])} rejects {@code null}.
     */
    @Test
    public void decodeNullThrows() {
        assertThrows(NullPointerException.class, () -> DcepMessage.decode(null));
    }

    /**
     * The Open record's compact constructor rejects negative priority.
     */
    @Test
    public void openConstructorRejectsNegativePriority() {
        assertThrows(IllegalArgumentException.class, () -> new DcepMessage.Open(
                DcepMessage.CHANNEL_RELIABLE, -1, 0L, "x", ""));
    }

    /**
     * The Open record's compact constructor rejects out-of-range
     * reliability parameter.
     */
    @Test
    public void openConstructorRejectsOutOfRangeReliability() {
        assertThrows(IllegalArgumentException.class, () -> new DcepMessage.Open(
                DcepMessage.CHANNEL_PARTIAL_RELIABLE_REXMIT, 0, -1L, "x", ""));
        assertThrows(IllegalArgumentException.class, () -> new DcepMessage.Open(
                DcepMessage.CHANNEL_PARTIAL_RELIABLE_REXMIT, 0, 1L << 32, "x", ""));
    }

    /**
     * Encoding always allocates a fresh array; mutating one does not
     * affect the next call.
     */
    @Test
    public void ackEncodingReturnsFreshArray() {
        var first = DcepMessage.Ack.INSTANCE.encode();
        first[0] = 0x55;
        var second = DcepMessage.Ack.INSTANCE.encode();
        assertEquals((byte) 0x02, second[0]);
        assertNotNull(second);
    }

    /**
     * The four reliable/unreliable identity helpers behave correctly
     * for both ordered and unordered cases.
     */
    @Test
    public void isUnorderedHelper() {
        assertFalse(DcepMessage.isUnordered(DcepMessage.CHANNEL_RELIABLE));
        assertFalse(DcepMessage.isUnordered(DcepMessage.CHANNEL_PARTIAL_RELIABLE_REXMIT));
        assertFalse(DcepMessage.isUnordered(DcepMessage.CHANNEL_PARTIAL_RELIABLE_TIMED));
        assertTrue(DcepMessage.isUnordered(DcepMessage.CHANNEL_RELIABLE_UNORDERED));
        assertTrue(DcepMessage.isUnordered(DcepMessage.CHANNEL_PARTIAL_RELIABLE_REXMIT_UNORDERED));
        assertTrue(DcepMessage.isUnordered(DcepMessage.CHANNEL_PARTIAL_RELIABLE_TIMED_UNORDERED));
    }

    /**
     * {@link DcepMessage.Ack#INSTANCE} is identity-stable so callers
     * can rely on {@code ==} for fast-path comparisons.
     */
    @Test
    public void ackInstanceIsStable() {
        assertSame(DcepMessage.Ack.INSTANCE, DcepMessage.Ack.INSTANCE);
    }
}
