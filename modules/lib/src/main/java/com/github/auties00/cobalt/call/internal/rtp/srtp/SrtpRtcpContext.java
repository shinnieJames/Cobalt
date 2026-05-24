package com.github.auties00.cobalt.call.internal.rtp.srtp;

import com.github.auties00.cobalt.exception.WhatsAppCallException;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.GeneralSecurityException;

/**
 * Per-SSRC SRTCP context implementing RFC 3711 §3.4 with the
 * {@code SRTP_AES128_CM_HMAC_SHA1_80} profile: AES-128 in counter
 * mode for confidentiality (with the encryption flag always set),
 * HMAC-SHA1 truncated to 80 bits for authentication, and a 31-bit
 * monotonic SRTCP index carried in the trailing index field.
 *
 * <p>One context handles a single direction (sender or receiver) for
 * one SSRC. Sender contexts increment the index per packet; receiver
 * contexts replay-check via {@link SrtpReplayWindow}.
 *
 * <p>Methods are {@code synchronized} since the underlying JCA
 * {@link Cipher} and {@link Mac} are stateful.
 */
final class SrtpRtcpContext {
    /**
     * Length, in bytes, of the truncated HMAC-SHA1-80 auth tag.
     */
    private static final int AUTH_TAG_LEN = 10;

    /**
     * Length, in bytes, of the trailing E-flag + SRTCP index field.
     */
    private static final int INDEX_FIELD_LEN = 4;

    /**
     * Length, in bytes, of the RTCP common header through the SSRC
     * of the sender — the boundary at which SRTCP encryption begins.
     */
    private static final int RTCP_HEADER_LEN = 8;

    /**
     * Bit set in the high byte of the index field to indicate that
     * the SRTCP payload is encrypted (as it always is in our
     * profile).
     */
    private static final int E_FLAG_MASK = 0x80000000;

    /**
     * 31-bit mask isolating the SRTCP index from the E flag.
     */
    private static final long INDEX_MASK = 0x7FFFFFFFL;

    /**
     * Owning direction holding the derived session keys and salt.
     */
    private final SrtpDirection direction;

    /**
     * Synchronization-source identifier this context is keyed on.
     */
    private final int ssrc;

    /**
     * {@code true} if this context is a sender; {@code false} if it
     * is a receiver.
     */
    private final boolean sender;

    /**
     * AES cipher in CTR mode, re-IV'd per packet.
     */
    private final Cipher aes;

    /**
     * HMAC-SHA1 instance, pre-keyed with
     * {@link SrtpDirection#srtcpAuthKey}.
     */
    private final Mac hmac;

    /**
     * AES key spec for the SRTCP session encryption key.
     */
    private final SecretKeySpec encKeySpec;

    /**
     * Sender state: monotonically incrementing 31-bit SRTCP index.
     * Starts at zero; incremented to one before the first packet is
     * sent.
     */
    private long senderIndex;

    /**
     * Receiver state: replay-detection window keyed on the 31-bit
     * SRTCP index.
     */
    private final SrtpReplayWindow replay = new SrtpReplayWindow();

    /**
     * Constructs a new context.
     *
     * @param direction the direction holding the derived session keys
     * @param ssrc      the SSRC the context is keyed on
     * @param sender    {@code true} for the sender side
     */
    SrtpRtcpContext(SrtpDirection direction, int ssrc, boolean sender) {
        this.direction = direction;
        this.ssrc = ssrc;
        this.sender = sender;
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
     * Encrypts and authenticates an outbound RTCP packet. The 8-byte
     * RTCP common header (V/P/RC/PT/length + sender SSRC) is left in
     * cleartext; everything after it is encrypted with AES-128-CTR;
     * the 4-byte (E=1 || index) field is appended; and a 10-byte
     * HMAC-SHA1 tag covering the encrypted packet plus the index
     * field is appended after that.
     *
     * @param rtcp the plaintext RTCP packet
     * @return the SRTCP packet, length
     *         {@code rtcp.length + 4 + 10}
     * @throws WhatsAppCallException.Srtp if the packet is malformed or encryption
     *                       fails
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

        var totalLen = rtcp.length + INDEX_FIELD_LEN + AUTH_TAG_LEN;
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
     * Authenticates and decrypts an inbound SRTCP packet. The trailing
     * 10-byte HMAC tag is validated first; the E-flag and index are
     * then extracted from the field preceding the tag, and the
     * encrypted portion is decrypted into the returned plaintext
     * RTCP packet.
     *
     * @param srtcp the SRTCP packet
     * @return the plaintext RTCP packet
     * @throws WhatsAppCallException.Srtp if the packet is malformed, replays an
     *                       earlier index, or fails authentication
     */
    synchronized byte[] unprotect(byte[] srtcp) {
        if (sender) {
            throw new WhatsAppCallException.Srtp("unprotect called on a sender context");
        }
        if (srtcp.length < RTCP_HEADER_LEN + INDEX_FIELD_LEN + AUTH_TAG_LEN) {
            throw new WhatsAppCallException.Srtp("SRTCP packet too short");
        }

        var dataLen = srtcp.length - AUTH_TAG_LEN;
        var expected = computeAuthTag(srtcp, dataLen);
        if (!SrtpDirection.constantTimeEquals(expected, 0, srtcp, dataLen, AUTH_TAG_LEN)) {
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
     * Computes the 10-byte HMAC-SHA1 auth tag covering the first
     * {@code authedLen} bytes of {@code packet}, truncated to
     * {@link #AUTH_TAG_LEN}.
     *
     * @param packet    the packet bytes
     * @param authedLen the count of authenticated bytes
     * @return the 10-byte truncated tag
     */
    private byte[] computeAuthTag(byte[] packet, int authedLen) {
        hmac.update(packet, 0, authedLen);
        var full = hmac.doFinal();
        var tag = new byte[AUTH_TAG_LEN];
        System.arraycopy(full, 0, tag, 0, AUTH_TAG_LEN);
        return tag;
    }

    /**
     * Computes and writes the auth tag for the outbound packet
     * currently in {@code packet[0..authedLen)} into
     * {@code packet[authedLen..authedLen+AUTH_TAG_LEN)}.
     *
     * @param packet    the in-progress SRTCP packet (room for the tag
     *                  must already exist)
     * @param authedLen the length of the authenticated portion
     */
    private void appendAuthTag(byte[] packet, int authedLen) {
        var tag = computeAuthTag(packet, authedLen);
        System.arraycopy(tag, 0, packet, authedLen, AUTH_TAG_LEN);
    }
}
