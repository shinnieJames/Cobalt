package com.github.auties00.cobalt.call.rtp.srtp;

import com.github.auties00.cobalt.exception.WhatsAppCallException;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.GeneralSecurityException;
import java.util.Arrays;

/**
 * Derived session keys for one direction of an SRTP/SRTCP association,
 * produced by running the RFC 3711 §4.3 key-derivation function on a
 * single (master_key, master_salt) pair.
 *
 * <p>With key-derivation rate set to zero (the default and the only
 * value WhatsApp/WebRTC uses), each label produces one constant value
 * for the whole session — so every SSRC sharing this direction shares
 * the same derived encryption keys, authentication keys, and salts.
 *
 * <p>This class also exposes static helpers that are needed by both
 * the SRTP and SRTCP per-SSRC contexts: the AES-CM initial-counter
 * builder and a constant-time byte-range comparator for auth-tag
 * validation.
 */
final class SrtpDirection {
    /**
     * RFC 3711 §4.3 label byte for the SRTP encryption key.
     */
    private static final byte LABEL_SRTP_ENC = 0x00;

    /**
     * RFC 3711 §4.3 label byte for the SRTP authentication key.
     */
    private static final byte LABEL_SRTP_AUTH = 0x01;

    /**
     * RFC 3711 §4.3 label byte for the SRTP session salt.
     */
    private static final byte LABEL_SRTP_SALT = 0x02;

    /**
     * RFC 3711 §4.3 label byte for the SRTCP encryption key.
     */
    private static final byte LABEL_SRTCP_ENC = 0x03;

    /**
     * RFC 3711 §4.3 label byte for the SRTCP authentication key.
     */
    private static final byte LABEL_SRTCP_AUTH = 0x04;

    /**
     * RFC 3711 §4.3 label byte for the SRTCP session salt.
     */
    private static final byte LABEL_SRTCP_SALT = 0x05;

    /**
     * Length, in bytes, of an AES-128 encryption key.
     */
    private static final int ENC_KEY_LEN = 16;

    /**
     * Length, in bytes, of an HMAC-SHA1 authentication key.
     */
    private static final int AUTH_KEY_LEN = 20;

    /**
     * Length, in bytes, of an SRTP/SRTCP session salt.
     */
    private static final int SALT_LEN = 14;

    /**
     * SRTP session encryption key (AES-128).
     */
    final byte[] srtpEncKey;

    /**
     * SRTP session authentication key (HMAC-SHA1).
     */
    final byte[] srtpAuthKey;

    /**
     * SRTP session salt, used to mask the AES-CM initial counter.
     */
    final byte[] srtpSalt;

    /**
     * SRTCP session encryption key (AES-128).
     */
    final byte[] srtcpEncKey;

    /**
     * SRTCP session authentication key (HMAC-SHA1).
     */
    final byte[] srtcpAuthKey;

    /**
     * SRTCP session salt, used to mask the AES-CM initial counter.
     */
    final byte[] srtcpSalt;

    /**
     * Constructs a direction by running the KDF on the given master
     * key and salt.
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
     * Zeros the cached session keys and salts. Called when the parent
     * endpoint is closed.
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
     * Runs the AES-CM-based PRF from RFC 3711 §4.3.1 with
     * key-derivation rate set to zero: the initial counter is
     * {@code (label || 0^48) XOR master_salt} (right-aligned within
     * the 14-byte salt) shifted left 16 bits to fill a 128-bit block,
     * and AES-CTR then produces a keystream of which the first
     * {@code outLen} bytes are the derived key.
     *
     * @param masterKey  the 16-byte master key
     * @param masterSalt the 14-byte master salt
     * @param label      the RFC 3711 label byte
     * @param outLen     the desired derived-key length, in bytes
     * @return the derived key
     */
    private static byte[] derive(byte[] masterKey, byte[] masterSalt, byte label, int outLen) {
        var iv = new byte[16];
        // Salt occupies bytes 0..13; bytes 14..15 are zero (the "*2^16" left-shift).
        System.arraycopy(masterSalt, 0, iv, 0, SALT_LEN);
        // key_id is label || 0^48 (7 bytes total) right-aligned with the 14-byte salt;
        // when XORed into the salt, the label byte lands at salt-byte 7 and the six
        // zero bytes touch salt-bytes 8..13 (no-op).
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
     * Builds the 16-byte AES-CM initial counter (RFC 3711 §4.1.1)
     * from a session salt, an SSRC, and a packet index. The salt is
     * placed in the high 14 bytes; the SSRC is XORed at byte offset
     * 4 (32-bit big-endian); the index is XORed at byte offset 8
     * (48-bit big-endian); the trailing two bytes stay zero.
     *
     * @param salt  the 14-byte session salt
     * @param ssrc  the synchronization-source identifier (32-bit)
     * @param index the packet index — 48 bits for SRTP, 31 bits for
     *              SRTCP (zero-extended into the same 48-bit slot)
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
     * Compares two byte ranges in constant time. Used to validate
     * received auth tags against the locally-computed ones without
     * leaking timing information.
     *
     * @param a    the first array
     * @param aOff offset into {@code a}
     * @param b    the second array
     * @param bOff offset into {@code b}
     * @param len  number of bytes to compare
     * @return {@code true} iff the ranges are byte-equal
     */
    static boolean constantTimeEquals(byte[] a, int aOff, byte[] b, int bOff, int len) {
        var diff = 0;
        for (var i = 0; i < len; i++) {
            diff |= (a[aOff + i] ^ b[bOff + i]) & 0xFF;
        }
        return diff == 0;
    }
}
