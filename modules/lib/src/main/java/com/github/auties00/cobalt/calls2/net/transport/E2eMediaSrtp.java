package com.github.auties00.cobalt.calls2.net.transport;

import com.github.auties00.cobalt.exception.WhatsAppCallException;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Protects and unprotects one-to-one call media with the end-to-end SRTP transform the relay forwards
 * opaquely, keyed by the per-participant master derived from the call key.
 *
 * <p>A one-to-one relay call does not hop-by-hop SRTP its media: the relay is a blind forwarder, so the
 * media on the wire is the end-to-end SRTP of {@code transport_srtp.cc} rather than the relay-hop SRTP of
 * {@code wa_hbh_srtp_relay.cc}. Each direction is keyed by a 30-byte master derived from the 32-byte call
 * key and the sending device's JID (the sender keys its outbound stream from its own device JID, the
 * receiver keys a peer's inbound stream from that peer's device JID) through
 * {@code CallE2eKeyDerivation.deriveSrtpMaster}. The master is expanded into the RFC 3711 AES-CM session
 * cipher key, salt, and HMAC-SHA1 authentication key; the payload after the RTP header is encrypted with
 * AES-128 counter mode and a {@value #WARP_MI_TAG_LENGTH}-byte WARP message-integrity tag is appended.
 *
 * <p>The receiver does not verify the WARP-MI tag, only strips it, matching the engine; the rollover
 * counter is tracked per synchronization source and estimated from sequence-number wraparound. One context
 * holds one direction's key set: a media session builds one from the local device's master to
 * {@link #protectRtp(byte[], int) protect} outbound packets and one from the peer device's master to
 * {@link #unprotectRtp(byte[], int) unprotect} inbound packets.
 *
 * @implNote This implementation reproduces {@code transport_srtp.cc} and is byte-verified against a live
 *           one-to-one call: the captured inbound packets' four-byte WARP-MI tags reproduce as
 *           {@code HMAC-SHA1(authKey, packet-without-tag || ROC) [0..4]} with {@code authKey} expanded from
 *           {@code deriveSrtpMaster(callKey, peerDeviceJid)} and {@code ROC = 0}. The session-key expansion
 *           is the RFC 3711 AES-CM key-derivation function with label {@code 0x00} for the cipher key,
 *           {@code 0x01} for the authentication key, and {@code 0x02} for the salt; the per-packet counter
 *           nonce places the salt in the high fourteen bytes and exclusive-ors the synchronization source
 *           and the forty-eight-bit packet index in.
 */
public final class E2eMediaSrtp {
    /**
     * The length, in bytes, of the trailing WARP message-integrity tag appended to each media packet.
     */
    public static final int WARP_MI_TAG_LENGTH = 4;

    /**
     * The length, in bytes, of the derived hop-by-hop SRTP master (a sixteen-byte key plus fourteen-byte
     * salt).
     */
    private static final int MASTER_LENGTH = 30;

    /**
     * The fixed RTP header length, in bytes, before any contributing-source identifiers or header extension.
     */
    private static final int RTP_FIXED_HEADER_LENGTH = 12;

    /**
     * The RFC 3711 AES-CM key-derivation label selecting the session cipher key.
     */
    private static final int LABEL_CIPHER = 0x00;

    /**
     * The RFC 3711 AES-CM key-derivation label selecting the session authentication key.
     */
    private static final int LABEL_AUTH = 0x01;

    /**
     * The RFC 3711 AES-CM key-derivation label selecting the session salt.
     */
    private static final int LABEL_SALT = 0x02;

    /**
     * Holds the sixteen-byte AES-128 session cipher key.
     */
    private final byte[] cipherKey;

    /**
     * Holds the twenty-byte HMAC-SHA1 session authentication key keying the WARP-MI tag.
     */
    private final byte[] authKey;

    /**
     * Holds the fourteen-byte session salt seeding the per-packet counter nonce.
     */
    private final byte[] salt;

    /**
     * Holds the immutable AES-128 session key specification, shared safely across threads.
     */
    private final SecretKeySpec cipherKeySpec;

    /**
     * Holds the immutable HMAC-SHA1 session key specification, shared safely across threads.
     */
    private final SecretKeySpec authKeySpec;

    /**
     * Holds the per-thread AES counter-mode engine reused across packets.
     *
     * <p>The outbound {@link #protectRtp(byte[], int)} path is driven by both the audio capture-pump
     * thread and the video-encode thread and {@link #cryptPayload} is shared with the inbound path, so
     * the mutable JCA engine is thread-confined rather than a shared field to avoid a data race.
     */
    private final ThreadLocal<Cipher> ctrCipher;

    /**
     * Holds the per-thread HMAC-SHA1 engine reused across packets for the outbound WARP-MI tag.
     */
    private final ThreadLocal<Mac> warpMac;

    /**
     * Holds the per-thread sixteen-byte counter-nonce scratch reused across packets.
     */
    private final ThreadLocal<byte[]> nonceScratch;

    /**
     * Holds the per-synchronization-source inbound rollover-counter state used to estimate the
     * forty-eight-bit packet index of an unprotected packet.
     */
    private final Map<Integer, RolloverState> inboundRoc = new HashMap<>();

    /**
     * Holds the single outbound rollover-counter state used to stamp the packet index of a protected
     * packet.
     */
    private final RolloverState outboundRoc = new RolloverState();

    /**
     * Constructs an end-to-end SRTP context from a derived per-participant SRTP master.
     *
     * @param master the {@value #MASTER_LENGTH}-byte SRTP master from
     *               {@code CallE2eKeyDerivation.deriveSrtpMaster}
     * @throws NullPointerException       if {@code master} is {@code null}
     * @throws IllegalArgumentException   if {@code master} is not exactly {@value #MASTER_LENGTH} bytes long
     * @throws WhatsAppCallException.Srtp if the platform cannot run AES or HMAC-SHA1
     */
    public E2eMediaSrtp(byte[] master) {
        Objects.requireNonNull(master, "master cannot be null");
        if (master.length != MASTER_LENGTH) {
            throw new IllegalArgumentException("master must be " + MASTER_LENGTH + " bytes, got " + master.length);
        }
        var masterKey = Arrays.copyOfRange(master, 0, 16);
        var masterSalt = Arrays.copyOfRange(master, 16, MASTER_LENGTH);
        this.cipherKey = deriveSessionBytes(masterKey, masterSalt, LABEL_CIPHER, 16);
        this.authKey = deriveSessionBytes(masterKey, masterSalt, LABEL_AUTH, 20);
        this.salt = deriveSessionBytes(masterKey, masterSalt, LABEL_SALT, 14);
        this.cipherKeySpec = new SecretKeySpec(cipherKey, "AES");
        this.authKeySpec = new SecretKeySpec(authKey, "HmacSHA1");
        this.ctrCipher = ThreadLocal.withInitial(() -> {
            try {
                return Cipher.getInstance("AES/CTR/NoPadding");
            } catch (Exception exception) {
                throw new WhatsAppCallException.Srtp("end-to-end SRTP AES-CTR unavailable", exception);
            }
        });
        this.warpMac = ThreadLocal.withInitial(() -> {
            try {
                return Mac.getInstance("HmacSHA1");
            } catch (Exception exception) {
                throw new WhatsAppCallException.Srtp("end-to-end SRTP WARP-MI HMAC unavailable", exception);
            }
        });
        this.nonceScratch = ThreadLocal.withInitial(() -> new byte[16]);
    }

    /**
     * Encrypts an RTP packet in place toward the peer and appends the WARP message-integrity tag.
     *
     * <p>The RTP header is left in the clear; the payload after it is encrypted with AES-128 counter mode
     * under the per-packet nonce built from this context's salt, the packet's synchronization source, and
     * the outbound rollover counter, and the {@value #WARP_MI_TAG_LENGTH}-byte tag is appended. The buffer
     * MUST have at least {@value #WARP_MI_TAG_LENGTH} trailing bytes free.
     *
     * @param packet the buffer holding the cleartext RTP packet, with trailing room for the tag
     * @param length the length, in bytes, of the cleartext RTP packet
     * @return the length, in bytes, of the protected packet ({@code length} grown by
     *         {@value #WARP_MI_TAG_LENGTH})
     * @throws NullPointerException       if {@code packet} is {@code null}
     * @throws IllegalArgumentException   if {@code length} is not a valid RTP packet within {@code packet}
     * @throws WhatsAppCallException.Srtp if the AES or HMAC-SHA1 computation fails
     */
    public int protectRtp(byte[] packet, int length) {
        Objects.requireNonNull(packet, "packet cannot be null");
        var headerLength = rtpHeaderLength(packet, length);
        var ssrc = readSsrc(packet);
        var sequence = ((packet[2] & 0xFF) << 8) | (packet[3] & 0xFF);
        var roc = outboundRoc.advance(sequence);
        cryptPayload(packet, headerLength, length - headerLength, ssrc, roc, sequence);
        var tag = warpMiTag(packet, length, roc);
        System.arraycopy(tag, 0, packet, length, WARP_MI_TAG_LENGTH);
        return length + WARP_MI_TAG_LENGTH;
    }

    /**
     * Decrypts an RTP packet in place that arrived from the peer, stripping the WARP message-integrity tag.
     *
     * <p>The trailing {@value #WARP_MI_TAG_LENGTH}-byte WARP-MI tag is stripped without verification (the
     * engine does not authenticate inbound media at this layer); the payload after the RTP header is then
     * decrypted with AES-128 counter mode under the per-packet nonce built from this context's salt, the
     * packet's synchronization source, and the per-source rollover counter estimated from the sequence
     * number.
     *
     * @param packet the buffer holding the protected RTP packet
     * @param length the length, in bytes, of the protected RTP packet
     * @return the length, in bytes, of the cleartext RTP packet ({@code length} shrunk by
     *         {@value #WARP_MI_TAG_LENGTH})
     * @throws NullPointerException       if {@code packet} is {@code null}
     * @throws IllegalArgumentException   if {@code length} is not a valid protected RTP packet within
     *                                    {@code packet}
     * @throws WhatsAppCallException.Srtp if the AES computation fails
     */
    public int unprotectRtp(byte[] packet, int length) {
        Objects.requireNonNull(packet, "packet cannot be null");
        if (length < RTP_FIXED_HEADER_LENGTH + WARP_MI_TAG_LENGTH) {
            throw new IllegalArgumentException("protected packet too short: " + length);
        }
        var payloadEnd = length - WARP_MI_TAG_LENGTH;
        var headerLength = rtpHeaderLength(packet, payloadEnd);
        var ssrc = readSsrc(packet);
        var sequence = ((packet[2] & 0xFF) << 8) | (packet[3] & 0xFF);
        var roc = inboundRoc.computeIfAbsent(ssrc, _ -> new RolloverState()).advance(sequence);
        cryptPayload(packet, headerLength, payloadEnd - headerLength, ssrc, roc, sequence);
        return payloadEnd;
    }

    /**
     * Encrypts or decrypts a packet's payload in place with AES-128 counter mode.
     *
     * <p>Counter mode is symmetric, so the same call both encrypts and decrypts; the per-packet counter
     * nonce is built by {@link #buildNonce(int, int, int)} from this context's salt and the packet's
     * synchronization source and index.
     *
     * @param packet     the buffer holding the packet
     * @param offset     the offset of the payload within {@code packet}
     * @param payloadLen the length, in bytes, of the payload to transform
     * @param ssrc       the packet's synchronization source
     * @param roc        the packet's rollover counter
     * @param sequence   the packet's sixteen-bit RTP sequence number
     * @throws WhatsAppCallException.Srtp if the AES computation fails
     */
    private void cryptPayload(byte[] packet, int offset, int payloadLen, int ssrc, int roc, int sequence) {
        if (payloadLen <= 0) {
            return;
        }
        try {
            var cipher = ctrCipher.get();
            cipher.init(Cipher.ENCRYPT_MODE, cipherKeySpec,
                    new IvParameterSpec(buildNonce(ssrc, roc, sequence)));
            // TODO: the doFinal output array is still allocated per packet; reusing it in place would need
            //  a five-argument doFinal into the same buffer, whose overlap behaviour is not proven safe
            //  across JCA providers, so it is left allocating to stay strictly behaviour-preserving.
            var transformed = cipher.doFinal(packet, offset, payloadLen);
            System.arraycopy(transformed, 0, packet, offset, transformed.length);
        } catch (Exception exception) {
            throw new WhatsAppCallException.Srtp("end-to-end SRTP AES-CTR failed", exception);
        }
    }

    /**
     * Builds the sixteen-byte AES counter nonce for a packet.
     *
     * <p>The salt occupies the high fourteen bytes; the synchronization source is exclusive-ored into bytes
     * four through seven and the forty-eight-bit packet index ({@code (roc << 16) | sequence}) into bytes
     * eight through thirteen; the low two bytes are the AES-CM block counter and start at zero.
     *
     * @param ssrc     the packet's synchronization source
     * @param roc      the packet's rollover counter
     * @param sequence the packet's sixteen-bit RTP sequence number
     * @return the sixteen-byte counter nonce
     */
    private byte[] buildNonce(int ssrc, int roc, int sequence) {
        var nonce = nonceScratch.get();
        System.arraycopy(salt, 0, nonce, 0, 14);
        nonce[4] ^= (byte) (ssrc >>> 24);
        nonce[5] ^= (byte) (ssrc >>> 16);
        nonce[6] ^= (byte) (ssrc >>> 8);
        nonce[7] ^= (byte) ssrc;
        var packetIndex = ((long) (roc & 0xFFFFFFFFL) << 16) | (sequence & 0xFFFFL);
        nonce[8] ^= (byte) (packetIndex >>> 40);
        nonce[9] ^= (byte) (packetIndex >>> 32);
        nonce[10] ^= (byte) (packetIndex >>> 24);
        nonce[11] ^= (byte) (packetIndex >>> 16);
        nonce[12] ^= (byte) (packetIndex >>> 8);
        nonce[13] ^= (byte) packetIndex;
        return nonce;
    }

    /**
     * Computes the WARP message-integrity tag over a packet.
     *
     * <p>The tag is the first {@value #WARP_MI_TAG_LENGTH} bytes of
     * {@code HMAC-SHA1(authKey, packet[0..length] || rocBigEndian)}.
     *
     * @param packet the buffer holding the packet whose first {@code length} bytes are covered
     * @param length the number of leading bytes of {@code packet} the tag covers
     * @param roc    the packet's rollover counter, appended big-endian to the authenticated bytes
     * @return the {@value #WARP_MI_TAG_LENGTH}-byte WARP-MI tag
     * @throws WhatsAppCallException.Srtp if the HMAC-SHA1 computation fails
     */
    private byte[] warpMiTag(byte[] packet, int length, int roc) {
        try {
            var mac = warpMac.get();
            mac.init(authKeySpec);
            mac.update(packet, 0, length);
            mac.update(new byte[]{(byte) (roc >>> 24), (byte) (roc >>> 16), (byte) (roc >>> 8), (byte) roc});
            return Arrays.copyOf(mac.doFinal(), WARP_MI_TAG_LENGTH);
        } catch (Exception exception) {
            throw new WhatsAppCallException.Srtp("end-to-end SRTP WARP-MI HMAC failed", exception);
        }
    }

    /**
     * Returns the RTP header length, in bytes, of a packet, accounting for contributing-source identifiers
     * and a one- or two-byte header extension.
     *
     * @param packet the buffer holding the RTP packet
     * @param length the length, in bytes, of the RTP packet
     * @return the header length, in bytes
     * @throws IllegalArgumentException if the header does not fit within {@code length}
     */
    private static int rtpHeaderLength(byte[] packet, int length) {
        if (length < RTP_FIXED_HEADER_LENGTH) {
            throw new IllegalArgumentException("RTP packet too short: " + length);
        }
        var csrcCount = packet[0] & 0x0F;
        var headerLength = RTP_FIXED_HEADER_LENGTH + csrcCount * 4;
        var extensionPresent = (packet[0] & 0x10) != 0;
        if (extensionPresent) {
            if (length < headerLength + 4) {
                throw new IllegalArgumentException("RTP extension header does not fit: " + length);
            }
            var extensionWords = ((packet[headerLength + 2] & 0xFF) << 8) | (packet[headerLength + 3] & 0xFF);
            headerLength += 4 + extensionWords * 4;
        }
        if (length < headerLength) {
            throw new IllegalArgumentException("RTP header does not fit: " + length);
        }
        return headerLength;
    }

    /**
     * Reads the thirty-two-bit synchronization source from an RTP packet.
     *
     * @param packet the buffer holding the RTP packet
     * @return the synchronization source
     */
    private static int readSsrc(byte[] packet) {
        return ((packet[8] & 0xFF) << 24) | ((packet[9] & 0xFF) << 16)
                | ((packet[10] & 0xFF) << 8) | (packet[11] & 0xFF);
    }

    /**
     * Expands one RFC 3711 AES-CM session value from a master key and salt.
     *
     * <p>The counter is the master salt in its high fourteen bytes with the label exclusive-ored into byte
     * seven; AES-128 counter mode over zero bytes yields the keystream that is the session value.
     *
     * @param masterKey  the sixteen-byte master key
     * @param masterSalt the fourteen-byte master salt
     * @param label      the key-derivation label
     * @param length     the session value length, in bytes
     * @return the {@code length}-byte session value
     * @throws WhatsAppCallException.Srtp if the AES computation fails
     */
    private static byte[] deriveSessionBytes(byte[] masterKey, byte[] masterSalt, int label, int length) {
        var counter = new byte[16];
        System.arraycopy(masterSalt, 0, counter, 0, 14);
        counter[7] ^= (byte) label;
        try {
            var cipher = Cipher.getInstance("AES/CTR/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(masterKey, "AES"), new IvParameterSpec(counter));
            return cipher.doFinal(new byte[length]);
        } catch (Exception exception) {
            throw new WhatsAppCallException.Srtp("end-to-end SRTP session-key derivation failed", exception);
        }
    }

    /**
     * Tracks a rollover counter and the last sequence number to estimate the forty-eight-bit packet index
     * across the sixteen-bit RTP sequence space.
     */
    private static final class RolloverState {
        /**
         * Holds the current rollover counter.
         */
        private int roc;

        /**
         * Holds the last sequence number observed, or {@code -1} before the first packet.
         */
        private int lastSequence = -1;

        /**
         * Advances the rollover counter for a newly observed sequence number and returns the counter to use
         * for that packet.
         *
         * <p>The first packet seeds the state and returns the current counter; a later packet whose sequence
         * number jumped backward by more than half the sequence space is treated as a wraparound and
         * increments the counter.
         *
         * @param sequence the packet's sixteen-bit RTP sequence number
         * @return the rollover counter for this packet
         */
        private int advance(int sequence) {
            if (lastSequence < 0) {
                lastSequence = sequence;
                return roc;
            }
            if (((short) (sequence - lastSequence)) < -0x4000 && sequence < lastSequence) {
                roc++;
            }
            lastSequence = sequence;
            return roc;
        }
    }
}
