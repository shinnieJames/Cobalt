package com.github.auties00.cobalt.call.internal.transport.dtls;

import com.github.auties00.cobalt.call.internal.rtp.srtp.SrtpEndpoint;
import com.github.auties00.cobalt.call.internal.rtp.srtp.SrtpRole;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.security.SecureRandom;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Round-trip tests for {@link DtlsSrtpEndpoint}: drives a complete
 * DTLS-SRTP handshake between a CLIENT-role and SERVER-role endpoint
 * over a {@link LoopbackTransport} pair, verifies that both sides
 * derive {@link SrtpEndpoint}s primed with matching keying material,
 * and exercises the full SRTP round-trip on top.
 */
public class DtlsSrtpEndpointTest {

    /**
     * Generates self-signed certs for both peers, exchanges
     * fingerprints, drives the handshake on two threads, and
     * verifies the resulting SRTP endpoints can encrypt/decrypt
     * each other's packets.
     *
     * @throws Exception on any handshake or transport failure
     */
    @Test
    public void clientServerHandshakeProducesInteroperableSrtpEndpoints() throws Exception {
        var clientCert = DtlsCertificate.generate();
        var serverCert = DtlsCertificate.generate();
        var pair = LoopbackTransport.pair();

        var client = DtlsSrtpEndpoint.client(clientCert, serverCert.sha256Fingerprint(), pair[0]);
        var server = DtlsSrtpEndpoint.server(serverCert, clientCert.sha256Fingerprint(), pair[1]);

        SrtpEndpoint clientSrtp;
        SrtpEndpoint serverSrtp;
        try {
            var clientFuture = CompletableFuture.supplyAsync(() -> {
                try { return client.handshake(); } catch (IOException e) { throw new RuntimeException(e); }
            });
            var serverFuture = CompletableFuture.supplyAsync(() -> {
                try { return server.handshake(); } catch (IOException e) { throw new RuntimeException(e); }
            });
            clientSrtp = clientFuture.get(15, TimeUnit.SECONDS);
            serverSrtp = serverFuture.get(15, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            throw new AssertionError("DTLS handshake did not complete within 15s", e);
        }

        assertNotNull(clientSrtp);
        assertNotNull(serverSrtp);

        // Round-trip an RTP packet through the negotiated SRTP keys.
        try (clientSrtp; serverSrtp) {
            var rtp = makeRtpPacket(0xCAFEBABE, 1, 1, "dtls-srtp ok".getBytes());
            var encrypted = clientSrtp.protectRtp(rtp);
            assertEquals(rtp.length + 10, encrypted.length);
            assertNotEquals(toHex(rtp), toHex(encrypted));
            var decrypted = serverSrtp.unprotectRtp(encrypted);
            assertArrayEquals(rtp, decrypted);

            // Reverse direction
            var rtp2 = makeRtpPacket(0xDEADBEEF, 2, 2, "and back".getBytes());
            var encrypted2 = serverSrtp.protectRtp(rtp2);
            var decrypted2 = clientSrtp.unprotectRtp(encrypted2);
            assertArrayEquals(rtp2, decrypted2);
        }
    }

    /**
     * The handshake must reject a peer whose fingerprint doesn't
     * match the one advertised via signaling.
     */
    @Test
    public void mismatchedFingerprintFailsHandshake() {
        var clientCert = DtlsCertificate.generate();
        var serverCert = DtlsCertificate.generate();
        var pair = LoopbackTransport.pair();

        // Tamper with the fingerprint the client expects
        var wrongFingerprint = serverCert.sha256Fingerprint();
        wrongFingerprint[0] ^= 0x01;

        var client = DtlsSrtpEndpoint.client(clientCert, wrongFingerprint, pair[0]);
        var server = DtlsSrtpEndpoint.server(serverCert, clientCert.sha256Fingerprint(), pair[1]);

        var clientFuture = CompletableFuture.supplyAsync(() -> {
            try { return client.handshake(); } catch (IOException e) { throw new RuntimeException(e); }
        });
        var serverFuture = CompletableFuture.supplyAsync(() -> {
            try { return server.handshake(); } catch (IOException e) { throw new RuntimeException(e); }
        });

        // At least one side must fail. Most likely the client fails first
        // (it validates the server cert during the handshake).
        var clientFailed = awaitFailure(clientFuture);
        var serverFailed = awaitFailure(serverFuture);
        assertTrue(clientFailed || serverFailed, "expected at least one side to fail the handshake");
    }

    /**
     * The constructor rejects fingerprints that aren't 32 bytes
     * (SHA-256 size).
     */
    @Test
    public void rejectsWrongFingerprintLength() {
        var cert = DtlsCertificate.generate();
        var pair = LoopbackTransport.pair();
        assertThrows(IllegalArgumentException.class,
                () -> DtlsSrtpEndpoint.client(cert, new byte[16], pair[0]));
        assertThrows(IllegalArgumentException.class,
                () -> DtlsSrtpEndpoint.server(cert, new byte[33], pair[1]));
    }

    /**
     * Cert generation produces unique certs each call.
     */
    @Test
    public void generatedCertsAreUnique() {
        var a = DtlsCertificate.generate();
        var b = DtlsCertificate.generate();
        assertNotEquals(toHex(a.sha256Fingerprint()), toHex(b.sha256Fingerprint()));
        assertEquals(32, a.sha256Fingerprint().length);
    }

    /**
     * Fingerprint hex string is 95 chars (32 bytes × 2 hex + 31
     * colons), upper-case, colon-separated.
     */
    @Test
    public void fingerprintHexFormat() {
        var cert = DtlsCertificate.generate();
        var hex = cert.fingerprintHex();
        assertEquals(95, hex.length(), "expected 32×2 + 31 colons = 95 chars");
        assertTrue(hex.matches("[0-9A-F]{2}(:[0-9A-F]{2}){31}"),
                "unexpected format: " + hex);
    }

    /**
     * Awaits a {@link CompletableFuture} that's expected to fail and
     * returns true iff it did.
     *
     * @param fut the future to wait on
     * @return {@code true} if the future completed exceptionally
     *         within 15s
     */
    private static boolean awaitFailure(CompletableFuture<?> fut) {
        try {
            fut.get(15, TimeUnit.SECONDS);
            return false;
        } catch (ExecutionException | TimeoutException | InterruptedException e) {
            return true;
        }
    }

    /**
     * Builds a minimal RTP packet (V=2, P=0, X=0, CC=0, M=0, PT=0).
     *
     * @param ssrc    the SSRC to embed
     * @param seq     the sequence number
     * @param ts      the RTP timestamp
     * @param payload the payload bytes
     * @return the RTP packet
     */
    private static byte[] makeRtpPacket(int ssrc, int seq, int ts, byte[] payload) {
        var pkt = new byte[12 + payload.length];
        pkt[0] = (byte) 0x80;
        pkt[1] = 0;
        pkt[2] = (byte) (seq >>> 8);
        pkt[3] = (byte) seq;
        pkt[4] = (byte) (ts >>> 24);
        pkt[5] = (byte) (ts >>> 16);
        pkt[6] = (byte) (ts >>> 8);
        pkt[7] = (byte) ts;
        pkt[8] = (byte) (ssrc >>> 24);
        pkt[9] = (byte) (ssrc >>> 16);
        pkt[10] = (byte) (ssrc >>> 8);
        pkt[11] = (byte) ssrc;
        System.arraycopy(payload, 0, pkt, 12, payload.length);
        return pkt;
    }

    /**
     * Hex-encodes a byte array.
     *
     * @param b the bytes
     * @return the lower-case hex string
     */
    private static String toHex(byte[] b) {
        var sb = new StringBuilder(b.length * 2);
        for (var x : b) sb.append(String.format("%02x", x & 0xFF));
        return sb.toString();
    }
}
