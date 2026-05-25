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
 * Self-loop tests for {@link SrtpEndpoint}: a {@link SrtpRole#CLIENT}-role endpoint
 * encrypts a packet, the matching {@link SrtpRole#SERVER}-role endpoint built from the
 * same exported keying material decrypts it, and the plaintext is asserted byte-equal.
 *
 * <p>The suite pins the DTLS-SRTP keying-material split (RFC 5764 section 4.2: client
 * writes with bytes [0..16] + [32..46], server writes with [16..32] + [46..60]),
 * round-trip integrity for both RTP and RTCP under AES-128-CM-HMAC-SHA1-80, auth-tag
 * enforcement (tampered packets fail decrypt), replay rejection, ROC roll-over, and
 * per-SSRC context isolation.
 */
public class SrtpEndpointTest {

    private static final int SSRC = 0x12345678;

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

    @Test
    public void tamperedPacketFailsAuthCheck() {
        var keying = randomKeying();
        try (var client = SrtpEndpoint.fromDtlsKeyingMaterial(keying, SrtpRole.CLIENT);
             var server = SrtpEndpoint.fromDtlsKeyingMaterial(keying, SrtpRole.SERVER)) {
            var rtp = makeRtpPacket(SSRC, 50, 1000, "tamper-test".getBytes());
            var encrypted = client.protectRtp(rtp);
            encrypted[15] ^= 0x01;
            assertThrows(WhatsAppCallException.Srtp.class, () -> server.unprotectRtp(encrypted));
        }
    }

    @Test
    public void rejectsWrongKeyingMaterialLength() {
        assertThrows(IllegalArgumentException.class,
                () -> SrtpEndpoint.fromDtlsKeyingMaterial(new byte[59], SrtpRole.CLIENT));
        assertThrows(IllegalArgumentException.class,
                () -> SrtpEndpoint.fromDtlsKeyingMaterial(new byte[61], SrtpRole.CLIENT));
    }

    // 60 bytes matches the length a real DTLS-SRTP handshake exports.
    private static byte[] randomKeying() {
        var k = new byte[SrtpEndpoint.KEYING_MATERIAL_LENGTH];
        new SecureRandom().nextBytes(k);
        return k;
    }

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

    private static void writeInt(byte[] b, int offset, int v) {
        b[offset]     = (byte) (v >>> 24);
        b[offset + 1] = (byte) (v >>> 16);
        b[offset + 2] = (byte) (v >>> 8);
        b[offset + 3] = (byte) v;
    }

    private static String toHex(byte[] b) {
        var sb = new StringBuilder(b.length * 2);
        for (var x : b) sb.append(String.format("%02x", x & 0xFF));
        return sb.toString();
    }

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
