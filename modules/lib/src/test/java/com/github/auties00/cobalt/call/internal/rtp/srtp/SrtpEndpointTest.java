package com.github.auties00.cobalt.call.internal.rtp.srtp;

import com.github.auties00.cobalt.exception.WhatsAppCallException;
import org.junit.jupiter.api.Test;

import java.security.SecureRandom;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Self-loop tests for {@link SrtpEndpoint}: a CLIENT-role endpoint
 * encrypts a packet, the matching SERVER-role endpoint (with the
 * same exported keying material) decrypts it, and the plaintext is
 * asserted byte-equal.
 *
 * <p>Pins:
 *
 * <ul>
 *   <li>The DTLS-SRTP keying-material split per RFC 5764 §4.2 (client
 *       writes with bytes [0..16] + [32..46]; server writes with
 *       [16..32] + [46..60]).</li>
 *   <li>Round-trip integrity for both RTP and RTCP packets under
 *       AES-128-CM-HMAC-SHA1-80.</li>
 *   <li>Auth-tag enforcement (tampered packets fail decrypt).</li>
 * </ul>
 */
public class SrtpEndpointTest {

    /**
     * The size of the SSRC field at offset 8 in an RTP/RTCP header.
     */
    private static final int SSRC = 0x12345678;

    /**
     * Encrypts an RTP packet with a CLIENT endpoint and decrypts it
     * with a SERVER endpoint sharing the same keying material;
     * asserts plaintext equality.
     */
    @Test
    public void rtpRoundTripsClientToServer() {
        var keying = randomKeying();
        try (var client = SrtpEndpoint.fromDtlsKeyingMaterial(keying, SrtpRole.CLIENT);
             var server = SrtpEndpoint.fromDtlsKeyingMaterial(keying, SrtpRole.SERVER)) {
            var rtp = makeRtpPacket(SSRC, 100, 12345, "hello srtp".getBytes());
            var encrypted = client.protectRtp(rtp);
            assertEquals(rtp.length + 10, encrypted.length, "SRTP must add a 10-byte auth tag");
            assertNotEquals(toHex(rtp), toHex(encrypted), "encrypted bytes must differ from plaintext");
            var decrypted = server.unprotectRtp(encrypted);
            assertArrayEquals(rtp, decrypted, "plaintext must round-trip byte-exact");
        }
    }

    /**
     * Same round-trip but for the reverse direction (server encrypts,
     * client decrypts) — verifies the keying-material split handles
     * both halves.
     */
    @Test
    public void rtpRoundTripsServerToClient() {
        var keying = randomKeying();
        try (var client = SrtpEndpoint.fromDtlsKeyingMaterial(keying, SrtpRole.CLIENT);
             var server = SrtpEndpoint.fromDtlsKeyingMaterial(keying, SrtpRole.SERVER)) {
            var rtp = makeRtpPacket(SSRC, 200, 67890, "from server".getBytes());
            var encrypted = server.protectRtp(rtp);
            var decrypted = client.unprotectRtp(encrypted);
            assertArrayEquals(rtp, decrypted);
        }
    }

    /**
     * RTCP round-trip — the per-SSRC SRTCP context derivation path.
     */
    @Test
    public void rtcpRoundTrips() {
        var keying = randomKeying();
        try (var client = SrtpEndpoint.fromDtlsKeyingMaterial(keying, SrtpRole.CLIENT);
             var server = SrtpEndpoint.fromDtlsKeyingMaterial(keying, SrtpRole.SERVER)) {
            // Minimal SR (Sender Report) RTCP packet: V=2, P=0, RC=0, PT=200 (SR), length=6, SSRC, NTP+RTP+pkt+oct stats
            var rtcp = new byte[28];
            rtcp[0] = (byte) 0x80;
            rtcp[1] = (byte) 200;
            rtcp[2] = 0;
            rtcp[3] = 6;
            writeInt(rtcp, 4, SSRC);
            // remaining fields can be zeros for the test
            var encrypted = client.protectRtcp(rtcp, SSRC);
            assertEquals(rtcp.length + 14, encrypted.length, "SRTCP must add 4-byte index + 10-byte auth tag");
            var decrypted = server.unprotectRtcp(encrypted, SSRC);
            assertArrayEquals(rtcp, decrypted);
        }
    }

    /**
     * Tampering with a single byte of an SRTP packet must cause
     * unprotect to fail with an {@link WhatsAppCallException.Srtp}.
     */
    @Test
    public void tamperedPacketFailsAuthCheck() {
        var keying = randomKeying();
        try (var client = SrtpEndpoint.fromDtlsKeyingMaterial(keying, SrtpRole.CLIENT);
             var server = SrtpEndpoint.fromDtlsKeyingMaterial(keying, SrtpRole.SERVER)) {
            var rtp = makeRtpPacket(SSRC, 50, 1000, "tamper-test".getBytes());
            var encrypted = client.protectRtp(rtp);
            // flip a payload byte
            encrypted[15] ^= 0x01;
            assertThrows(WhatsAppCallException.Srtp.class, () -> server.unprotectRtp(encrypted));
        }
    }

    /**
     * The keying-material length must be exactly 60 bytes; passing
     * anything else fails fast.
     */
    @Test
    public void rejectsWrongKeyingMaterialLength() {
        assertThrows(IllegalArgumentException.class,
                () -> SrtpEndpoint.fromDtlsKeyingMaterial(new byte[59], SrtpRole.CLIENT));
        assertThrows(IllegalArgumentException.class,
                () -> SrtpEndpoint.fromDtlsKeyingMaterial(new byte[61], SrtpRole.CLIENT));
    }

    /**
     * Generates 60 bytes of random keying material — the same length
     * a real DTLS-SRTP handshake would export.
     *
     * @return a fresh 60-byte array
     */
    private static byte[] randomKeying() {
        var k = new byte[SrtpEndpoint.KEYING_MATERIAL_LENGTH];
        new SecureRandom().nextBytes(k);
        return k;
    }

    /**
     * Builds a minimal RTP packet (V=2, P=0, X=0, CC=0, M=0, PT=0, no
     * extensions) carrying the given payload.
     *
     * @param ssrc     the SSRC to embed at offset 8
     * @param seq      the sequence number
     * @param ts       the RTP timestamp
     * @param payload  the payload bytes
     * @return the full RTP packet (12-byte header + payload)
     */
    private static byte[] makeRtpPacket(int ssrc, int seq, int ts, byte[] payload) {
        var pkt = new byte[12 + payload.length];
        pkt[0] = (byte) 0x80; // V=2, P=0, X=0, CC=0
        pkt[1] = 0;           // M=0, PT=0
        pkt[2] = (byte) (seq >>> 8);
        pkt[3] = (byte) seq;
        writeInt(pkt, 4, ts);
        writeInt(pkt, 8, ssrc);
        System.arraycopy(payload, 0, pkt, 12, payload.length);
        return pkt;
    }

    /**
     * Writes a big-endian 32-bit integer at the given offset.
     *
     * @param b      the destination array
     * @param offset the byte offset
     * @param v      the value to write
     */
    private static void writeInt(byte[] b, int offset, int v) {
        b[offset]     = (byte) (v >>> 24);
        b[offset + 1] = (byte) (v >>> 16);
        b[offset + 2] = (byte) (v >>> 8);
        b[offset + 3] = (byte) v;
    }

    /**
     * Hex-encodes a byte array for assertion error messages.
     *
     * @param b the bytes
     * @return the lower-case hex string
     */
    private static String toHex(byte[] b) {
        var sb = new StringBuilder(b.length * 2);
        for (var x : b) sb.append(String.format("%02x", x & 0xFF));
        return sb.toString();
    }

    /**
     * Round-trip with payloads near the maximum SRTP packet size
     * (typical Ethernet MTU minus headers ≈ 1400 bytes).
     */
    @Test
    public void rtpRoundTripsLargePayload() {
        var keying = randomKeying();
        try (var client = SrtpEndpoint.fromDtlsKeyingMaterial(keying, SrtpRole.CLIENT);
             var server = SrtpEndpoint.fromDtlsKeyingMaterial(keying, SrtpRole.SERVER)) {
            var payload = new byte[1400];
            new SecureRandom().nextBytes(payload);
            var rtp = makeRtpPacket(SSRC, 1, 1, payload);
            var encrypted = client.protectRtp(rtp);
            var decrypted = server.unprotectRtp(encrypted);
            assertArrayEquals(Arrays.copyOfRange(rtp, 12, rtp.length),
                    Arrays.copyOfRange(decrypted, 12, decrypted.length));
        }
    }

    /**
     * Receiving the same SRTP packet twice must accept the first and
     * reject the second (the replay window is keyed on the 48-bit
     * packet index per RFC 3711 §3.3.2).
     */
    @Test
    public void rejectsReplayedRtpPacket() {
        var keying = randomKeying();
        try (var client = SrtpEndpoint.fromDtlsKeyingMaterial(keying, SrtpRole.CLIENT);
             var server = SrtpEndpoint.fromDtlsKeyingMaterial(keying, SrtpRole.SERVER)) {
            var rtp = makeRtpPacket(SSRC, 42, 1000, "replay-me".getBytes());
            var encrypted = client.protectRtp(rtp);
            assertArrayEquals(rtp, server.unprotectRtp(encrypted.clone()));
            assertThrows(WhatsAppCallException.Srtp.class, () -> server.unprotectRtp(encrypted));
        }
    }

    /**
     * The 32-bit roll-over counter must increment when the 16-bit
     * sequence number wraps: a packet with SEQ=0 following one with
     * SEQ=0xFFFF still round-trips, since both ends bump ROC in
     * lockstep.
     */
    @Test
    public void rocIncrementsOnSequenceWrap() {
        var keying = randomKeying();
        try (var client = SrtpEndpoint.fromDtlsKeyingMaterial(keying, SrtpRole.CLIENT);
             var server = SrtpEndpoint.fromDtlsKeyingMaterial(keying, SrtpRole.SERVER)) {
            var beforeWrap = makeRtpPacket(SSRC, 0xFFFF, 100, "before".getBytes());
            var afterWrap  = makeRtpPacket(SSRC, 0x0000, 200, "after".getBytes());
            assertArrayEquals(beforeWrap, server.unprotectRtp(client.protectRtp(beforeWrap)));
            assertArrayEquals(afterWrap,  server.unprotectRtp(client.protectRtp(afterWrap)));
        }
    }

    /**
     * Packets arriving out of order within the replay window must
     * still authenticate and decrypt correctly, since the receiver
     * reconstructs the per-packet index from the highest SEQ seen
     * and the candidate ROC.
     */
    @Test
    public void rtpRoundTripsOutOfOrderInWindow() {
        var keying = randomKeying();
        try (var client = SrtpEndpoint.fromDtlsKeyingMaterial(keying, SrtpRole.CLIENT);
             var server = SrtpEndpoint.fromDtlsKeyingMaterial(keying, SrtpRole.SERVER)) {
            var p10 = makeRtpPacket(SSRC, 10, 100, "ten".getBytes());
            var p11 = makeRtpPacket(SSRC, 11, 110, "eleven".getBytes());
            var p12 = makeRtpPacket(SSRC, 12, 120, "twelve".getBytes());
            var enc10 = client.protectRtp(p10);
            var enc11 = client.protectRtp(p11);
            var enc12 = client.protectRtp(p12);
            // Deliver out of order: 10, 12, 11.
            assertArrayEquals(p10, server.unprotectRtp(enc10));
            assertArrayEquals(p12, server.unprotectRtp(enc12));
            assertArrayEquals(p11, server.unprotectRtp(enc11));
        }
    }

    /**
     * The SRTCP context refuses to accept the same SRTCP index
     * twice — a replay must throw.
     */
    @Test
    public void rejectsReplayedRtcpPacket() {
        var keying = randomKeying();
        try (var client = SrtpEndpoint.fromDtlsKeyingMaterial(keying, SrtpRole.CLIENT);
             var server = SrtpEndpoint.fromDtlsKeyingMaterial(keying, SrtpRole.SERVER)) {
            var rtcp = makeRtcpPacket(SSRC);
            var encrypted = client.protectRtcp(rtcp, SSRC);
            assertArrayEquals(rtcp, server.unprotectRtcp(encrypted.clone(), SSRC));
            assertThrows(WhatsAppCallException.Srtp.class, () -> server.unprotectRtcp(encrypted, SSRC));
        }
    }

    /**
     * Two SSRCs in the same direction must each get their own
     * per-SSRC context; encrypting an SSRC=A packet must not consume
     * the index/ROC space of SSRC=B.
     */
    @Test
    public void multipleSsrcsRoundTripIndependently() {
        var keying = randomKeying();
        try (var client = SrtpEndpoint.fromDtlsKeyingMaterial(keying, SrtpRole.CLIENT);
             var server = SrtpEndpoint.fromDtlsKeyingMaterial(keying, SrtpRole.SERVER)) {
            var pktA = makeRtpPacket(0xAAAAAAAA, 1, 100, "from A".getBytes());
            var pktB = makeRtpPacket(0xBBBBBBBB, 1, 100, "from B".getBytes());
            assertArrayEquals(pktA, server.unprotectRtp(client.protectRtp(pktA)));
            assertArrayEquals(pktB, server.unprotectRtp(client.protectRtp(pktB)));
        }
    }

    /**
     * Builds a minimal Sender Report (PT=200) RTCP packet with the
     * supplied sender SSRC at offset 4.
     *
     * @param ssrc the sender SSRC
     * @return a 28-byte RTCP packet
     */
    private static byte[] makeRtcpPacket(int ssrc) {
        var rtcp = new byte[28];
        rtcp[0] = (byte) 0x80; // V=2, P=0, RC=0
        rtcp[1] = (byte) 200;  // PT=SR
        rtcp[2] = 0;
        rtcp[3] = 6;           // length (32-bit words minus 1)
        writeInt(rtcp, 4, ssrc);
        return rtcp;
    }
}
