package com.github.auties00.cobalt.call.internal.rtp.srtp;

import com.github.auties00.cobalt.exception.WhatsAppCallException;

import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Protects outbound RTP and RTCP packets and unprotects inbound ones for one DTLS-SRTP
 * association.
 *
 * <p>The endpoint is keyed on the master key and salt material exported from a completed DTLS-SRTP
 * handshake (RFC 5764). It hard-codes the {@code SRTP_AES128_CM_HMAC_SHA1_80} profile, which is the
 * WebRTC default that WhatsApp's wasm engine negotiates. The 60-byte exported keying material is
 * split per RFC 5764 section 4.2 into a client and a server master key followed by a client and a
 * server master salt:
 *
 * {@snippet :
 *   bytes[0  .. 16]  = client_write_SRTP_master_key   (16)
 *   bytes[16 .. 32]  = server_write_SRTP_master_key   (16)
 *   bytes[32 .. 46]  = client_write_SRTP_master_salt  (14)
 *   bytes[46 .. 60]  = server_write_SRTP_master_salt  (14)
 * }
 *
 * <p>Each direction (outbound and inbound) owns its own {@link SrtpDirection} carrying the derived
 * session keys. Per-SSRC {@link SrtpRtpContext} and {@link SrtpRtcpContext} instances are created
 * lazily on first use of an SSRC and cached in {@link ConcurrentHashMap} maps, so the endpoint is
 * safe to use from multiple threads.
 *
 * @implNote This implementation is pure Java, building only on the JDK's AES-CTR and HMAC-SHA1
 *           primitives, so SRTP packet protection requires no third-party SRTP library or
 *           BouncyCastle.
 */
public final class SrtpEndpoint implements AutoCloseable {
    /**
     * Holds the length, in bytes, of an SRTP master key (AES-128).
     */
    private static final int MASTER_KEY_LENGTH = 16;

    /**
     * Holds the length, in bytes, of an SRTP master salt.
     */
    private static final int MASTER_SALT_LENGTH = 14;

    /**
     * Holds the total length of the DTLS-SRTP exported keying material, which is
     * {@code 2 * (MASTER_KEY_LENGTH + MASTER_SALT_LENGTH)}.
     */
    public static final int KEYING_MATERIAL_LENGTH =
            2 * (MASTER_KEY_LENGTH + MASTER_SALT_LENGTH);

    /**
     * Holds the minimum size of a valid SRTP packet, which is the 12-byte fixed RTP header plus the
     * 10-byte authentication tag.
     */
    private static final int MIN_SRTP_LENGTH = 22;

    /**
     * Holds the minimum size of a valid RTP packet, which is the 12-byte fixed header.
     */
    private static final int MIN_RTP_LENGTH = 12;

    /**
     * Holds the outbound (sender-side) derived session keys.
     */
    private final SrtpDirection outbound;

    /**
     * Holds the inbound (receiver-side) derived session keys.
     */
    private final SrtpDirection inbound;

    /**
     * Caches the per-SSRC outbound RTP contexts, created lazily on first use of each SSRC.
     */
    private final ConcurrentHashMap<Integer, SrtpRtpContext> outboundRtp = new ConcurrentHashMap<>();

    /**
     * Caches the per-SSRC inbound RTP contexts, created lazily on first use of each SSRC.
     */
    private final ConcurrentHashMap<Integer, SrtpRtpContext> inboundRtp = new ConcurrentHashMap<>();

    /**
     * Caches the per-SSRC outbound RTCP contexts, created lazily on first use of each SSRC.
     */
    private final ConcurrentHashMap<Integer, SrtpRtcpContext> outboundRtcp = new ConcurrentHashMap<>();

    /**
     * Caches the per-SSRC inbound RTCP contexts, created lazily on first use of each SSRC.
     */
    private final ConcurrentHashMap<Integer, SrtpRtcpContext> inboundRtcp = new ConcurrentHashMap<>();

    /**
     * Constructs an endpoint from the already-split outbound and inbound master keys and salts.
     *
     * @param outboundKey  the outbound 16-byte master key
     * @param outboundSalt the outbound 14-byte master salt
     * @param inboundKey   the inbound 16-byte master key
     * @param inboundSalt  the inbound 14-byte master salt
     */
    private SrtpEndpoint(byte[] outboundKey, byte[] outboundSalt,
                         byte[] inboundKey, byte[] inboundSalt) {
        this.outbound = new SrtpDirection(outboundKey, outboundSalt);
        this.inbound = new SrtpDirection(inboundKey, inboundSalt);
    }

    /**
     * Builds an endpoint from the 60-byte DTLS-SRTP exported keying material, assigning the
     * outbound and inbound halves according to the local DTLS role.
     *
     * <p>The material is split per RFC 5764 section 4.2 into the client and server master keys and
     * salts. When {@code role} is {@link SrtpRole#CLIENT}, the {@code client_write} pair becomes the
     * outbound direction and the {@code server_write} pair the inbound direction; the assignment is
     * reversed for {@link SrtpRole#SERVER}.
     *
     * @param keyingMaterial the 60-byte block exported from the DTLS session via the
     *                       {@code "EXTRACTOR-dtls_srtp"} label
     * @param role           the local DTLS role, which determines which half of
     *                       {@code keyingMaterial} is outbound and which is inbound
     * @return a fresh endpoint keyed on the derived session material
     * @throws NullPointerException     if {@code keyingMaterial} or {@code role} is {@code null}
     * @throws IllegalArgumentException if {@code keyingMaterial} is not exactly
     *                                  {@link #KEYING_MATERIAL_LENGTH} bytes long
     */
    public static SrtpEndpoint fromDtlsKeyingMaterial(byte[] keyingMaterial, SrtpRole role) {
        Objects.requireNonNull(keyingMaterial, "keyingMaterial cannot be null");
        Objects.requireNonNull(role, "role cannot be null");
        if (keyingMaterial.length != KEYING_MATERIAL_LENGTH) {
            throw new IllegalArgumentException(
                    "keyingMaterial must be " + KEYING_MATERIAL_LENGTH
                            + " bytes, got " + keyingMaterial.length);
        }
        var clientKey = Arrays.copyOfRange(keyingMaterial, 0, 16);
        var serverKey = Arrays.copyOfRange(keyingMaterial, 16, 32);
        var clientSalt = Arrays.copyOfRange(keyingMaterial, 32, 46);
        var serverSalt = Arrays.copyOfRange(keyingMaterial, 46, 60);
        return switch (role) {
            case CLIENT -> new SrtpEndpoint(clientKey, clientSalt, serverKey, serverSalt);
            case SERVER -> new SrtpEndpoint(serverKey, serverSalt, clientKey, clientSalt);
        };
    }

    /**
     * Encrypts and authenticates an outbound RTP packet.
     *
     * <p>The SSRC is read from bytes 8 through 11 of the packet, the matching outbound
     * {@link SrtpRtpContext} is created if needed, and the context transforms the packet. The
     * returned array is a fresh copy that is longer than the input by the 10-byte authentication
     * tag.
     *
     * @param rtpPacket the plaintext RTP packet
     * @return the SRTP packet, of length {@code rtpPacket.length + 10}
     * @throws NullPointerException       if {@code rtpPacket} is {@code null}
     * @throws IllegalArgumentException   if the packet is shorter than the 12-byte RTP header
     * @throws WhatsAppCallException.Srtp if the SRTP transformation fails
     */
    public byte[] protectRtp(byte[] rtpPacket) {
        Objects.requireNonNull(rtpPacket, "rtpPacket cannot be null");
        if (rtpPacket.length < MIN_RTP_LENGTH) {
            throw new IllegalArgumentException(
                    "rtpPacket too short for RTP header: " + rtpPacket.length);
        }
        var ssrc = readInt(rtpPacket, 8);
        return outboundRtp
                .computeIfAbsent(ssrc, s -> new SrtpRtpContext(outbound, s, true))
                .protect(rtpPacket);
    }

    /**
     * Authenticates and decrypts an inbound SRTP packet.
     *
     * <p>The SSRC is read from bytes 8 through 11 of the packet, the matching inbound
     * {@link SrtpRtpContext} is created if needed, and the context validates and decrypts the
     * packet. The returned array is a fresh copy that is shorter than the input by the 10-byte
     * authentication tag.
     *
     * @param srtpPacket the SRTP packet
     * @return the plaintext RTP packet
     * @throws NullPointerException       if {@code srtpPacket} is {@code null}
     * @throws IllegalArgumentException   if the packet is shorter than the 12-byte RTP header plus
     *                                    the 10-byte authentication tag
     * @throws WhatsAppCallException.Srtp if authentication fails or a replay is detected
     */
    public byte[] unprotectRtp(byte[] srtpPacket) {
        Objects.requireNonNull(srtpPacket, "srtpPacket cannot be null");
        if (srtpPacket.length < MIN_SRTP_LENGTH) {
            throw new IllegalArgumentException(
                    "srtpPacket too short for header + auth tag: " + srtpPacket.length);
        }
        var ssrc = readInt(srtpPacket, 8);
        return inboundRtp
                .computeIfAbsent(ssrc, s -> new SrtpRtpContext(inbound, s, false))
                .unprotect(srtpPacket);
    }

    /**
     * Encrypts and authenticates an outbound RTCP packet for the given sender SSRC.
     *
     * <p>The matching outbound {@link SrtpRtcpContext} is created if needed, and the context
     * transforms the packet. The returned array is a fresh copy that is longer than the input by
     * the 4-byte SRTCP index field plus the 10-byte authentication tag.
     *
     * @param rtcpPacket the plaintext RTCP packet
     * @param ssrc       the sender SSRC whose per-SSRC SRTCP context is used
     * @return the SRTCP packet
     * @throws NullPointerException       if {@code rtcpPacket} is {@code null}
     * @throws WhatsAppCallException.Srtp if the SRTCP transformation fails
     */
    public byte[] protectRtcp(byte[] rtcpPacket, int ssrc) {
        Objects.requireNonNull(rtcpPacket, "rtcpPacket cannot be null");
        return outboundRtcp
                .computeIfAbsent(ssrc, s -> new SrtpRtcpContext(outbound, s, true))
                .protect(rtcpPacket);
    }

    /**
     * Authenticates and decrypts an inbound SRTCP packet for the given sender SSRC.
     *
     * <p>The matching inbound {@link SrtpRtcpContext} is created if needed, and the context
     * validates and decrypts the packet. The returned array is a fresh copy holding the plaintext
     * RTCP packet.
     *
     * @param srtcpPacket the SRTCP packet
     * @param ssrc        the sender SSRC whose per-SSRC SRTCP context is used
     * @return the plaintext RTCP packet
     * @throws NullPointerException       if {@code srtcpPacket} is {@code null}
     * @throws WhatsAppCallException.Srtp if authentication fails or a replay is detected
     */
    public byte[] unprotectRtcp(byte[] srtcpPacket, int ssrc) {
        Objects.requireNonNull(srtcpPacket, "srtcpPacket cannot be null");
        return inboundRtcp
                .computeIfAbsent(ssrc, s -> new SrtpRtcpContext(inbound, s, false))
                .unprotect(srtcpPacket);
    }

    /**
     * Closes the endpoint, zeroing the cached session keys held by each direction.
     *
     * <p>Per-SSRC contexts retain references to their direction's key arrays, so after this call
     * any further protect or unprotect through an existing context operates on zeroed key material
     * and no longer produces meaningful output.
     */
    @Override
    public void close() {
        outbound.zero();
        inbound.zero();
    }

    /**
     * Reads a big-endian 32-bit integer from the byte array at the given offset.
     *
     * @param b      the source bytes
     * @param offset the byte offset at which the integer begins
     * @return the parsed {@code int}
     */
    private static int readInt(byte[] b, int offset) {
        return ((b[offset] & 0xFF) << 24)
                | ((b[offset + 1] & 0xFF) << 16)
                | ((b[offset + 2] & 0xFF) << 8)
                | (b[offset + 3] & 0xFF);
    }
}
