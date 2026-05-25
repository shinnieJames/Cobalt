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
 * Protects and unprotects SRTP packets for one SSRC in one direction under the
 * {@code SRTP_AES128_CM_HMAC_SHA1_80} profile (RFC 3711 section 4.1).
 *
 * <p>The context uses AES-128 in counter mode for confidentiality and HMAC-SHA1 truncated to
 * 80 bits for authentication. A single context serves either the sender or the receiver side: a
 * sender tracks the 32-bit roll-over counter (ROC) that extends the 16-bit RTP sequence number
 * into a 48-bit packet index, while a receiver reconstructs the sender's index per RFC 3711
 * section 3.3.1 and rejects replays via a {@link SrtpReplayWindow}.
 *
 * <p>The protect and unprotect methods are {@code synchronized} because the underlying JCA
 * {@link Cipher} and {@link Mac} carry per-operation state; callers may invoke them from multiple
 * threads, although typical RTP traffic for one SSRC arrives on a single thread.
 */
final class SrtpRtpContext {
    /**
     * Holds the length, in bytes, of the truncated HMAC-SHA1-80 authentication tag.
     */
    private static final int AUTH_TAG_LEN = 10;

    /**
     * Holds the length, in bytes, of the fixed RTP header with no CSRCs and no extension.
     *
     * <p>Variable-length headers add CSRC slots and an optional extension block on top of this
     * fixed prefix.
     */
    private static final int RTP_FIXED_HEADER_LEN = 12;

    /**
     * Holds the mask that isolates the CSRC count from the first byte of an RTP header.
     */
    private static final int CC_MASK = 0x0F;

    /**
     * Holds the mask of the X (extension-present) bit in the first byte of an RTP header.
     */
    private static final int EXTENSION_BIT = 0x10;

    /**
     * Holds the width, in bytes, of one CSRC slot.
     */
    private static final int CSRC_LEN = 4;

    /**
     * Holds the width, in bytes, of the fixed extension-header preamble (the defined-by-profile
     * field plus the length field).
     */
    private static final int EXTENSION_HEADER_LEN = 4;

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
     *
     * <p>It is created once at construction so the JCA provider lookup happens a single time.
     */
    private final Cipher aes;

    /**
     * Holds the HMAC-SHA1 instance, pre-keyed with {@link SrtpDirection#srtpAuthKey}.
     *
     * <p>{@link Mac#doFinal} resets the instance to its initial keyed state, so the single instance
     * is reused across packets.
     */
    private final Mac hmac;

    /**
     * Holds the AES key spec for the SRTP session encryption key.
     *
     * <p>It is cached so the per-packet cipher initialization does not re-allocate it.
     */
    private final SecretKeySpec encKeySpec;

    /**
     * Holds the sender-side 32-bit roll-over counter, treated as unsigned by widening to
     * {@code long}.
     */
    private long senderRoc;

    /**
     * Holds the last 16-bit sequence number sent, or {@code -1} before the first packet.
     *
     * <p>It is used to detect sequence-number wrap-around so the sender ROC can be incremented.
     */
    private int lastSenderSeq = -1;

    /**
     * Holds the receiver-side 32-bit roll-over counter, treated as unsigned by widening to
     * {@code long}.
     */
    private long receiverRoc;

    /**
     * Holds the highest 16-bit sequence number accepted ({@code s_l} per RFC 3711 section 3.3.1),
     * or {@code -1} before any packet has been authenticated.
     */
    private int receiverSl = -1;

    /**
     * Holds the receiver-side replay-detection window keyed on the 48-bit packet index.
     */
    private final SrtpReplayWindow replay = new SrtpReplayWindow();

    /**
     * Constructs a context for one SSRC and direction, initializing the AES cipher and the
     * pre-keyed HMAC instance.
     *
     * @param direction the direction holding the derived session keys
     * @param ssrc      the SSRC this context is keyed on
     * @param sender    {@code true} for the sender side, {@code false} for the receiver side
     * @throws WhatsAppCallException.Srtp if the JCA cipher or MAC cannot be initialized
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
     * Encrypts and authenticates an outbound RTP packet.
     *
     * <p>The header, including any CSRC list and extension header, is copied in cleartext; the
     * payload is encrypted with AES-128-CTR keyed on the per-packet initial counter. The sequence
     * number is inspected to advance the ROC across wrap-around, and a 10-byte HMAC-SHA1 tag over
     * {@code (header || encrypted-payload || ROC)} is appended.
     *
     * @param rtp the plaintext RTP packet
     * @return the SRTP packet, of length {@code rtp.length + 10}
     * @throws WhatsAppCallException.Srtp if called on a receiver context, if the packet is
     *                                    malformed, or if encryption fails
     * @implNote This implementation treats a backward sequence-number jump of more than 32768 as a
     *           16-bit wrap-around and increments the ROC, matching the half-sequence-space
     *           heuristic of RFC 3711 section 3.3.1.
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
     * Authenticates and decrypts an inbound SRTP packet.
     *
     * <p>The candidate ROC is guessed from the sequence number, the resulting 48-bit packet index
     * is replay-checked, and the trailing 10-byte tag is validated in constant time against an HMAC
     * over {@code (header || encrypted-payload || candidate-ROC)} before the payload is decrypted.
     * On success the replay window and the receiver's highest-sequence state are advanced.
     *
     * @param srtp the SRTP packet
     * @return the plaintext RTP packet
     * @throws WhatsAppCallException.Srtp if called on a sender context, if the packet is malformed,
     *                                    if it replays an earlier index, if authentication fails, or
     *                                    if decryption fails
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
     * Guesses the roll-over counter for an inbound packet's sequence number per RFC 3711
     * section 3.3.1.
     *
     * <p>The first received packet uses the initial ROC of zero regardless of {@code seq}.
     * Subsequent packets pick {@code ROC-1}, {@code ROC}, or {@code ROC+1} by comparing {@code seq}
     * against the highest accepted sequence number, using the half-sequence-space threshold of
     * 32768 to decide whether a wrap-around has occurred.
     *
     * @param seq the 16-bit sequence number from the RTP header
     * @return the candidate ROC, treated as unsigned 32-bit by widening to {@code long}
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
     * Computes the truncated authentication tag for an SRTP packet.
     *
     * <p>HMAC-SHA1 is run over the first {@code dataLen} bytes of the packet followed by the 32-bit
     * ROC in big-endian order, and the leading {@link #AUTH_TAG_LEN} bytes of the output are
     * returned as the tag.
     *
     * @param packet  the packet bytes; only the leading {@code dataLen} bytes feed the HMAC, so the
     *                authentication-tag region is excluded
     * @param dataLen the count of authenticated leading bytes
     * @param roc     the 32-bit roll-over counter to append to the HMAC input
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
     * Computes and writes the authentication tag for the outbound packet held in
     * {@code packet[0..dataLen)} into {@code packet[dataLen..dataLen + AUTH_TAG_LEN)}.
     *
     * @param packet  the in-progress SRTP packet, which must already have room for the tag
     * @param dataLen the length of the authenticated portion
     * @param roc     the 32-bit roll-over counter to append to the HMAC input
     */
    private void appendAuthTag(byte[] packet, int dataLen, int roc) {
        var tag = computeAuthTag(packet, dataLen, roc);
        System.arraycopy(tag, 0, packet, dataLen, AUTH_TAG_LEN);
    }

    /**
     * Returns the byte length of the RTP header, accounting for the CSRC list and an optional
     * extension block.
     *
     * <p>The length is the 12-byte fixed header plus one 4-byte slot per CSRC indicated by the CC
     * field, plus, when the X bit is set, the 4-byte extension preamble and the extension words it
     * advertises.
     *
     * @param rtp the RTP packet
     * @return the header length in bytes
     * @throws WhatsAppCallException.Srtp if the packet is shorter than the fixed header, if its
     *                                    extension preamble is truncated, or if it is shorter than
     *                                    the full header it advertises
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
