package com.github.auties00.cobalt.call.internal.rtp.srtp;

import com.github.auties00.cobalt.exception.WhatsAppCallException;
import com.github.auties00.cobalt.util.DataUtils;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteOrder;
import java.security.GeneralSecurityException;

/**
 * Per-SSRC SRTP context implementing the
 * {@code SRTP_AES128_CM_HMAC_SHA1_80} profile (RFC 3711 §4.1):
 * AES-128 in counter mode for confidentiality, HMAC-SHA1 truncated
 * to 80 bits for authentication.
 *
 * <p>One context handles a single direction (sender or receiver) for
 * one SSRC. Sender contexts track the 32-bit roll-over counter (ROC)
 * for the 48-bit packet index; receiver contexts reconstruct the
 * sender's index per RFC 3711 §3.3.1 and replay-check via
 * {@link SrtpReplayWindow}.
 *
 * <p>Methods are {@code synchronized} since the underlying JCA
 * {@link Cipher} and {@link Mac} are stateful; callers may safely
 * invoke them from multiple threads, though typical RTP traffic for
 * one SSRC is single-threaded.
 */
final class SrtpRtpContext {
    /**
     * Length, in bytes, of the truncated HMAC-SHA1-80 auth tag.
     */
    private static final int AUTH_TAG_LEN = 10;

    /**
     * Length, in bytes, of the fixed RTP header (no CSRCs, no
     * extension). Variable-length headers add CSRC slots and an
     * optional extension block on top of this.
     */
    private static final int RTP_FIXED_HEADER_LEN = 12;

    /**
     * Mask isolating the CSRC count from the first byte of an RTP
     * header.
     */
    private static final int CC_MASK = 0x0F;

    /**
     * Mask of the X (extension-present) bit in the first byte of an
     * RTP header.
     */
    private static final int EXTENSION_BIT = 0x10;

    /**
     * Width, in bytes, of one CSRC slot.
     */
    private static final int CSRC_LEN = 4;

    /**
     * Width, in bytes, of the fixed extension-header preamble
     * (defined-by-profile + length).
     */
    private static final int EXTENSION_HEADER_LEN = 4;

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
     * AES cipher in CTR mode, re-IV'd per packet. Pre-instantiated
     * so the JCA provider lookup happens once.
     */
    private final Cipher aes;

    /**
     * HMAC-SHA1 instance, pre-keyed with
     * {@link SrtpDirection#srtpAuthKey}. JCA's {@link Mac#doFinal}
     * resets the instance to its initial keyed state, so a single
     * instance is reused across packets.
     */
    private final Mac hmac;

    /**
     * AES key spec for the SRTP session encryption key, cached so the
     * per-packet cipher init avoids re-allocating it.
     */
    private final SecretKeySpec encKeySpec;

    /**
     * Sender state: current 32-bit roll-over counter, treated as
     * unsigned via {@code long}.
     */
    private long senderRoc;

    /**
     * Sender state: last 16-bit sequence number sent, or {@code -1}
     * before the first packet. Used to detect SEQ wrap-around so the
     * ROC can be incremented.
     */
    private int lastSenderSeq = -1;

    /**
     * Receiver state: current 32-bit roll-over counter, treated as
     * unsigned via {@code long}.
     */
    private long receiverRoc;

    /**
     * Receiver state: highest 16-bit SEQ accepted ({@code s_l} per
     * RFC 3711 §3.3.1), or {@code -1} before any packet has been
     * authenticated.
     */
    private int receiverSl = -1;

    /**
     * Receiver state: replay-detection window keyed on the 48-bit
     * packet index.
     */
    private final SrtpReplayWindow replay = new SrtpReplayWindow();

    /**
     * Constructs a new context.
     *
     * @param direction the direction holding the derived session keys
     * @param ssrc      the SSRC the context is keyed on
     * @param sender    {@code true} for the sender side
     */
    SrtpRtpContext(SrtpDirection direction, int ssrc, boolean sender) {
        this.direction = direction;
        this.ssrc = ssrc;
        this.sender = sender;
        this.encKeySpec = new SecretKeySpec(direction.srtpEncKey, "AES");
        try {
            this.aes = Cipher.getInstance("AES/CTR/NoPadding");
            this.hmac = Mac.getInstance("HmacSHA1");
            this.hmac.init(new SecretKeySpec(direction.srtpAuthKey, "HmacSHA1"));
        } catch (GeneralSecurityException e) {
            throw new WhatsAppCallException.Srtp("failed to initialize SRTP context", e);
        }
    }

    /**
     * Encrypts and authenticates an outbound RTP packet. The header
     * (including any CSRC list and extension header) is left in
     * cleartext; the payload is encrypted with AES-128-CTR; a 10-byte
     * HMAC-SHA1 tag computed over (header || encrypted-payload || ROC)
     * is appended.
     *
     * @param rtp the plaintext RTP packet
     * @return the SRTP packet, length {@code rtp.length + 10}
     * @throws WhatsAppCallException.Srtp if the packet is malformed or encryption
     *                       fails
     */
    synchronized byte[] protect(byte[] rtp) {
        if (!sender) {
            throw new WhatsAppCallException.Srtp("protect called on a receiver context");
        }
        var headerLen = headerLength(rtp);

        var seq = DataUtils.getShort(rtp, 2, ByteOrder.BIG_ENDIAN) & 0xFFFF;
        if (lastSenderSeq != -1 && seq < lastSenderSeq && (lastSenderSeq - seq) > 32768) {
            senderRoc = (senderRoc + 1) & 0xFFFFFFFFL;
        }
        lastSenderSeq = seq;
        var packetIndex = (senderRoc << 16) | seq;

        var out = new byte[rtp.length + AUTH_TAG_LEN];
        System.arraycopy(rtp, 0, out, 0, headerLen);
        var payloadLen = rtp.length - headerLen;
        try {
            aes.init(Cipher.ENCRYPT_MODE, encKeySpec,
                    new IvParameterSpec(SrtpDirection.computeIv(direction.srtpSalt, ssrc, packetIndex)));
            aes.doFinal(rtp, headerLen, payloadLen, out, headerLen);
        } catch (GeneralSecurityException e) {
            throw new WhatsAppCallException.Srtp("SRTP encryption failed", e);
        }

        appendAuthTag(out, rtp.length, (int) senderRoc);
        return out;
    }

    /**
     * Authenticates and decrypts an inbound SRTP packet. The 10-byte
     * trailing tag is validated against an HMAC computed over
     * (header || encrypted-payload || candidate-ROC) before the
     * payload is decrypted.
     *
     * @param srtp the SRTP packet
     * @return the plaintext RTP packet
     * @throws WhatsAppCallException.Srtp if the packet is malformed, replays an
     *                       earlier index, or fails authentication
     */
    synchronized byte[] unprotect(byte[] srtp) {
        if (sender) {
            throw new WhatsAppCallException.Srtp("unprotect called on a sender context");
        }
        if (srtp.length < RTP_FIXED_HEADER_LEN + AUTH_TAG_LEN) {
            throw new WhatsAppCallException.Srtp("SRTP packet too short");
        }
        var dataLen = srtp.length - AUTH_TAG_LEN;
        var headerLen = headerLength(srtp);
        if (headerLen > dataLen) {
            throw new WhatsAppCallException.Srtp("SRTP header longer than packet");
        }

        var seq = DataUtils.getShort(srtp, 2, ByteOrder.BIG_ENDIAN) & 0xFFFF;
        var rocCandidate = guessRoc(seq);
        var packetIndex = (rocCandidate << 16) | (long) seq;

        if (!replay.check(packetIndex)) {
            throw new WhatsAppCallException.Srtp("SRTP replay detected at index " + packetIndex);
        }

        var expected = computeAuthTag(srtp, dataLen, (int) rocCandidate);
        if (!SrtpDirection.constantTimeEquals(expected, 0, srtp, dataLen, AUTH_TAG_LEN)) {
            throw new WhatsAppCallException.Srtp("SRTP auth failed");
        }

        var plain = new byte[dataLen];
        System.arraycopy(srtp, 0, plain, 0, headerLen);
        var payloadLen = dataLen - headerLen;
        try {
            aes.init(Cipher.DECRYPT_MODE, encKeySpec,
                    new IvParameterSpec(SrtpDirection.computeIv(direction.srtpSalt, ssrc, packetIndex)));
            aes.doFinal(srtp, headerLen, payloadLen, plain, headerLen);
        } catch (GeneralSecurityException e) {
            throw new WhatsAppCallException.Srtp("SRTP decryption failed", e);
        }

        replay.update(packetIndex);
        if (receiverSl == -1
                || rocCandidate > receiverRoc
                || (rocCandidate == receiverRoc && seq > receiverSl)) {
            receiverRoc = rocCandidate;
            receiverSl = seq;
        }
        return plain;
    }

    /**
     * Implements RFC 3711 §3.3.1 "guess of ROC" for an inbound
     * packet's sequence number, returning the candidate 32-bit
     * roll-over counter to combine with {@code seq}. The very first
     * received packet uses the initial ROC (zero) regardless of
     * {@code seq}; subsequent packets pick {@code ROC-1}, {@code ROC},
     * or {@code ROC+1} based on the highest accepted sequence number.
     *
     * @param seq the 16-bit sequence number from the RTP header
     * @return the candidate ROC, treated as unsigned 32-bit via
     *         {@code long}
     */
    private long guessRoc(int seq) {
        if (receiverSl == -1) {
            return receiverRoc;
        }
        if (receiverSl < 32768) {
            if (seq - receiverSl > 32768) {
                return receiverRoc == 0 ? 0xFFFFFFFFL : receiverRoc - 1;
            }
            return receiverRoc;
        }
        if (receiverSl - 32768 > seq) {
            return (receiverRoc + 1) & 0xFFFFFFFFL;
        }
        return receiverRoc;
    }

    /**
     * Computes the 10-byte authentication tag for an SRTP packet by
     * running HMAC-SHA1 over the first {@code dataLen} bytes followed
     * by the 32-bit ROC in big-endian order, truncated to
     * {@link #AUTH_TAG_LEN}.
     *
     * @param packet  the packet bytes (auth tag region not included
     *                in the HMAC input — only {@code dataLen} bytes)
     * @param dataLen the count of authenticated bytes from
     *                {@code packet}
     * @param roc     the 32-bit ROC
     * @return the 10-byte truncated tag
     */
    private byte[] computeAuthTag(byte[] packet, int dataLen, int roc) {
        hmac.update(packet, 0, dataLen);
        hmac.update((byte) (roc >>> 24));
        hmac.update((byte) (roc >>> 16));
        hmac.update((byte) (roc >>>  8));
        hmac.update((byte)  roc);
        var full = hmac.doFinal();
        var tag = new byte[AUTH_TAG_LEN];
        System.arraycopy(full, 0, tag, 0, AUTH_TAG_LEN);
        return tag;
    }

    /**
     * Computes the auth tag for the outbound packet currently in
     * {@code packet[0..dataLen)} and writes it into
     * {@code packet[dataLen..dataLen+AUTH_TAG_LEN)}.
     *
     * @param packet  the in-progress SRTP packet (room for the tag
     *                must already exist)
     * @param dataLen the length of the authenticated portion
     * @param roc     the 32-bit ROC
     */
    private void appendAuthTag(byte[] packet, int dataLen, int roc) {
        var tag = computeAuthTag(packet, dataLen, roc);
        System.arraycopy(tag, 0, packet, dataLen, AUTH_TAG_LEN);
    }

    /**
     * Returns the byte length of the RTP header (12 bytes plus any
     * CSRC list, plus an extension header if the X bit is set).
     *
     * @param rtp the RTP packet
     * @return the header length in bytes
     * @throws WhatsAppCallException.Srtp if the packet is shorter than the header
     *                       it advertises
     */
    private static int headerLength(byte[] rtp) {
        if (rtp.length < RTP_FIXED_HEADER_LEN) {
            throw new WhatsAppCallException.Srtp("RTP packet shorter than fixed header");
        }
        var cc = rtp[0] & CC_MASK;
        var hasExtension = (rtp[0] & EXTENSION_BIT) != 0;
        var len = RTP_FIXED_HEADER_LEN + cc * CSRC_LEN;
        if (hasExtension) {
            if (rtp.length < len + EXTENSION_HEADER_LEN) {
                throw new WhatsAppCallException.Srtp("RTP extension header truncated");
            }
            var extLen = DataUtils.getShort(rtp, len + 2, ByteOrder.BIG_ENDIAN) & 0xFFFF;
            len += EXTENSION_HEADER_LEN + extLen * CSRC_LEN;
        }
        if (rtp.length < len) {
            throw new WhatsAppCallException.Srtp("RTP packet shorter than full header");
        }
        return len;
    }
}
