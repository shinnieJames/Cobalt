package com.github.auties00.cobalt.call.internal.rtp.srtp;

import org.junit.jupiter.api.Test;

import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

/**
 * Known-answer tests for {@link SrtpDirection}'s key-derivation
 * function, pinning the AES-CM PRF (RFC 3711 §4.3) against the test
 * vectors published in RFC 3711 Appendix B.3.
 *
 * <p>These vectors validate the cipher key (label 0x00, 16 bytes),
 * the auth key (label 0x01, 20 bytes — spans two AES output blocks),
 * and the cipher salt (label 0x02, 14 bytes), exercising every
 * length corner of the PRF.
 */
public class SrtpDirectionTest {

    /**
     * The master key from RFC 3711 Appendix B.3.
     */
    private static final byte[] MASTER_KEY = hex("E1F97A0D3E018BE0D64FA32C06DE4139");

    /**
     * The master salt from RFC 3711 Appendix B.3.
     */
    private static final byte[] MASTER_SALT = hex("0EC675AD498AFEEBB6960B3AABE6");

    /**
     * The expected SRTP cipher key (label 0x00) from the same
     * appendix.
     */
    private static final byte[] EXPECTED_CIPHER_KEY = hex("C61E7A93744F39EE10734AFE3FF7A087");

    /**
     * The expected SRTP auth key (label 0x01) from the same appendix.
     */
    private static final byte[] EXPECTED_AUTH_KEY = hex("CEBE321F6FF7716B6FD4AB49AF256A156D38BAA4");

    /**
     * The expected SRTP cipher salt (label 0x02) from the same
     * appendix.
     */
    private static final byte[] EXPECTED_CIPHER_SALT = hex("30CBBC08863D8C85D49DB34A9AE1");

    /**
     * Asserts that the SRTP encryption key derived for label 0x00
     * matches the RFC 3711 Appendix B.3 vector.
     */
    @Test
    public void rfc3711B3CipherKey() {
        var dir = new SrtpDirection(MASTER_KEY, MASTER_SALT);
        assertArrayEquals(EXPECTED_CIPHER_KEY, dir.srtpEncKey);
    }

    /**
     * Asserts that the SRTP authentication key derived for label
     * 0x01 matches the RFC 3711 Appendix B.3 vector. This vector
     * spans two AES output blocks so it pins the counter-incrementing
     * code path.
     */
    @Test
    public void rfc3711B3AuthKey() {
        var dir = new SrtpDirection(MASTER_KEY, MASTER_SALT);
        assertArrayEquals(EXPECTED_AUTH_KEY, dir.srtpAuthKey);
    }

    /**
     * Asserts that the SRTP session salt derived for label 0x02
     * matches the RFC 3711 Appendix B.3 vector.
     */
    @Test
    public void rfc3711B3CipherSalt() {
        var dir = new SrtpDirection(MASTER_KEY, MASTER_SALT);
        assertArrayEquals(EXPECTED_CIPHER_SALT, dir.srtpSalt);
    }

    /**
     * Decodes a hex string into a byte array.
     *
     * @param s the hex-encoded string
     * @return the decoded bytes
     */
    private static byte[] hex(String s) {
        return HexFormat.of().parseHex(s);
    }
}
