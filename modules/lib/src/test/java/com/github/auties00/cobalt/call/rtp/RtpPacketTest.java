package com.github.auties00.cobalt.call.rtp;

import com.github.auties00.cobalt.exception.WhatsAppCallException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers the {@link RtpPacket} encoder and decoder against the RFC 3550 wire format: minimal
 * round-trip, marker-bit and full-range field preservation, parsing of inbound packets that
 * carry CSRCs, header extensions, or padding, rejection of malformed input, constructor range
 * validation, and the no-aliasing guarantee on {@code encode}.
 */
public class RtpPacketTest {

    @Test
    public void minimalPacketRoundTrips() {
        var packet = new RtpPacket(false, 111, 12345, 0xDEADBEEFL, 0xCAFEBABE,
                "hello".getBytes());
        var encoded = packet.encode();
        assertEquals(12 + 4 + 5, encoded.length);  // fixed header + empty 0xDEBE extension + payload
        assertEquals((byte) 0x90, encoded[0]);  // V=2, P=0, X=1, CC=0
        assertEquals((byte) 0x6F, encoded[1]);  // M=0, PT=111
        assertEquals((byte) 0xDE, encoded[12]); // extension preamble 0xDEBE
        assertEquals((byte) 0xBE, encoded[13]);
        assertEquals((byte) 0x00, encoded[14]); // extension length = 0 words
        assertEquals((byte) 0x00, encoded[15]);
        var decoded = RtpPacket.decode(encoded);
        assertEquals(packet.marker(), decoded.marker());
        assertEquals(packet.payloadType(), decoded.payloadType());
        assertEquals(packet.sequenceNumber(), decoded.sequenceNumber());
        assertEquals(packet.timestamp(), decoded.timestamp());
        assertEquals(packet.ssrc(), decoded.ssrc());
        assertArrayEquals(packet.payload(), decoded.payload());
    }

    @Test
    public void markerBitRoundTrips() {
        var packet = new RtpPacket(true, 96, 1, 0L, 0, new byte[]{1, 2, 3});
        var decoded = RtpPacket.decode(packet.encode());
        assertTrue(decoded.marker());
        assertEquals(96, decoded.payloadType());
    }

    @Test
    public void timestampPreservesFullUnsigned32() {
        var packet = new RtpPacket(false, 0, 0, 0xFFFFFFFFL, 0, new byte[0]);
        var decoded = RtpPacket.decode(packet.encode());
        assertEquals(0xFFFFFFFFL, decoded.timestamp());
    }

    @Test
    public void sequenceNumberPreservesFullUnsigned16() {
        var packet = new RtpPacket(false, 0, 0xFFFF, 0L, 0, new byte[0]);
        var decoded = RtpPacket.decode(packet.encode());
        assertEquals(0xFFFF, decoded.sequenceNumber());
    }

    @Test
    public void inboundPacketWithCsrcsParses() {
        var bytes = new byte[12 + 8 + 4];          // 2 CSRCs + 4-byte payload
        bytes[0] = (byte) 0x82;                     // V=2, CC=2
        bytes[1] = 0x00;                            // PT=0
        bytes[2] = 0; bytes[3] = 1;                 // seq=1
        bytes[4] = 0; bytes[5] = 0; bytes[6] = 0; bytes[7] = 1;  // ts=1
        bytes[8] = 0; bytes[9] = 0; bytes[10] = 0; bytes[11] = 7; // ssrc=7
        // CSRCs occupy bytes 12..20; their content is irrelevant to the parse
        bytes[20] = 'a'; bytes[21] = 'b'; bytes[22] = 'c'; bytes[23] = 'd';
        var packet = RtpPacket.decode(bytes);
        assertArrayEquals(new byte[]{'a', 'b', 'c', 'd'}, packet.payload());
        assertEquals(7, packet.ssrc());
    }

    @Test
    public void inboundPacketWithExtensionParses() {
        // byte 0 = 0x90: V=2, X=1, CC=0. Extension header is profile (2B) + length (2B, in
        // 32-bit words) + ext data (length*4 B); here length = 1 word = 4 bytes.
        var bytes = new byte[12 + 4 + 4 + 5];  // fixed header + ext header + ext data + payload
        bytes[0] = (byte) 0x90;
        bytes[1] = 0;
        bytes[2] = 0; bytes[3] = 1;
        bytes[4] = 0; bytes[5] = 0; bytes[6] = 0; bytes[7] = 0;
        bytes[8] = 0; bytes[9] = 0; bytes[10] = 0; bytes[11] = 1;
        bytes[12] = (byte) 0xBE; bytes[13] = (byte) 0xDE;  // RFC 8285 one-byte profile marker
        bytes[14] = 0; bytes[15] = 1;                       // extension length = 1 word
        bytes[16] = 1; bytes[17] = 2; bytes[18] = 3; bytes[19] = 4;  // opaque extension data
        bytes[20] = 'h'; bytes[21] = 'i'; bytes[22] = '!';
        bytes[23] = 0; bytes[24] = 0;
        var packet = RtpPacket.decode(bytes);
        assertEquals(5, packet.payload().length);
    }

    @Test
    public void paddingBytesStripped() {
        // byte 0 = 0xA0: V=2, P=1. Payload "ab" followed by 2 padding bytes; the final byte
        // holds the padding count.
        var bytes = new byte[12 + 2 + 2];
        bytes[0] = (byte) 0xA0;
        bytes[1] = 0;
        bytes[12] = 'a'; bytes[13] = 'b';
        bytes[14] = 0; bytes[15] = 2;
        var packet = RtpPacket.decode(bytes);
        assertArrayEquals(new byte[]{'a', 'b'}, packet.payload());
    }

    @Test
    public void truncatedPacketRejected() {
        assertThrows(WhatsAppCallException.Rtp.class, () -> RtpPacket.decode(new byte[8]));
    }

    @Test
    public void wrongVersionRejected() {
        var bytes = new byte[12];
        bytes[0] = 0x40;  // V=1, not the required 2
        assertThrows(WhatsAppCallException.Rtp.class, () -> RtpPacket.decode(bytes));
    }

    @Test
    public void constructorRejectsOutOfRangeFields() {
        assertThrows(IllegalArgumentException.class, () -> new RtpPacket(
                false, 128, 0, 0L, 0, new byte[0]));
        assertThrows(IllegalArgumentException.class, () -> new RtpPacket(
                false, 0, 0x10000, 0L, 0, new byte[0]));
        assertThrows(IllegalArgumentException.class, () -> new RtpPacket(
                false, 0, 0, -1L, 0, new byte[0]));
        assertThrows(IllegalArgumentException.class, () -> new RtpPacket(
                false, 0, 0, 1L << 32, 0, new byte[0]));
    }

    @Test
    public void encodeReturnsFreshArray() {
        var packet = new RtpPacket(false, 96, 0, 0L, 0, new byte[]{0});
        var first = packet.encode();
        first[0] = 0x55;
        var second = packet.encode();
        assertFalse(first[0] == second[0]);
    }
}
