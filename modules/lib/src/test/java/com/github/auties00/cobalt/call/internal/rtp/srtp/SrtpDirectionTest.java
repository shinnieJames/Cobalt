package com.github.auties00.cobalt.call.internal.rtp.srtp;

import org.junit.jupiter.api.Test;

import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

/**
 * Pins {@link SrtpDirection}'s AES-CM key-derivation function (RFC 3711 section 4.3)
 * against the known-answer vectors published in RFC 3711 Appendix B.3. The vectors
 * cover the cipher key (label 0x00, 16 bytes), the auth key (label 0x01, 20 bytes,
 * which spans two AES output blocks and so exercises the counter-increment path), and
 * the cipher salt (label 0x02, 14 bytes).
 */
public class SrtpDirectionTest {

    // All five constants are the RFC 3711 Appendix B.3 known-answer vectors.
    private static final byte[] MASTER_KEY = hex("E1F97A0D3E018BE0D64FA32C06DE4139");

    private static final byte[] MASTER_SALT = hex("0EC675AD498AFEEBB6960B3AABE6");

    private static final byte[] EXPECTED_CIPHER_KEY = hex("C61E7A93744F39EE10734AFE3FF7A087");

    private static final byte[] EXPECTED_AUTH_KEY = hex("CEBE321F6FF7716B6FD4AB49AF256A156D38BAA4");

    private static final byte[] EXPECTED_CIPHER_SALT = hex("30CBBC08863D8C85D49DB34A9AE1");

    @Test
    public void rfc3711B3CipherKey() {
        var dir = new SrtpDirection(MASTER_KEY, MASTER_SALT);
        assertArrayEquals(EXPECTED_CIPHER_KEY, dir.srtpEncKey);
    }

    @Test
    public void rfc3711B3AuthKey() {
        var dir = new SrtpDirection(MASTER_KEY, MASTER_SALT);
        assertArrayEquals(EXPECTED_AUTH_KEY, dir.srtpAuthKey);
    }

    @Test
    public void rfc3711B3CipherSalt() {
        var dir = new SrtpDirection(MASTER_KEY, MASTER_SALT);
        assertArrayEquals(EXPECTED_CIPHER_SALT, dir.srtpSalt);
    }

    private static byte[] hex(String s) {
        return HexFormat.of().parseHex(s);
    }
}
