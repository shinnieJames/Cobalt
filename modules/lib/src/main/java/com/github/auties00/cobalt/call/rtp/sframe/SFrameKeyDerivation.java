package com.github.auties00.cobalt.call.rtp.sframe;

import com.github.auties00.cobalt.exception.WhatsAppCallException;

import javax.crypto.KDF;
import javax.crypto.spec.HKDFParameterSpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.Objects;

/**
 * Derives the per-participant SFrame base key that seeds the end-to-end media encryption shared
 * between the two clients of a call.
 *
 * <p>SFrame is the inner, end-to-end layer of the WhatsApp call media path: the audio and video
 * payloads are SFrame-encrypted by the sender, the relay forwards the SFrame'd frames without being
 * able to read them, and the receiver decrypts them. Each participant in a call owns one SFrame key
 * provider whose base (chain) key is derived here; the provider then expands per-frame keys, salts,
 * and nonces from that base key and ratchets it forward as frames are sent.
 *
 * <p>The base key for a participant is a single HKDF-SHA256 output derived from the 32-byte call key
 * (the end-to-end shared secret transported in the encrypted call offer). The call key is split into
 * two 16-byte halves: the first half is the HKDF salt and the second half is the HKDF input keying
 * material. The info is an ASCII string formed by concatenating a fixed label with the participant's
 * JID:
 *
 * {@snippet :
 *   salt = callKey[0 .. 16]                    // first 16 bytes
 *   ikm  = callKey[16 .. 32]                   // second 16 bytes
 *   info = "e2e sframe key" + participantJid   // ASCII, no NUL, no separator
 *   baseKey = HKDF-SHA256(ikm, salt, info, L = 32)
 * }
 *
 * <p>Because the only input that varies between participants is the JID embedded in {@code info},
 * the two clients of a one-to-one call derive distinct base keys from the same call key: each derives
 * its own send key from its own JID and the peer's receive key from the peer's JID.
 *
 * @implNote This implementation reproduces the WhatsApp VoIP SFrame base-key schedule reverse
 *           engineered from the native call engine's {@code VoipCrypto::HkdfSha256} routine (the
 *           {@code wa::sframe} / {@code facebook::sframe} key provider, shared with the
 *           {@code Bv0D72sgyHY} call wasm module): the {@code info} bytes, the 16-byte salt and
 *           16-byte input keying material (the two halves of the call key, salt half first), and the
 *           32-byte output length were captured at the live HKDF call site and verified against the
 *           live base key. The cipher the base key feeds is {@code AES_128_CTR_HMAC_SHA256} with a
 *           4-byte tag (IETF SFrame cipher suite 3). The per-frame key, salt, and nonce expansion and
 *           the ratchet step are performed by the SFrame key provider on top of this base key and are
 *           not done here.
 */
public final class SFrameKeyDerivation {
    /**
     * Holds the required length, in bytes, of the call key used as the HKDF input keying material.
     */
    public static final int CALL_KEY_LENGTH = 32;

    /**
     * Holds the length, in bytes, of the derived SFrame base key.
     */
    public static final int BASE_KEY_LENGTH = 32;

    /**
     * Holds the length, in bytes, of each half of the call key; the first half is the HKDF salt and
     * the second half is the HKDF input keying material.
     */
    private static final int HALF = 16;

    /**
     * Holds the fixed ASCII label that prefixes the participant JID in the HKDF info string, with no
     * trailing {@code NUL} and no separator before the JID.
     */
    private static final byte[] INFO_LABEL = "e2e sframe key".getBytes(StandardCharsets.US_ASCII);

    /**
     * Prevents instantiation of this stateless derivation holder.
     */
    private SFrameKeyDerivation() {
        throw new AssertionError("SFrameKeyDerivation is not instantiable");
    }

    /**
     * Derives the SFrame base key for a single call participant.
     *
     * <p>The {@code participantJid} is the participant's wire JID exactly as it appears on the
     * signaling layer, for example {@code "258252122116273:63@lid"}; its ASCII bytes are appended to
     * the fixed {@link #INFO_LABEL} to form the HKDF info.
     *
     * @param callKey        the 32-byte end-to-end call key shared by the two clients
     * @param participantJid the participant whose base key is derived
     * @return the 32-byte SFrame base key for {@code participantJid}
     * @throws NullPointerException       if {@code callKey} or {@code participantJid} is {@code null}
     * @throws IllegalArgumentException   if {@code callKey} is not exactly {@value #CALL_KEY_LENGTH}
     *                                    bytes long
     * @throws WhatsAppCallException.Srtp if the HKDF computation fails
     */
    public static byte[] deriveParticipantBaseKey(byte[] callKey, String participantJid) {
        Objects.requireNonNull(callKey, "callKey cannot be null");
        Objects.requireNonNull(participantJid, "participantJid cannot be null");
        if (callKey.length != CALL_KEY_LENGTH) {
            throw new IllegalArgumentException(
                    "callKey must be " + CALL_KEY_LENGTH + " bytes, got " + callKey.length);
        }
        var jidBytes = participantJid.getBytes(StandardCharsets.US_ASCII);
        var info = new byte[INFO_LABEL.length + jidBytes.length];
        System.arraycopy(INFO_LABEL, 0, info, 0, INFO_LABEL.length);
        System.arraycopy(jidBytes, 0, info, INFO_LABEL.length, jidBytes.length);
        var salt = Arrays.copyOfRange(callKey, 0, HALF);
        var ikm = Arrays.copyOfRange(callKey, HALF, CALL_KEY_LENGTH);
        try {
            var hkdf = KDF.getInstance("HKDF-SHA256");
            var params = HKDFParameterSpec.ofExtract()
                    .addIKM(ikm)
                    .addSalt(salt)
                    .thenExpand(info, BASE_KEY_LENGTH);
            return hkdf.deriveData(params);
        } catch (GeneralSecurityException e) {
            throw new WhatsAppCallException.Srtp(
                    "Cannot derive SFrame base key for " + participantJid, e);
        }
    }
}
