package com.github.auties00.cobalt.call.rtp.srtp;

import com.github.auties00.cobalt.exception.WhatsAppCallException;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.GeneralSecurityException;

/**
 * Protects and unprotects SRTCP packets for one SSRC in one direction under the
 * {@code SRTP_AES128_CM_HMAC_SHA1_80} profile (RFC 3711 section 3.4).
 *
 * <p>The context uses AES-128 in counter mode for confidentiality (with the encryption flag always
 * set), HMAC-SHA1 truncated to 80 bits for authentication, and a 31-bit monotonic SRTCP index
 * carried in the trailing index field. A single context serves either the sender or the receiver
 * side: a sender increments the index for every packet, while a receiver rejects replays via a
 * {@link SrtpReplayWindow}.
 *
 * <p>The protect and unprotect methods are {@code synchronized} because the underlying JCA
 * {@link Cipher} and {@link Mac} carry per-operation state that must not interleave.
 */
final class SrtpRtcpContext {
    /**
     * Holds the length, in bytes, of the truncated HMAC-SHA1 authentication tag, set per call from the
     * relay's {@code warp_mi_tag_len} attribute (at most 8) or defaulting to the profile's 10.
     */
    private final int authTagLen;

    /**
     * Holds the length, in bytes, of the trailing field that carries the E flag and the SRTCP
     * index.
     */
    private static final int INDEX_FIELD_LEN = 4;

    /**
     * Holds the length, in bytes, of the RTCP common header through the sender SSRC, which is the
     * boundary at which SRTCP encryption begins.
     */
    private static final int RTCP_HEADER_LEN = 8;

    /**
     * Holds the bit set in the high byte of the index field to mark the SRTCP payload as
     * encrypted, which it always is in this profile.
     */
    private static final int E_FLAG_MASK = 0x80000000;

    /**
     * Holds the 31-bit mask that isolates the SRTCP index from the E flag.
     */
    private static final long INDEX_MASK = 0x7FFFFFFFL;

    /**
     * Holds the owning direction, which carries the derived session keys and salt.
     */
    private final SrtpDirection direction;

    /**
     * Holds the synchronization-source identifier this context is keyed on.
     */
    private final int ssrc;

    /**
     * Indicates whether this context is a sender ({@code true}) or a receiver ({@code false}).
     */
    private final boolean sender;

    /**
     * Holds the AES cipher in counter mode, re-initialized with a fresh initial counter for every
     * packet.
     */
    private final Cipher aes;

    /**
     * Holds the HMAC-SHA1 instance, pre-keyed with {@link SrtpDirection#srtcpAuthKey}.
     */
    private final Mac hmac;

    /**
     * Holds the AES key spec for the SRTCP session encryption key.
     */
    private final SecretKeySpec encKeySpec;

    /**
     * Holds the sender-side monotonically incrementing 31-bit SRTCP index.
     *
     * <p>It starts at zero and is incremented to one before the first packet is sent, so the first
     * outbound packet carries index one.
     */
    private long senderIndex;

    /**
     * Holds the receiver-side replay-detection window keyed on the 31-bit SRTCP index.
     */
    private final SrtpReplayWindow replay = new SrtpReplayWindow();

    /**
     * Constructs a context for one SSRC and direction, initializing the AES cipher and the
     * pre-keyed HMAC instance.
     *
     * @param direction the direction holding the derived session keys
     * @param ssrc      the SSRC this context is keyed on
     * @param sender    {@code true} for the sender side, {@code false} for the receiver side
     * @param authTagLen the truncated authentication tag length in bytes
     * @throws WhatsAppCallException.Srtp if the JCA cipher or MAC cannot be initialized
     */
    SrtpRtcpContext(SrtpDirection direction, int ssrc, boolean sender, int authTagLen) {
        this.direction = direction;
        this.ssrc = ssrc;
        this.sender = sender;
        this.authTagLen = authTagLen;
        this.encKeySpec = new SecretKeySpec(direction.srtcpEncKey, "AES");
        try {
            this.aes = Cipher.getInstance("AES/CTR/NoPadding");
            this.hmac = Mac.getInstance("HmacSHA1");
            this.hmac.init(new SecretKeySpec(direction.srtcpAuthKey, "HmacSHA1"));
        } catch (GeneralSecurityException e) {
            throw new WhatsAppCallException.Srtp("failed to initialize SRTCP context", e);
        }
    }

    /**
     * Encrypts and authenticates an outbound RTCP packet.
     *
     * <p>The 8-byte RTCP common header (version, padding, reception count, packet type, length, and
     * sender SSRC) is copied in cleartext; everything after it is encrypted with AES-128-CTR keyed
     * on the per-packet initial counter. The sender index is incremented and appended as the
     * 4-byte {@code (E=1 || index)} field, and a 10-byte HMAC-SHA1 tag covering the encrypted
     * packet plus that index field is appended last.
     *
     * @param rtcp the plaintext RTCP packet
     * @return the SRTCP packet, of length {@code rtcp.length + 4 + 10}
     * @throws WhatsAppCallException.Srtp if called on a receiver context, if the packet is shorter
     *                                    than the 8-byte header, if the 31-bit index space is
     *                                    exhausted, or if encryption fails
     */
    synchronized byte[] protect(byte[] rtcp) {
        if (!sender) {
            throw new WhatsAppCallException.Srtp("protect called on a receiver context");
        }
        if (rtcp.length < RTCP_HEADER_LEN) {
            throw new WhatsAppCallException.Srtp("RTCP packet shorter than 8-byte header");
        }
        senderIndex++;
        if ((senderIndex & ~INDEX_MASK) != 0L) {
            throw new WhatsAppCallException.Srtp("SRTCP index space exhausted");
        }

        var totalLen = rtcp.length + INDEX_FIELD_LEN + authTagLen;
        var out = new byte[totalLen];
        System.arraycopy(rtcp, 0, out, 0, RTCP_HEADER_LEN);
        var encryptedLen = rtcp.length - RTCP_HEADER_LEN;
        try {
            aes.init(Cipher.ENCRYPT_MODE, encKeySpec,
                    new IvParameterSpec(SrtpDirection.computeIv(direction.srtcpSalt, ssrc, senderIndex)));
            aes.doFinal(rtcp, RTCP_HEADER_LEN, encryptedLen, out, RTCP_HEADER_LEN);
        } catch (GeneralSecurityException e) {
            throw new WhatsAppCallException.Srtp("SRTCP encryption failed", e);
        }

        var indexFieldOff = rtcp.length;
        var indexField = (int) (E_FLAG_MASK | (senderIndex & INDEX_MASK));
        out[indexFieldOff]     = (byte) (indexField >>> 24);
        out[indexFieldOff + 1] = (byte) (indexField >>> 16);
        out[indexFieldOff + 2] = (byte) (indexField >>>  8);
        out[indexFieldOff + 3] = (byte)  indexField;

        var authedLen = rtcp.length + INDEX_FIELD_LEN;
        appendAuthTag(out, authedLen);
        return out;
    }

    /**
     * Authenticates and decrypts an inbound SRTCP packet.
     *
     * <p>The trailing 10-byte HMAC tag is validated first in constant time. The E flag and index
     * are then extracted from the 4-byte field preceding the tag, the index is replay-checked, and,
     * when the E flag is set, the encrypted portion after the 8-byte header is decrypted into the
     * returned plaintext RTCP packet; an unencrypted payload is copied verbatim. The replay window
     * is updated only after successful authentication.
     *
     * @param srtcp the SRTCP packet
     * @return the plaintext RTCP packet
     * @throws WhatsAppCallException.Srtp if called on a sender context, if the packet is too short,
     *                                    if it replays an earlier index, if authentication fails, or
     *                                    if decryption fails
     */
    synchronized byte[] unprotect(byte[] srtcp) {
        if (sender) {
            throw new WhatsAppCallException.Srtp("unprotect called on a sender context");
        }
        if (srtcp.length < RTCP_HEADER_LEN + INDEX_FIELD_LEN + authTagLen) {
            throw new WhatsAppCallException.Srtp("SRTCP packet too short");
        }

        var dataLen = srtcp.length - authTagLen;
        var expected = computeAuthTag(srtcp, dataLen);
        if (!SrtpDirection.constantTimeEquals(expected, 0, srtcp, dataLen, authTagLen)) {
            throw new WhatsAppCallException.Srtp("SRTCP auth failed");
        }

        var indexFieldOff = dataLen - INDEX_FIELD_LEN;
        var indexField = ((srtcp[indexFieldOff] & 0xFF) << 24)
                | ((srtcp[indexFieldOff + 1] & 0xFF) << 16)
                | ((srtcp[indexFieldOff + 2] & 0xFF) <<  8)
                |  (srtcp[indexFieldOff + 3] & 0xFF);
        var encrypted = (indexField & E_FLAG_MASK) != 0;
        var index = indexField & INDEX_MASK;

        if (!replay.check(index)) {
            throw new WhatsAppCallException.Srtp("SRTCP replay detected at index " + index);
        }

        var rtcpLen = indexFieldOff;
        var plain = new byte[rtcpLen];
        System.arraycopy(srtcp, 0, plain, 0, RTCP_HEADER_LEN);
        var encryptedLen = rtcpLen - RTCP_HEADER_LEN;
        if (encrypted) {
            try {
                aes.init(Cipher.DECRYPT_MODE, encKeySpec,
                        new IvParameterSpec(SrtpDirection.computeIv(direction.srtcpSalt, ssrc, index)));
                aes.doFinal(srtcp, RTCP_HEADER_LEN, encryptedLen, plain, RTCP_HEADER_LEN);
            } catch (GeneralSecurityException e) {
                throw new WhatsAppCallException.Srtp("SRTCP decryption failed", e);
            }
        } else {
            System.arraycopy(srtcp, RTCP_HEADER_LEN, plain, RTCP_HEADER_LEN, encryptedLen);
        }

        replay.update(index);
        return plain;
    }

    /**
     * Computes the truncated HMAC-SHA1 authentication tag over the first {@code authedLen} bytes of
     * the packet.
     *
     * <p>The full 20-byte HMAC-SHA1 output is computed and the leading {@link #authTagLen} bytes
     * are returned as the tag.
     *
     * @param packet    the packet bytes
     * @param authedLen the count of authenticated leading bytes
     * @return the 10-byte truncated tag
     */
    private byte[] computeAuthTag(byte[] packet, int authedLen) {
        hmac.update(packet, 0, authedLen);
        var full = hmac.doFinal();
        var tag = new byte[authTagLen];
        System.arraycopy(full, 0, tag, 0, authTagLen);
        return tag;
    }

    /**
     * Computes and writes the authentication tag for the outbound packet held in
     * {@code packet[0..authedLen)} into {@code packet[authedLen..authedLen + authTagLen)}.
     *
     * @param packet    the in-progress SRTCP packet, which must already have room for the tag
     * @param authedLen the length of the authenticated portion
     */
    private void appendAuthTag(byte[] packet, int authedLen) {
        var tag = computeAuthTag(packet, authedLen);
        System.arraycopy(tag, 0, packet, authedLen, authTagLen);
    }
}
