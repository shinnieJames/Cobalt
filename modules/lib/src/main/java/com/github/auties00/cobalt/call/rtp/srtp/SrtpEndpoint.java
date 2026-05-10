package com.github.auties00.cobalt.call.rtp.srtp;

import com.github.auties00.cobalt.exception.WhatsAppCallException;

import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SRTP/SRTCP endpoint that protects outbound RTP/RTCP packets and
 * unprotects inbound ones, keyed on master key+salt material exported
 * from a completed DTLS-SRTP handshake (RFC 5764).
 *
 * <p>WhatsApp's wasm engine uses the WebRTC default SRTP profile —
 * {@code SRTP_AES128_CM_HMAC_SHA1_80} — so this endpoint hard-codes
 * that profile. The 60-byte exported keying material is split per
 * RFC 5764 §4.2:
 *
 * <pre>{@code
 *   bytes[0  .. 16]  = client_write_SRTP_master_key   (16)
 *   bytes[16 .. 32]  = server_write_SRTP_master_key   (16)
 *   bytes[32 .. 46]  = client_write_SRTP_master_salt  (14)
 *   bytes[46 .. 60]  = server_write_SRTP_master_salt  (14)
 * }</pre>
 *
 * <p>Each direction (outbound, inbound) gets its own
 * {@link SrtpDirection} carrying the derived session keys; per-SSRC
 * {@link SrtpRtpContext} and {@link SrtpRtcpContext} instances are
 * created lazily on first use of an SSRC and cached in
 * {@link ConcurrentHashMap} maps. Thread-safe.
 *
 * <p>Pure-Java implementation built on the JDK's AES-CTR and
 * HMAC-SHA1 primitives — no third-party SRTP library or BouncyCastle
 * required for SRTP packet protection.
 */
public final class SrtpEndpoint implements AutoCloseable {
    /**
     * Length, in bytes, of an SRTP master key (AES-128).
     */
    private static final int MASTER_KEY_LENGTH = 16;

    /**
     * Length, in bytes, of an SRTP master salt.
     */
    private static final int MASTER_SALT_LENGTH = 14;

    /**
     * Total length of the DTLS-SRTP exported keying material:
     * {@code 2 * (MASTER_KEY_LENGTH + MASTER_SALT_LENGTH)}.
     */
    public static final int KEYING_MATERIAL_LENGTH =
            2 * (MASTER_KEY_LENGTH + MASTER_SALT_LENGTH);

    /**
     * Minimum size of a valid SRTP packet: 12-byte fixed RTP header
     * plus 10-byte auth tag.
     */
    private static final int MIN_SRTP_LENGTH = 22;

    /**
     * Minimum size of a valid RTP packet: the 12-byte fixed header.
     */
    private static final int MIN_RTP_LENGTH = 12;

    /**
     * Outbound (sender-side) derived session keys.
     */
    private final SrtpDirection outbound;

    /**
     * Inbound (receiver-side) derived session keys.
     */
    private final SrtpDirection inbound;

    /**
     * Per-SSRC outbound RTP contexts, derived lazily.
     */
    private final ConcurrentHashMap<Integer, SrtpRtpContext> outboundRtp = new ConcurrentHashMap<>();

    /**
     * Per-SSRC inbound RTP contexts, derived lazily.
     */
    private final ConcurrentHashMap<Integer, SrtpRtpContext> inboundRtp = new ConcurrentHashMap<>();

    /**
     * Per-SSRC outbound RTCP contexts, derived lazily.
     */
    private final ConcurrentHashMap<Integer, SrtpRtcpContext> outboundRtcp = new ConcurrentHashMap<>();

    /**
     * Per-SSRC inbound RTCP contexts, derived lazily.
     */
    private final ConcurrentHashMap<Integer, SrtpRtcpContext> inboundRtcp = new ConcurrentHashMap<>();

    /**
     * Constructs a new endpoint from already-split keys.
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
     * Builds an endpoint from the 60-byte DTLS-SRTP exported keying
     * material, splitting it according to {@code role} per RFC 5764
     * §4.2.
     *
     * @param keyingMaterial the 60-byte block exported from the DTLS
     *                       session via the
     *                       {@code "EXTRACTOR-dtls_srtp"} label
     * @param role           our DTLS role (client or server) —
     *                       determines which half of
     *                       {@code keyingMaterial} is outbound vs
     *                       inbound
     * @return a fresh endpoint
     * @throws NullPointerException     if any argument is {@code null}
     * @throws IllegalArgumentException if {@code keyingMaterial} is
     *                                  not exactly
     *                                  {@link #KEYING_MATERIAL_LENGTH}
     *                                  bytes long
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
     * Encrypts and authenticates an outbound RTP packet, returning a
     * fresh byte array containing the SRTP packet (longer by the
     * 10-byte auth tag).
     *
     * @param rtpPacket the plaintext RTP packet
     * @return the SRTP packet ({@code rtpPacket.length + 10} bytes)
     * @throws NullPointerException     if {@code rtpPacket} is
     *                                  {@code null}
     * @throws IllegalArgumentException if the packet is shorter than
     *                                  the 12-byte RTP header
     * @throws WhatsAppCallException.Srtp            if SRTP transformation fails
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
     * Authenticates and decrypts an inbound SRTP packet, returning a
     * fresh byte array containing the plaintext RTP packet (shorter
     * by the 10-byte auth tag).
     *
     * @param srtpPacket the SRTP packet
     * @return the plaintext RTP packet
     * @throws NullPointerException     if {@code srtpPacket} is
     *                                  {@code null}
     * @throws IllegalArgumentException if the packet is shorter than
     *                                  the 12-byte RTP header + 10-byte
     *                                  auth tag
     * @throws WhatsAppCallException.Srtp            if authentication fails or a
     *                                  replay is detected
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
     * Encrypts and authenticates an outbound RTCP packet, returning a
     * fresh byte array containing the SRTCP packet (longer by the
     * 4-byte SRTCP index + 10-byte auth tag).
     *
     * @param rtcpPacket the plaintext RTCP packet
     * @param ssrc       the sender SSRC for which to derive the
     *                   per-SSRC SRTCP context
     * @return the SRTCP packet
     * @throws NullPointerException if {@code rtcpPacket} is {@code null}
     * @throws WhatsAppCallException.Srtp        if SRTCP transformation fails
     */
    public byte[] protectRtcp(byte[] rtcpPacket, int ssrc) {
        Objects.requireNonNull(rtcpPacket, "rtcpPacket cannot be null");
        return outboundRtcp
                .computeIfAbsent(ssrc, s -> new SrtpRtcpContext(outbound, s, true))
                .protect(rtcpPacket);
    }

    /**
     * Authenticates and decrypts an inbound SRTCP packet, returning a
     * fresh byte array containing the plaintext RTCP packet.
     *
     * @param srtcpPacket the SRTCP packet
     * @param ssrc        the sender SSRC for which to derive the
     *                    per-SSRC SRTCP context
     * @return the plaintext RTCP packet
     * @throws NullPointerException if {@code srtcpPacket} is {@code null}
     * @throws WhatsAppCallException.Srtp        if authentication fails or a
     *                              replay is detected
     */
    public byte[] unprotectRtcp(byte[] srtcpPacket, int ssrc) {
        Objects.requireNonNull(srtcpPacket, "srtcpPacket cannot be null");
        return inboundRtcp
                .computeIfAbsent(ssrc, s -> new SrtpRtcpContext(inbound, s, false))
                .unprotect(srtcpPacket);
    }

    /**
     * Closes the endpoint, zeroing the cached session keys held by
     * each direction. Per-SSRC contexts retain references to their
     * direction's keys, so subsequent calls will fail to produce
     * meaningful output.
     */
    @Override
    public void close() {
        outbound.zero();
        inbound.zero();
    }

    /**
     * Reads a big-endian 32-bit integer from the byte array at the
     * given offset.
     *
     * @param b      the source bytes
     * @param offset the byte offset to read from
     * @return the parsed {@code int}
     */
    private static int readInt(byte[] b, int offset) {
        return ((b[offset] & 0xFF) << 24)
                | ((b[offset + 1] & 0xFF) << 16)
                | ((b[offset + 2] & 0xFF) << 8)
                | (b[offset + 3] & 0xFF);
    }
}
