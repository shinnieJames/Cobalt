package com.github.auties00.cobalt.call.rtp.srtp;

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
     * Holds the minimum size of a valid RTP packet, which is the 12-byte fixed header.
     */
    private static final int MIN_RTP_LENGTH = 12;

    /**
     * Holds the default truncated HMAC-SHA1 authentication tag length, used when no relay-supplied
     * {@code warp_mi_tag_len} overrides it.
     *
     * <p>This is the full {@code AES_CM_128_HMAC_SHA1_80} tag length that a DTLS-SRTP handshake
     * negotiates, so it applies to the peer DTLS-SRTP keying path; the relayed media leg uses the
     * shorter {@link #RELAY_MEDIA_AUTH_TAG_LENGTH} instead.
     */
    public static final int DEFAULT_AUTH_TAG_LENGTH = 10;

    /**
     * Holds the truncated HMAC-SHA1 authentication tag length the WhatsApp edge relay applies to the
     * relayed media SRTP leg when the {@code <relay>} block carries no explicit {@code warp_mi_tag_len}.
     *
     * @implNote This implementation uses {@code 4}. The {@code warp_mi_tag_len} attribute is absent from
     * every captured relay block, and a live Cobalt-to-WhatsApp call's inbound media authenticated
     * byte-for-byte only with a 4-byte tag (the Family-B participant-key SRTP master with a 10-byte tag
     * failed every packet); the relay truncates the {@code _80} profile's 10-byte tag down to 4 on this
     * leg. The matching offline sweep is {@code .temp/call-re/solve_inbound.py}.
     */
    public static final int RELAY_MEDIA_AUTH_TAG_LENGTH = 4;

    /**
     * Holds the truncated HMAC-SHA1 authentication tag length this endpoint protects with.
     */
    private final int authTagLength;

    /**
     * Holds the outbound (sender-side) derived session keys; replaced wholesale by
     * {@link #rotateMasterKey(byte[])} on a mid-call rekey.
     */
    private volatile SrtpDirection outbound;

    /**
     * Holds the inbound (receiver-side) derived session keys; replaced wholesale by
     * {@link #rotateMasterKey(byte[])} on a mid-call rekey.
     */
    private volatile SrtpDirection inbound;

    /**
     * Guards {@link #rotateMasterKey(byte[])} so a concurrent rotation cannot interleave with
     * itself; in-flight {@link #protectRtp(byte[])} / {@link #unprotectRtp(byte[])} calls on
     * pre-rotation per-SSRC contexts complete unimpeded.
     */
    private final Object rotationLock = new Object();

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
    SrtpEndpoint(byte[] outboundKey, byte[] outboundSalt,
                 byte[] inboundKey, byte[] inboundSalt) {
        this(outboundKey, outboundSalt, inboundKey, inboundSalt, DEFAULT_AUTH_TAG_LENGTH);
    }

    /**
     * Constructs an endpoint from the already-split outbound and inbound master keys and salts and an
     * explicit truncated authentication tag length.
     *
     * @param outboundKey   the outbound 16-byte master key
     * @param outboundSalt  the outbound 14-byte master salt
     * @param inboundKey    the inbound 16-byte master key
     * @param inboundSalt   the inbound 14-byte master salt
     * @param authTagLength the truncated HMAC-SHA1 tag length in bytes
     */
    SrtpEndpoint(byte[] outboundKey, byte[] outboundSalt,
                 byte[] inboundKey, byte[] inboundSalt, int authTagLength) {
        this.outbound = new SrtpDirection(outboundKey, outboundSalt);
        this.inbound = new SrtpDirection(inboundKey, inboundSalt);
        this.authTagLength = authTagLength;
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
     * Builds an endpoint that protects the relayed media RTP on the hop-by-hop leg between this
     * client and the WhatsApp edge relay, keyed from the 30-byte hop-by-hop key the relay hands out
     * in the {@code <hbh_key>} element.
     *
     * <p>The relay re-encrypts media as it forwards, so the two legs carry different keys: the uplink
     * this client sends to the relay is protected with the
     * {@link HbhKeyDerivation.Group#UPLINK_SRTCP} master and the downlink it receives from the relay
     * with the {@link HbhKeyDerivation.Group#DOWNLINK_SRTCP} master. Each 30-byte keymat is an
     * {@code AES_CM_128_HMAC_SHA1_80} master key and salt that {@link SrtpDirection} expands into the
     * full RFC 3711 session key set, so the same master protects both the media SRTP and the SRTCP of
     * its leg. Unlike {@link #fromDtlsKeyingMaterial(byte[], SrtpRole)}, no DTLS role is needed
     * because the leg assignment is fixed by the uplink and downlink labels rather than by the
     * handshake role.
     *
     * @param hopByHopKey the 30-byte hop-by-hop key decoded from the relay block {@code <hbh_key>}
     * @return a fresh endpoint keyed on the uplink and downlink hop-by-hop media masters
     * @throws NullPointerException       if {@code hopByHopKey} is {@code null}
     * @throws IllegalArgumentException   if {@code hopByHopKey} is not exactly
     *                                    {@link HbhKeyDerivation#HBH_KEY_LENGTH} bytes long
     * @throws com.github.auties00.cobalt.exception.WhatsAppCallException.Srtp if the key schedule fails
     * @implNote This implementation maps the uplink leg to the outbound direction and the downlink
     *           leg to the inbound direction. The hop-by-hop key feeds no separate {@code hbh srtp}
     *           derivation: on a relayed 1:1 call the native call engine derives only the
     *           {@code hbh srtcp}, {@code uplink hbh srtcp}, and {@code downlink hbh srtcp} masters,
     *           protects media through {@code srtp_protect}, and never applies the SFrame transform.
     */
    public static SrtpEndpoint fromHopByHopKey(byte[] hopByHopKey) {
        Objects.requireNonNull(hopByHopKey, "hopByHopKey cannot be null");
        return fromHopByHopKey(hopByHopKey, DEFAULT_AUTH_TAG_LENGTH);
    }

    /**
     * Builds a hop-by-hop endpoint with an explicit truncated authentication tag length.
     *
     * @param hopByHopKey   the 30-byte hop-by-hop key decoded from the relay block {@code <hbh_key>}
     * @param authTagLength the truncated HMAC-SHA1 tag length from the relay {@code warp_mi_tag_len}
     * @return a fresh endpoint keyed on the uplink and downlink hop-by-hop media masters
     * @throws NullPointerException     if {@code hopByHopKey} is {@code null}
     * @throws IllegalArgumentException if {@code hopByHopKey} is not exactly
     *                                  {@link HbhKeyDerivation#HBH_KEY_LENGTH} bytes long
     * @throws com.github.auties00.cobalt.exception.WhatsAppCallException.Srtp if the key schedule fails
     */
    public static SrtpEndpoint fromHopByHopKey(byte[] hopByHopKey, int authTagLength) {
        Objects.requireNonNull(hopByHopKey, "hopByHopKey cannot be null");
        var uplinkKeymat = HbhKeyDerivation.deriveKeymat(hopByHopKey, HbhKeyDerivation.Group.UPLINK_SRTCP);
        var downlinkKeymat = HbhKeyDerivation.deriveKeymat(hopByHopKey, HbhKeyDerivation.Group.DOWNLINK_SRTCP);
        return new SrtpEndpoint(
                HbhKeyDerivation.masterKey(uplinkKeymat), HbhKeyDerivation.masterSalt(uplinkKeymat),
                HbhKeyDerivation.masterKey(downlinkKeymat), HbhKeyDerivation.masterSalt(downlinkKeymat),
                authTagLength);
    }

    /**
     * Builds an endpoint that protects the relayed media RTP on the hop-by-hop leg between this client
     * and the WhatsApp edge relay, keyed from the single non-directional {@code "hbh srtp"} master
     * derived from the relay {@code <hbh_key>}.
     *
     * <p>This is the media SRTP that actually travels on the wire over a relayed call: the engine
     * protects each media RTP packet with the {@link HbhKeyDerivation.Group#MEDIA hbh srtp} master and
     * the relay re-keys as it forwards, so a single master keys both the outbound and inbound legs (the
     * relay holds the same hop context for both). End-to-end confidentiality of the Opus payload is
     * layered on top of this hop by the SFrame transform keyed from the call key, not by the SRTP key.
     * This replaces the mistaken use of a call-key-and-participant-JID SRTP master, which has no
     * counterpart in the engine key schedule and which the relay therefore cannot authenticate.
     *
     * @param hopByHopKey   the 30-byte hop-by-hop key decoded from the relay block {@code <hbh_key>}
     * @param authTagLength the truncated HMAC-SHA1 tag length from the relay {@code warp_mi_tag_len}
     * @return a fresh endpoint keyed on the {@code "hbh srtp"} media master for both directions
     * @throws NullPointerException     if {@code hopByHopKey} is {@code null}
     * @throws IllegalArgumentException if {@code hopByHopKey} is not exactly
     *                                  {@link HbhKeyDerivation#HBH_KEY_LENGTH} bytes long
     * @throws com.github.auties00.cobalt.exception.WhatsAppCallException.Srtp if the key schedule fails
     * @implNote This implementation derives one {@code "hbh srtp"} keymat from the relay key and uses it
     * for both the outbound and inbound directions, matching the wasm {@code derive_hbh_srtp_key} which
     * produces a single non-directional media master (the {@code "hbh srtp key"}/{@code "hbh srtp salt"}
     * labels have no uplink/downlink variants, unlike the SRTCP labels).
     */
    public static SrtpEndpoint fromHopByHopMedia(byte[] hopByHopKey, int authTagLength) {
        Objects.requireNonNull(hopByHopKey, "hopByHopKey cannot be null");
        var keymat = HbhKeyDerivation.deriveKeymat(hopByHopKey, HbhKeyDerivation.Group.MEDIA);
        return new SrtpEndpoint(
                HbhKeyDerivation.masterKey(keymat), HbhKeyDerivation.masterSalt(keymat),
                HbhKeyDerivation.masterKey(keymat), HbhKeyDerivation.masterSalt(keymat),
                authTagLength);
    }

    /**
     * Builds an endpoint that protects the hop-by-hop SRTCP control traffic between this client and the
     * edge relay, keyed from the single non-directional {@code "hbh srtcp"} master both directions.
     *
     * <p>Unlike {@link #fromHopByHopKey(byte[], int)}, which keys the two media legs from the
     * directional {@link HbhKeyDerivation.Group#UPLINK_SRTCP uplink} and
     * {@link HbhKeyDerivation.Group#DOWNLINK_SRTCP downlink} masters, the RTCP sender reports a plain
     * (non-warp) relay exchanges are keyed from the base {@link HbhKeyDerivation.Group#SRTCP} master in
     * both directions. Using the directional masters here yields RTCP the peer cannot authenticate, so
     * the peer never confirms the reverse RTCP path and holds the call in the connecting state.
     *
     * @param hopByHopKey   the 30-byte hop-by-hop key decoded from the relay block {@code <hbh_key>}
     * @param authTagLength the truncated HMAC-SHA1 tag length from the relay {@code warp_mi_tag_len}
     * @return a fresh endpoint keyed on the base {@code "hbh srtcp"} master for both directions
     * @throws NullPointerException     if {@code hopByHopKey} is {@code null}
     * @throws IllegalArgumentException if {@code hopByHopKey} is not exactly
     *                                  {@link HbhKeyDerivation#HBH_KEY_LENGTH} bytes long
     * @throws com.github.auties00.cobalt.exception.WhatsAppCallException.Srtp if the key schedule fails
     * @implNote This implementation derives one {@code "hbh srtcp"} keymat (the base, byte-exact verified
     * against a live {@code set_key}) and uses it for both the outbound and inbound directions, matching
     * the plain relay where a single master covers the SRTCP of both legs.
     */
    public static SrtpEndpoint fromHopByHopSrtcp(byte[] hopByHopKey, int authTagLength) {
        Objects.requireNonNull(hopByHopKey, "hopByHopKey cannot be null");
        var keymat = HbhKeyDerivation.deriveKeymat(hopByHopKey, HbhKeyDerivation.Group.SRTCP);
        return new SrtpEndpoint(
                HbhKeyDerivation.masterKey(keymat), HbhKeyDerivation.masterSalt(keymat),
                HbhKeyDerivation.masterKey(keymat), HbhKeyDerivation.masterSalt(keymat),
                authTagLength);
    }

    /**
     * Builds an endpoint keyed on the end-to-end participant masters that protect the media payload,
     * derived from the call key via {@link CallKeyDerivation}.
     *
     * <p>The local participant JID keys the outbound direction and the peer participant JID keys the
     * inbound direction. This is the {@code AES_CM_128_HMAC_SHA1} family that encrypts the Opus and
     * video payload end to end, layered under the hop-by-hop relay protection.
     *
     * @param callKey       the 32-byte end-to-end call key fanned out in the offer {@code <enc>}
     * @param localJid      the local participant {@code <lid>:<device>@lid} JID
     * @param peerJid       the peer participant {@code <lid>:<device>@lid} JID
     * @param authTagLength the truncated HMAC-SHA1 tag length from the relay {@code warp_mi_tag_len}
     * @return a fresh endpoint keyed on the participant media masters
     * @throws NullPointerException     if any argument is {@code null}
     * @throws IllegalArgumentException if {@code callKey} is not exactly
     *                                  {@link CallKeyDerivation#CALL_KEY_LENGTH} bytes long
     * @throws com.github.auties00.cobalt.exception.WhatsAppCallException.Srtp if the key schedule fails
     */
    public static SrtpEndpoint fromParticipantKeys(byte[] callKey, String localJid, String peerJid,
                                                   int authTagLength) {
        Objects.requireNonNull(localJid, "localJid cannot be null");
        Objects.requireNonNull(peerJid, "peerJid cannot be null");
        var localMaster = CallKeyDerivation.deriveMaster(callKey, localJid);
        var peerMaster = CallKeyDerivation.deriveMaster(callKey, peerJid);
        return new SrtpEndpoint(
                CallKeyDerivation.masterKey(localMaster), CallKeyDerivation.masterSalt(localMaster),
                CallKeyDerivation.masterKey(peerMaster), CallKeyDerivation.masterSalt(peerMaster),
                authTagLength);
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
                .computeIfAbsent(ssrc, s -> new SrtpRtpContext(outbound, s, true, authTagLength))
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
        if (srtpPacket.length < MIN_RTP_LENGTH + authTagLength) {
            throw new IllegalArgumentException(
                    "srtpPacket too short for header + auth tag: " + srtpPacket.length);
        }
        var ssrc = readInt(srtpPacket, 8);
        return inboundRtp
                .computeIfAbsent(ssrc, s -> new SrtpRtpContext(inbound, s, false, authTagLength))
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
                .computeIfAbsent(ssrc, s -> new SrtpRtcpContext(outbound, s, true, authTagLength))
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
                .computeIfAbsent(ssrc, s -> new SrtpRtcpContext(inbound, s, false, authTagLength))
                .unprotect(srtcpPacket);
    }

    /**
     * Replaces the master key for both directions in place, deriving fresh session keys against the
     * existing master salts.
     *
     * <p>Used by the call layer's {@code <enc_rekey>} runtime to rotate SRTP keys on participant
     * join and leave in a group call. The supplied 16-byte master key replaces both directions'
     * master keys (the rekey published by WhatsApp's wasm is a single symmetric per-domain master
     * key); the master salts originally exported from the DTLS handshake stay. Per-SSRC RTP and
     * RTCP context caches are cleared so the next packet on each SSRC creates a fresh context
     * keyed on the new session material.
     *
     * <p>Packet-protection calls already in flight on the old per-SSRC contexts continue with the
     * pre-rotation keys; the rotation point is logical and atomic at the cache-clear, not at every
     * outstanding {@link #protectRtp(byte[])} invocation.
     *
     * @param newMasterKey the new 16-byte SRTP master key published in the
     *                     {@link com.github.auties00.cobalt.model.call.datachannel.E2eRekeyPayload E2eRekeyPayload}
     * @throws NullPointerException     if {@code newMasterKey} is {@code null}
     * @throws IllegalArgumentException if {@code newMasterKey} is not exactly
     *                                  {@value #MASTER_KEY_LENGTH} bytes long
     */
    public void rotateMasterKey(byte[] newMasterKey) {
        Objects.requireNonNull(newMasterKey, "newMasterKey cannot be null");
        if (newMasterKey.length != MASTER_KEY_LENGTH) {
            throw new IllegalArgumentException(
                    "newMasterKey must be " + MASTER_KEY_LENGTH
                            + " bytes, got " + newMasterKey.length);
        }
        synchronized (rotationLock) {
            this.outbound = new SrtpDirection(newMasterKey, this.outbound.masterSalt);
            this.inbound = new SrtpDirection(newMasterKey, this.inbound.masterSalt);
            outboundRtp.clear();
            inboundRtp.clear();
            outboundRtcp.clear();
            inboundRtcp.clear();
        }
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
