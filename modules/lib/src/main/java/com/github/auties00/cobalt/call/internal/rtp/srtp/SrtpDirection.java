package com.github.auties00.cobalt.call.internal.rtp.srtp;

import com.github.auties00.cobalt.exception.WhatsAppCallException;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.GeneralSecurityException;
import java.util.Arrays;

/**
 * Holds the derived session keys and salts for one direction of an SRTP/SRTCP association.
 *
 * <p>The six values are produced by running the RFC 3711 section 4.3 key-derivation function over
 * a single {@code (master_key, master_salt)} pair, one derivation per RFC 3711 label: the SRTP
 * encryption key, SRTP authentication key, and SRTP salt, plus the matching SRTCP triple. With the
 * key-derivation rate fixed at zero (the default, and the only value WhatsApp and WebRTC use) each
 * label yields a single constant value for the whole session, so every SSRC sharing this direction
 * shares the same derived encryption keys, authentication keys, and salts.
 *
 * <p>The class also exposes the two static helpers needed by both the SRTP and SRTCP per-SSRC
 * contexts: the AES-CM initial-counter builder ({@link #computeIv(byte[], int, long)}) and a
 * constant-time byte-range comparator for auth-tag validation
 * ({@link #constantTimeEquals(byte[], int, byte[], int, int)}).
 *
 * @implNote This implementation fixes the key-derivation rate at zero rather than exposing it as a
 *           parameter, so each label is derived exactly once at construction and cached for the
 *           lifetime of the direction.
 */
final class SrtpDirection {
    /**
     * Holds the RFC 3711 section 4.3 label byte that selects the SRTP encryption key.
     */
    private static final byte LABEL_SRTP_ENC = 0x00;

    /**
     * Holds the RFC 3711 section 4.3 label byte that selects the SRTP authentication key.
     */
    private static final byte LABEL_SRTP_AUTH = 0x01;

    /**
     * Holds the RFC 3711 section 4.3 label byte that selects the SRTP session salt.
     */
    private static final byte LABEL_SRTP_SALT = 0x02;

    /**
     * Holds the RFC 3711 section 4.3 label byte that selects the SRTCP encryption key.
     */
    private static final byte LABEL_SRTCP_ENC = 0x03;

    /**
     * Holds the RFC 3711 section 4.3 label byte that selects the SRTCP authentication key.
     */
    private static final byte LABEL_SRTCP_AUTH = 0x04;

    /**
     * Holds the RFC 3711 section 4.3 label byte that selects the SRTCP session salt.
     */
    private static final byte LABEL_SRTCP_SALT = 0x05;

    /**
     * Holds the length, in bytes, of an AES-128 encryption key.
     */
    private static final int ENC_KEY_LEN = 16;

    /**
     * Holds the length, in bytes, of an HMAC-SHA1 authentication key.
     */
    private static final int AUTH_KEY_LEN = 20;

    /**
     * Holds the length, in bytes, of an SRTP/SRTCP session salt.
     */
    private static final int SALT_LEN = 14;

    /**
     * Holds the derived SRTP session encryption key (AES-128).
     */
    final byte[] srtpEncKey;

    /**
     * Holds the derived SRTP session authentication key (HMAC-SHA1).
     */
    final byte[] srtpAuthKey;

    /**
     * Holds the derived SRTP session salt, which masks the AES-CM initial counter.
     */
    final byte[] srtpSalt;

    /**
     * Holds the derived SRTCP session encryption key (AES-128).
     */
    final byte[] srtcpEncKey;

    /**
     * Holds the derived SRTCP session authentication key (HMAC-SHA1).
     */
    final byte[] srtcpAuthKey;

    /**
     * Holds the derived SRTCP session salt, which masks the AES-CM initial counter.
     */
    final byte[] srtcpSalt;

    /**
     * Constructs a direction by running the key-derivation function over the given master key and
     * salt for each of the six RFC 3711 labels.
     *
     * @param masterKey  the 16-byte master key
     * @param masterSalt the 14-byte master salt
     */
    SrtpDirection(byte[] masterKey, byte[] masterSalt) {
        this.srtpEncKey   = derive(masterKey, masterSalt, LABEL_SRTP_ENC,   ENC_KEY_LEN);
        this.srtpAuthKey  = derive(masterKey, masterSalt, LABEL_SRTP_AUTH,  AUTH_KEY_LEN);
        this.srtpSalt     = derive(masterKey, masterSalt, LABEL_SRTP_SALT,  SALT_LEN);
        this.srtcpEncKey  = derive(masterKey, masterSalt, LABEL_SRTCP_ENC,  ENC_KEY_LEN);
        this.srtcpAuthKey = derive(masterKey, masterSalt, LABEL_SRTCP_AUTH, AUTH_KEY_LEN);
        this.srtcpSalt    = derive(masterKey, masterSalt, LABEL_SRTCP_SALT, SALT_LEN);
    }

    /**
     * Overwrites the cached session keys and salts with zero bytes.
     *
     * <p>The owning endpoint calls this on close so that derived key material does not linger in
     * the heap after the association ends. The arrays remain allocated but no longer carry secret
     * bytes.
     */
    void zero() {
        Arrays.fill(srtpEncKey,   (byte) 0);
        Arrays.fill(srtpAuthKey,  (byte) 0);
        Arrays.fill(srtpSalt,     (byte) 0);
        Arrays.fill(srtcpEncKey,  (byte) 0);
        Arrays.fill(srtcpAuthKey, (byte) 0);
        Arrays.fill(srtcpSalt,    (byte) 0);
    }

    /**
     * Derives one session value by running the AES-CM pseudo-random function of RFC 3711
     * section 4.3.1 with the key-derivation rate fixed at zero.
     *
     * <p>The initial counter is {@code (label || 0^48)} XORed into the master salt and shifted
     * left by 16 bits to fill a 128-bit block. AES in counter mode is then run over a
     * {@code outLen}-byte block of zeros, and the resulting keystream is the derived value.
     *
     * @param masterKey  the 16-byte master key
     * @param masterSalt the 14-byte master salt
     * @param label      the RFC 3711 label byte selecting which value is derived
     * @param outLen     the desired derived-value length, in bytes
     * @return the derived key, authentication key, or salt of length {@code outLen}
     * @throws WhatsAppCallException.Srtp if the JCA AES-CTR transformation cannot be initialized or run
     * @implNote This implementation builds the counter in place: the salt occupies bytes 0 through
     *           13 and bytes 14 and 15 stay zero, which realizes the {@code "* 2^16"} left shift.
     *           The {@code key_id} is {@code label || 0^48}, seven bytes right-aligned with the
     *           14-byte salt, so XORing the label lands it at salt byte 7 while the six trailing
     *           zero bytes touch salt bytes 8 through 13 as a no-op.
     */
    private static byte[] derive(byte[] masterKey, byte[] masterSalt, byte label, int outLen) {
        var iv = new byte[16];
        System.arraycopy(masterSalt, 0, iv, 0, SALT_LEN);
        iv[7] ^= label;
        try {
            var cipher = Cipher.getInstance("AES/CTR/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE,
                    new SecretKeySpec(masterKey, "AES"),
                    new IvParameterSpec(iv));
            return cipher.doFinal(new byte[outLen]);
        } catch (GeneralSecurityException e) {
            throw new WhatsAppCallException.Srtp("SRTP KDF failed", e);
        }
    }

    /**
     * Builds the 16-byte AES-CM initial counter of RFC 3711 section 4.1.1 from a session salt, an
     * SSRC, and a packet index.
     *
     * <p>The salt is placed in the high 14 bytes; the SSRC is XORed in as a 32-bit big-endian value
     * at byte offset 4; the index is XORed in as a 48-bit big-endian value at byte offset 8; the
     * trailing two bytes remain zero.
     *
     * @param salt  the 14-byte session salt
     * @param ssrc  the 32-bit synchronization-source identifier
     * @param index the packet index, 48 bits for SRTP or a 31-bit SRTCP index zero-extended into
     *              the same 48-bit slot
     * @return the 16-byte initial counter
     */
    static byte[] computeIv(byte[] salt, int ssrc, long index) {
        var iv = new byte[16];
        System.arraycopy(salt, 0, iv, 0, SALT_LEN);
        iv[4]  ^= (byte) (ssrc >>> 24);
        iv[5]  ^= (byte) (ssrc >>> 16);
        iv[6]  ^= (byte) (ssrc >>>  8);
        iv[7]  ^= (byte)  ssrc;
        iv[8]  ^= (byte) (index >>> 40);
        iv[9]  ^= (byte) (index >>> 32);
        iv[10] ^= (byte) (index >>> 24);
        iv[11] ^= (byte) (index >>> 16);
        iv[12] ^= (byte) (index >>>  8);
        iv[13] ^= (byte)  index;
        return iv;
    }

    /**
     * Compares two byte ranges for equality in constant time.
     *
     * <p>The comparison accumulates the bitwise difference of every byte pair and only inspects the
     * accumulator at the end, so its running time does not depend on the position of the first
     * differing byte. It validates a received authentication tag against the locally computed tag
     * without leaking, through timing, how many leading bytes matched.
     *
     * @param a    the first array
     * @param aOff the offset into {@code a} at which the range begins
     * @param b    the second array
     * @param bOff the offset into {@code b} at which the range begins
     * @param len  the number of bytes to compare
     * @return {@code true} if and only if the two ranges are byte-for-byte equal
     */
    static boolean constantTimeEquals(byte[] a, int aOff, byte[] b, int bOff, int len) {
        var diff = 0;
        for (var i = 0; i < len; i++) {
            diff |= (a[aOff + i] ^ b[bOff + i]) & 0xFF;
        }
        return diff == 0;
    }
}
