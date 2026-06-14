package com.github.auties00.cobalt.call.rtp.srtp;

import com.github.auties00.cobalt.exception.WhatsAppCallException;

import javax.crypto.KDF;
import javax.crypto.spec.HKDFParameterSpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.Objects;

/**
 * Derives the end-to-end participant SRTP master that protects the actual audio and video payload of a
 * call.
 *
 * <p>WhatsApp call media runs two independent SRTP families. The hop-by-hop family (see
 * {@link HbhKeyDerivation}) protects only the client-to-relay leg and the RTCP control traffic; it is
 * keyed from the relay {@code <hbh_key>}. The end-to-end family derived here is the one that actually
 * encrypts the Opus and H.264 or VP8 payload, and it is keyed not from the relay key but from the
 * 32-byte call key the caller generates and fans out per device in the offer's
 * {@code <destination><enc>} Signal envelope. Because the relay never sees the call key, it can route
 * but not read the media.
 *
 * <p>For each participant, identified by its {@code <lid>:<device>@lid} JID string, the 30-byte SRTP
 * master is the first 30 bytes of a 46-byte HKDF-SHA256 expansion over the call key with the
 * participant JID as the info:
 *
 * {@snippet :
 *   okm     = HKDF-SHA256(ikm = callKey[32], salt = "" /* RFC 5869 no-salt *\/, info = participantJid, L = 30)
 *   master  = okm[0 .. 30]    // 16-byte AES-128 key followed by a 14-byte salt (media SRTP)
 * }
 *
 * <p>The {@link #deriveMaster(byte[], String)} method returns this media master.
 *
 * <p>The 30-byte master is a {@code AES_CM_128_HMAC_SHA1} master key (16 bytes) concatenated with a
 * master salt (14 bytes), which feed the RFC 3711 session key-derivation in {@link SrtpDirection}. The
 * local participant JID keys the outbound stream and the peer participant JID keys the inbound stream.
 * Group calls reuse this identical derivation, fanning the call key to every member.
 *
 * @implNote This implementation reproduces the WhatsApp VoIP per-participant media-key schedule
 *           (native {@code generate_srtp_and_p2p_keys_for_participant}). It is verified byte-exact
 *           against live captures: for call key
 *           {@code bc4e7efa3efe251b9d5aeb3c36843d776c27777d67e5555f7956fd4f8ab003d8},
 *           {@code deriveMaster(callKey, "39110693621863:29@lid")} equals
 *           {@code c1087730f4b5c07801a37795c5335885f751ba1c1cbe9c3669965b50ba1d} and
 *           {@code deriveMaster(callKey, "258252122116273:71@lid")} equals {@code 357a151c...}, each of
 *           which appeared verbatim as a live cipher {@code set_key} input. The salt is the RFC 5869
 *           no-salt default (a 32-byte zero salt, which HMAC-SHA256 zero-pads identically to an empty
 *           key), and the info is the participant JID bytes with no trailing NUL.
 */
public final class CallKeyDerivation {
    /**
     * Holds the required length, in bytes, of the end-to-end call key.
     */
    public static final int CALL_KEY_LENGTH = 32;

    /**
     * Holds the length, in bytes, of the SRTP master (a 16-byte key followed by a 14-byte salt).
     */
    public static final int MASTER_LENGTH = 30;

    /**
     * Holds the RFC 5869 no-salt default applied to the extract step, matching the native engine.
     */
    private static final byte[] ZERO_SALT = new byte[32];

    /**
     * Prevents instantiation of this stateless derivation holder.
     */
    private CallKeyDerivation() {
        throw new AssertionError("CallKeyDerivation is not instantiable");
    }

    /**
     * Derives the 30-byte end-to-end SRTP master for a participant from the call key.
     *
     * <p>The master is the {@code AES_CM_128_HMAC_SHA1} master key (bytes 0 through 16) followed by the
     * master salt (bytes 16 through 30); use {@link #masterKey(byte[])} and {@link #masterSalt(byte[])}
     * to split it.
     *
     * @param callKey        the 32-byte call key fanned out in the offer's {@code <enc>} envelope
     * @param participantJid the participant's {@code <lid>:<device>@lid} JID string
     * @return the 30-byte SRTP master
     * @throws NullPointerException       if {@code callKey} or {@code participantJid} is {@code null}
     * @throws IllegalArgumentException   if {@code callKey} is not exactly {@value #CALL_KEY_LENGTH}
     *                                    bytes long
     * @throws WhatsAppCallException.Srtp if the HKDF computation fails
     */
    public static byte[] deriveMaster(byte[] callKey, String participantJid) {
        Objects.requireNonNull(callKey, "callKey cannot be null");
        Objects.requireNonNull(participantJid, "participantJid cannot be null");
        if (callKey.length != CALL_KEY_LENGTH) {
            throw new IllegalArgumentException(
                    "callKey must be " + CALL_KEY_LENGTH + " bytes, got " + callKey.length);
        }
        var info = participantJid.getBytes(StandardCharsets.US_ASCII);
        try {
            var hkdf = KDF.getInstance("HKDF-SHA256");
            var params = HKDFParameterSpec.ofExtract()
                    .addIKM(callKey)
                    .addSalt(ZERO_SALT)
                    .thenExpand(info, MASTER_LENGTH);
            return hkdf.deriveData(params);
        } catch (GeneralSecurityException e) {
            throw new WhatsAppCallException.Srtp(
                    "Cannot derive participant keys for " + participantJid, e);
        }
    }

    /**
     * Splits a 30-byte master into its {@code AES_CM_128_HMAC_SHA1} master key.
     *
     * @param master a 30-byte master from {@link #deriveMaster(byte[], String)}
     * @return the 16-byte master key
     * @throws NullPointerException     if {@code master} is {@code null}
     * @throws IllegalArgumentException if {@code master} is not exactly {@value #MASTER_LENGTH} bytes
     */
    public static byte[] masterKey(byte[] master) {
        requireMaster(master);
        return Arrays.copyOfRange(master, 0, 16);
    }

    /**
     * Splits a 30-byte master into its {@code AES_CM_128_HMAC_SHA1} master salt.
     *
     * @param master a 30-byte master from {@link #deriveMaster(byte[], String)}
     * @return the 14-byte master salt
     * @throws NullPointerException     if {@code master} is {@code null}
     * @throws IllegalArgumentException if {@code master} is not exactly {@value #MASTER_LENGTH} bytes
     */
    public static byte[] masterSalt(byte[] master) {
        requireMaster(master);
        return Arrays.copyOfRange(master, 16, 30);
    }

    /**
     * Validates that a master is non-null and exactly {@value #MASTER_LENGTH} bytes.
     *
     * @param master the master to validate
     * @throws NullPointerException     if {@code master} is {@code null}
     * @throws IllegalArgumentException if {@code master} is not exactly {@value #MASTER_LENGTH} bytes
     */
    private static void requireMaster(byte[] master) {
        Objects.requireNonNull(master, "master cannot be null");
        if (master.length != MASTER_LENGTH) {
            throw new IllegalArgumentException(
                    "master must be " + MASTER_LENGTH + " bytes, got " + master.length);
        }
    }
}
