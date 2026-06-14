package com.github.auties00.cobalt.call.rtp.srtp;

import com.github.auties00.cobalt.exception.WhatsAppCallException;

import javax.crypto.KDF;
import javax.crypto.spec.HKDFParameterSpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.Objects;

/**
 * Derives the hop-by-hop SRTCP session secrets that protect the RTCP control traffic between this
 * client and the WhatsApp edge relay.
 *
 * <p>The relayed media path is hop-by-hop encrypted to the relay. Because the relay re-encrypts as
 * it forwards, each leg carries its own master: the uplink this client sends is keyed from
 * {@link Group#UPLINK_SRTCP} and the downlink it receives from {@link Group#DOWNLINK_SRTCP}, with the
 * non-directional {@link Group#SRTCP} keying the shared RTCP control traffic. Each master is expanded
 * by the RFC 3711 key-derivation function into the full session key set, so the same hop-by-hop
 * master protects both the media SRTP and the SRTCP of its leg. All masters derive from the 30-byte
 * hop-by-hop key the relay hands out in the call ack as the base64 {@code <hbh_key>} element. On a
 * linked device the SFrame end-to-end layer is provisioned (key chains are set) but the per-frame
 * SFrame transform is not applied to call media, so the hop-by-hop SRTP key set is what actually
 * protects the relayed media RTP.
 *
 * <p>The 30-byte hop-by-hop key is split into two secrets and each group is derived with a chained
 * pair of HKDF-SHA256 computations: a 32-byte chaining salt is expanded from the first secret with an
 * all-zero salt, and the 30-byte SRTCP master key and salt are then expanded from the second secret
 * using that chaining salt as the HKDF salt:
 *
 * {@snippet :
 *   secretSalt = hbhKey[0  .. 14]   // 14 bytes, input keying material for every chaining salt
 *   secretKey  = hbhKey[14 .. 30]   // 16 bytes, input keying material for every SRTCP keymat
 *   chainSalt  = HKDF-SHA256(ikm = secretSalt, salt = 32 zero bytes, info = "<group> salt", L = 32)
 *   keymat     = HKDF-SHA256(ikm = secretKey,  salt = chainSalt,     info = "<group> key",  L = 30)
 * }
 *
 * <p>The 30-byte {@code keymat} is an {@code AES_CM_128_HMAC_SHA1_80} master key (16 bytes)
 * concatenated with a master salt (14 bytes), which feed {@link SrtpDirection}.
 *
 * @implNote This implementation reproduces the WhatsApp VoIP hop-by-hop SRTCP key schedule reverse
 *           engineered from the native call engine's {@code VoipCrypto::HkdfSha256} routine (shared
 *           with the {@code Bv0D72sgyHY} call wasm module). The label bytes, the all-zero 32-byte
 *           salt of the chaining step, and the chaining of the salt output into the key step were
 *           captured at the live HKDF call site and verified: each group's captured key-step salt
 *           equals the group's chaining-salt output. The {@code "hbh srtp"} ({@link Group#MEDIA})
 *           master protects the relayed media RTP on the client-to-relay hop and is non-directional
 *           (one master both ways); the end-to-end confidentiality of the media payload is layered on
 *           top by the SFrame transform keyed from the call key (see
 *           {@link com.github.auties00.cobalt.call.rtp.sframe.SFrameKeyDerivation}). The {@code "warp
 *           auth"} output keys the relay-ingest MESSAGE-INTEGRITY rather than media protection.
 */
public final class HbhKeyDerivation {
    /**
     * Holds the required length, in bytes, of the hop-by-hop master key.
     */
    public static final int HBH_KEY_LENGTH = 30;

    /**
     * Holds the length, in bytes, of the salt secret (the first slice of the hop-by-hop key), used as
     * the input keying material for every chaining-salt derivation.
     */
    private static final int SALT_SECRET_LENGTH = 14;

    /**
     * Holds the length, in bytes, of the chaining salt expanded from the salt secret and used as the
     * HKDF salt of the key derivation.
     */
    private static final int CHAIN_SALT_LENGTH = 32;

    /**
     * Holds the length, in bytes, of the SRTCP keymat output (a 16-byte master key followed by a
     * 14-byte master salt).
     */
    private static final int KEYMAT_LENGTH = 30;

    /**
     * Holds the all-zero HKDF salt applied to the chaining-salt derivation, matching the native engine
     * which passes a 32-byte zero salt (equivalent to RFC 5869 "no salt").
     */
    private static final byte[] ZERO_SALT = new byte[32];

    /**
     * Enumerates the three hop-by-hop SRTCP key sets, each identified by the ASCII label prefix that
     * forms the HKDF info when suffixed with {@code " salt"} or {@code " key"}.
     */
    public enum Group {
        /**
         * The base non-directional hop-by-hop SRTCP key set, which keys the shared RTCP control
         * traffic.
         */
        SRTCP("hbh srtcp"),

        /**
         * The uplink hop-by-hop SRTCP key set.
         */
        UPLINK_SRTCP("uplink hbh srtcp"),

        /**
         * The downlink hop-by-hop SRTCP key set.
         */
        DOWNLINK_SRTCP("downlink hbh srtcp"),

        /**
         * The non-directional hop-by-hop media key set, which protects the relayed media RTP on the
         * client-to-relay hop. Unlike the directional SRTCP groups a single {@code "hbh srtp"} master
         * keys both the outbound and inbound media legs (the relay forwards on one hop-by-hop context),
         * matching the wasm key-schedule labels {@code "hbh srtp key"} and {@code "hbh srtp salt"}.
         */
        MEDIA("hbh srtp"),

        /**
         * The warp relay-authentication key set from the WhatsApp Web DataChannel relay leg. Cobalt's
         * media plane uses the raw-UDP relay transport (bare SRTP straight to the relay), which carries
         * no warp framing, so this group is retained only to document the full key-schedule family.
         */
        WARP_AUTH("warp auth");

        /**
         * Holds the ASCII label prefix of this group.
         */
        private final String label;

        /**
         * Constructs a group with its label prefix.
         *
         * @param label the ASCII label prefix
         */
        Group(String label) {
            this.label = label;
        }

        /**
         * Returns the HKDF info bytes for this group's chaining-salt derivation.
         *
         * @return the ASCII bytes of {@code "<label> salt"}
         */
        private byte[] saltInfo() {
            return (label + " salt").getBytes(StandardCharsets.US_ASCII);
        }

        /**
         * Returns the HKDF info bytes for this group's keymat derivation.
         *
         * @return the ASCII bytes of {@code "<label> key"}
         */
        private byte[] keyInfo() {
            return (label + " key").getBytes(StandardCharsets.US_ASCII);
        }
    }

    /**
     * Prevents instantiation of this stateless derivation holder.
     */
    private HbhKeyDerivation() {
        throw new AssertionError("HbhKeyDerivation is not instantiable");
    }

    /**
     * Derives the 32-byte chaining salt for a group from the hop-by-hop key.
     *
     * @param hbhKey the 30-byte hop-by-hop master key shared with the relay
     * @param group  the SRTCP key set
     * @return the 32-byte chaining salt
     * @throws NullPointerException       if {@code hbhKey} or {@code group} is {@code null}
     * @throws IllegalArgumentException   if {@code hbhKey} is not exactly {@value #HBH_KEY_LENGTH}
     *                                    bytes long
     * @throws WhatsAppCallException.Srtp if the HKDF computation fails
     */
    public static byte[] deriveChainSalt(byte[] hbhKey, Group group) {
        requireKey(hbhKey);
        Objects.requireNonNull(group, "group cannot be null");
        var secretSalt = Arrays.copyOfRange(hbhKey, 0, SALT_SECRET_LENGTH);
        return expand(secretSalt, ZERO_SALT, group.saltInfo(), CHAIN_SALT_LENGTH, group, "salt");
    }

    /**
     * Derives the 30-byte SRTCP keymat for a group from the hop-by-hop key.
     *
     * <p>The keymat is the {@code AES_CM_128_HMAC_SHA1_80} master key (bytes 0 through 16) followed by
     * the master salt (bytes 16 through 30); use {@link #masterKey(byte[])} and
     * {@link #masterSalt(byte[])} to split it.
     *
     * @param hbhKey the 30-byte hop-by-hop master key shared with the relay
     * @param group  the SRTCP key set
     * @return the 30-byte SRTCP keymat
     * @throws NullPointerException       if {@code hbhKey} or {@code group} is {@code null}
     * @throws IllegalArgumentException   if {@code hbhKey} is not exactly {@value #HBH_KEY_LENGTH}
     *                                    bytes long
     * @throws WhatsAppCallException.Srtp if the HKDF computation fails
     */
    public static byte[] deriveKeymat(byte[] hbhKey, Group group) {
        requireKey(hbhKey);
        Objects.requireNonNull(group, "group cannot be null");
        var secretKey = Arrays.copyOfRange(hbhKey, SALT_SECRET_LENGTH, HBH_KEY_LENGTH);
        var chainSalt = deriveChainSalt(hbhKey, group);
        return expand(secretKey, chainSalt, group.keyInfo(), KEYMAT_LENGTH, group, "key");
    }

    /**
     * Splits a 30-byte SRTCP keymat into its {@code AES_CM_128_HMAC_SHA1_80} master key.
     *
     * @param keymat a 30-byte keymat from {@link #deriveKeymat(byte[], Group)}
     * @return the 16-byte SRTCP master key
     * @throws NullPointerException     if {@code keymat} is {@code null}
     * @throws IllegalArgumentException if {@code keymat} is not exactly 30 bytes long
     */
    public static byte[] masterKey(byte[] keymat) {
        requireKeymat(keymat);
        return Arrays.copyOfRange(keymat, 0, 16);
    }

    /**
     * Splits a 30-byte SRTCP keymat into its {@code AES_CM_128_HMAC_SHA1_80} master salt.
     *
     * @param keymat a 30-byte keymat from {@link #deriveKeymat(byte[], Group)}
     * @return the 14-byte SRTCP master salt
     * @throws NullPointerException     if {@code keymat} is {@code null}
     * @throws IllegalArgumentException if {@code keymat} is not exactly 30 bytes long
     */
    public static byte[] masterSalt(byte[] keymat) {
        requireKeymat(keymat);
        return Arrays.copyOfRange(keymat, 16, 30);
    }

    /**
     * Performs one HKDF-SHA256 extract-and-expand and wraps any failure as a call exception.
     *
     * @param ikm    the input keying material
     * @param salt   the HKDF salt
     * @param info   the HKDF info
     * @param length the output length in bytes
     * @param group  the group being derived, for error reporting
     * @param part   the part being derived ({@code "salt"} or {@code "key"}), for error reporting
     * @return the derived bytes
     * @throws WhatsAppCallException.Srtp if the HKDF computation fails
     */
    private static byte[] expand(byte[] ikm, byte[] salt, byte[] info, int length, Group group, String part) {
        try {
            var hkdf = KDF.getInstance("HKDF-SHA256");
            var params = HKDFParameterSpec.ofExtract()
                    .addIKM(ikm)
                    .addSalt(salt)
                    .thenExpand(info, length);
            return hkdf.deriveData(params);
        } catch (GeneralSecurityException e) {
            throw new WhatsAppCallException.Srtp(
                    "Cannot derive hop-by-hop " + group + " " + part, e);
        }
    }

    /**
     * Validates that a hop-by-hop key is non-null and exactly {@value #HBH_KEY_LENGTH} bytes.
     *
     * @param hbhKey the hop-by-hop key to validate
     * @throws NullPointerException     if {@code hbhKey} is {@code null}
     * @throws IllegalArgumentException if {@code hbhKey} is not exactly {@value #HBH_KEY_LENGTH} bytes
     */
    private static void requireKey(byte[] hbhKey) {
        Objects.requireNonNull(hbhKey, "hbhKey cannot be null");
        if (hbhKey.length != HBH_KEY_LENGTH) {
            throw new IllegalArgumentException(
                    "hbhKey must be " + HBH_KEY_LENGTH + " bytes, got " + hbhKey.length);
        }
    }

    /**
     * Validates that a keymat is non-null and exactly {@value #KEYMAT_LENGTH} bytes.
     *
     * @param keymat the keymat to validate
     * @throws NullPointerException     if {@code keymat} is {@code null}
     * @throws IllegalArgumentException if {@code keymat} is not exactly {@value #KEYMAT_LENGTH} bytes
     */
    private static void requireKeymat(byte[] keymat) {
        Objects.requireNonNull(keymat, "keymat cannot be null");
        if (keymat.length != KEYMAT_LENGTH) {
            throw new IllegalArgumentException(
                    "keymat must be " + KEYMAT_LENGTH + " bytes, got " + keymat.length);
        }
    }
}
