package com.github.auties00.cobalt.message.addon;

import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.util.FastRandomUtils;

import javax.crypto.Cipher;
import javax.crypto.KDF;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.HKDFParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.InvalidAlgorithmParameterException;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;

/**
 * Service for dual encryption of add-on messages (poll votes, CAG reactions, comments,
 * event responses, event edits, poll edits, poll add options, message edits).
 * <p>
 * Add-on messages require two layers of encryption:
 * <ol>
 *   <li><b>Inner encryption (AES-GCM):</b> Encrypts the actual content using a use-case
 *       secret derived from the parent message's messageSecret via HKDF.</li>
 *   <li><b>Outer encryption (Signal):</b> The wrapped message is then encrypted with
 *       the standard Signal protocol for E2E delivery.</li>
 * </ol>
 * <p>
 * This dual encryption ensures that:
 * <ul>
 *   <li>Each add-on is bound to a specific parent message via stanzaId</li>
 *   <li>Identity binding to both original sender and add-on sender</li>
 *   <li>Context separation for different use-case types</li>
 *   <li>Forward secrecy with new secret per parent message</li>
 * </ul>
 *
 * @implNote WAWebAddonEncryption: provides {@code encryptAddOn} and {@code decryptAddOn}.
 */
public final class MessageAddonEncryption {
    /**
     * The size of the AES-GCM initialization vector in bytes.
     *
     * @implNote WACryptoAesGcm: IV is always 12 bytes for AES-GCM.
     */
    private static final int AES_GCM_IV_SIZE = 12;

    /**
     * The AES-GCM authentication tag size in bits.
     *
     * @implNote WACryptoAesGcm.gcmEncrypt: {@code tagLength: a * 8} where {@code a} defaults to 16,
     *           producing 128 bits.
     */
    private static final int AES_GCM_TAG_SIZE = 128; // bits

    /**
     * The output size for the HKDF-derived use-case secret in bytes.
     *
     * @implNote WAUseCaseSecret: {@code var s = 32}
     */
    private static final int HKDF_OUTPUT_SIZE = 32;

    /**
     * The AES-GCM cipher algorithm identifier.
     *
     * @implNote WACryptoAesGcm: uses Web Crypto API with {@code name: "AES-GCM"}.
     */
    private static final String AES_GCM_ALGORITHM = "AES/GCM/NoPadding";

    /**
     * The HKDF algorithm identifier.
     *
     * @implNote WACryptoHkdf.extractAndExpand: uses HMAC-SHA-256 for both extract and expand steps.
     */
    private static final String HKDF_ALGORITHM = "HKDF-SHA256";

    /**
     * Private constructor to prevent instantiation of this utility class.
     *
     * @implNote WAWebAddonEncryption: module-level functions, not a class.
     */
    private MessageAddonEncryption() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    /**
     * Encrypts add-on content with dual encryption.
     * <p>
     * Derives a use-case secret from the parent message's message secret via HKDF
     * extract-and-expand, generates a random 12-byte IV, then encrypts the plaintext
     * using AES-256-GCM. For poll votes and event responses, additional authenticated
     * data (AAD) is included to bind the ciphertext to the specific stanza and sender.
     *
     * @param plaintext      the add-on content to encrypt
     * @param messageSecret  the parent message's 32-byte secret
     * @param stanzaId       the parent message's stanza ID
     * @param originalSender the JID of the original message sender (poll creator)
     * @param addonSender    the JID of the add-on sender (voter/reactor)
     * @param useCaseType    the type of add-on
     * @return the encrypted result containing ciphertext and IV
     * @throws IllegalArgumentException if {@code messageSecret} is not 32 bytes
     *
     * @implNote WAWebAddonEncryption.encryptAddOn
     */
    public static MessageEncryptedAddon encrypt(
            byte[] plaintext,
            byte[] messageSecret,
            String stanzaId,
            Jid originalSender,
            Jid addonSender,
            MessageAddonType useCaseType
    ) {
        Objects.requireNonNull(plaintext, "plaintext cannot be null");
        Objects.requireNonNull(messageSecret, "messageSecret cannot be null");
        Objects.requireNonNull(stanzaId, "stanzaId cannot be null");
        Objects.requireNonNull(originalSender, "originalSender cannot be null");
        Objects.requireNonNull(addonSender, "addonSender cannot be null");
        Objects.requireNonNull(useCaseType, "useCaseType cannot be null");

        if (messageSecret.length != 32) {
            throw new IllegalArgumentException("messageSecret must be 32 bytes");
        }

        try {
            // WAUseCaseSecret.createUseCaseSecret
            var useCaseSecret = deriveUseCaseSecret(messageSecret, stanzaId, originalSender, addonSender, useCaseType);

            // Generate random IV
            var iv = FastRandomUtils.randomByteArray(AES_GCM_IV_SIZE);

            // WACryptoAesGcm.gcmEncrypt(S, i, R, d(t.type, ...))
            var cipher = Cipher.getInstance(AES_GCM_ALGORITHM);
            var keySpec = new SecretKeySpec(useCaseSecret, "AES");
            var gcmSpec = new GCMParameterSpec(AES_GCM_TAG_SIZE, iv);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, gcmSpec);

            // WAWebAddonEncryption.d(): AAD for PollVote and EventResponse types
            if (useCaseType.usesAad()) {
                var aad = buildAad(stanzaId, addonSender);
                cipher.updateAAD(aad);
            }

            var ciphertext = cipher.doFinal(plaintext);

            return new MessageEncryptedAddon(ciphertext, iv);

        } catch (GeneralSecurityException e) {
            // ADAPTED: WAWebAddonEncryption.encryptAddOn throws DualEncryptionValidationError
            throw new RuntimeException("Failed to encrypt add-on", e);
        }
    }

    /**
     * Decrypts add-on content.
     * <p>
     * Derives a use-case secret from the parent message's message secret via HKDF
     * extract-and-expand, then decrypts the ciphertext using AES-256-GCM with the
     * stored IV. For poll votes and event responses, additional authenticated data
     * (AAD) must match what was used during encryption.
     *
     * @param encryptedAddon the encrypted add-on
     * @param messageSecret  the parent message's 32-byte secret
     * @param stanzaId       the parent message's stanza ID
     * @param originalSender the JID of the original message sender
     * @param addonSender    the JID of the add-on sender
     * @param useCaseType    the type of add-on
     * @return the decrypted plaintext
     * @throws IllegalArgumentException if {@code messageSecret} is not 32 bytes
     *
     * @implNote WAWebAddonEncryption.decryptAddOn
     */
    public static byte[] decrypt(
            MessageEncryptedAddon encryptedAddon,
            byte[] messageSecret,
            String stanzaId,
            Jid originalSender,
            Jid addonSender,
            MessageAddonType useCaseType
    ) {
        Objects.requireNonNull(encryptedAddon, "encryptedAddon cannot be null");
        Objects.requireNonNull(messageSecret, "messageSecret cannot be null");
        Objects.requireNonNull(stanzaId, "stanzaId cannot be null");
        Objects.requireNonNull(originalSender, "originalSender cannot be null");
        Objects.requireNonNull(addonSender, "addonSender cannot be null");
        Objects.requireNonNull(useCaseType, "useCaseType cannot be null");

        if (messageSecret.length != 32) {
            throw new IllegalArgumentException("messageSecret must be 32 bytes");
        }

        try {
            // WAUseCaseSecret.createUseCaseSecret
            var useCaseSecret = deriveUseCaseSecret(messageSecret, stanzaId, originalSender, addonSender, useCaseType);

            // WACryptoAesGcm.gcmDecrypt(_, a, e.encryptedAddOn, d(e.type, ...))
            var cipher = Cipher.getInstance(AES_GCM_ALGORITHM);
            var keySpec = new SecretKeySpec(useCaseSecret, "AES");
            var gcmSpec = new GCMParameterSpec(AES_GCM_TAG_SIZE, encryptedAddon.iv());
            cipher.init(Cipher.DECRYPT_MODE, keySpec, gcmSpec);

            // WAWebAddonEncryption.d(): AAD for PollVote and EventResponse types
            if (useCaseType.usesAad()) {
                var aad = buildAad(stanzaId, addonSender);
                cipher.updateAAD(aad);
            }

            return cipher.doFinal(encryptedAddon.ciphertext());

        } catch (GeneralSecurityException e) {
            // ADAPTED: WAWebAddonEncryption.decryptAddOn throws DualEncryptionValidationError
            throw new RuntimeException("Failed to decrypt add-on", e);
        }
    }

    /**
     * Derives the use-case secret from the parent message's secret via HKDF extract-and-expand.
     * <p>
     * The extract step uses {@code null} salt (defaulting to a zero-filled byte array of
     * SHA-256 hash length) and the message secret as the input keying material. The expand
     * step uses the concatenated info parameter and produces a 32-byte output.
     *
     * @param messageSecret  the parent message's 32-byte secret
     * @param stanzaId       the parent message's stanza ID
     * @param originalSender the JID of the original message sender (poll creator)
     * @param addonSender    the JID of the add-on sender (voter/reactor)
     * @param useCaseType    the type of add-on
     * @return the derived 32-byte use-case secret
     * @throws NoSuchAlgorithmException           if HKDF-SHA256 is unavailable
     * @throws InvalidAlgorithmParameterException if the HKDF parameters are invalid
     *
     * @implNote WAUseCaseSecret.createUseCaseSecret: calls
     *           {@code WACryptoHkdf.extractAndExpand(messageSecret, info, 32)} which performs
     *           {@code extractSha256(null, messageSecret)} then {@code expand(prk, info, 32)}.
     */
    private static byte[] deriveUseCaseSecret(
            byte[] messageSecret,
            String stanzaId,
            Jid originalSender,
            Jid addonSender,
            MessageAddonType useCaseType
    ) throws NoSuchAlgorithmException, InvalidAlgorithmParameterException {
        // WAUseCaseSecret.createUseCaseSecret: Binary.build(stanzaId, parentMsgOriginalSender, modificationSender, modificationType).readBuffer()
        var info = buildUseCaseInfo(stanzaId, originalSender, addonSender, useCaseType);

        // WACryptoHkdf.extractAndExpand(messageSecret, info, 32)
        // Extract with null salt (zeros), then expand with info
        var kdf = KDF.getInstance(HKDF_ALGORITHM);
        var params = HKDFParameterSpec.ofExtract()
                .addIKM(messageSecret) // WACryptoHkdf.extractAndExpand: first argument is IKM
                .thenExpand(info, HKDF_OUTPUT_SIZE); // WACryptoHkdf.expand: prk, info, 32

        return kdf.deriveData(params);
    }

    /**
     * Builds the use-case info for HKDF derivation.
     * <p>
     * The info is the raw byte concatenation of the UTF-8 encodings of:
     * {@code stanzaId || originalSender || addonSender || useCaseType.value()}.
     *
     * @param stanzaId       the parent message's stanza ID
     * @param originalSender the JID of the original message sender
     * @param addonSender    the JID of the add-on sender
     * @param useCaseType    the type of add-on
     * @return the info bytes for HKDF expand
     *
     * @implNote WAUseCaseSecret.createUseCaseSecret: {@code WABinary.Binary.build(stanzaId,
     *           parentMsgOriginalSender, modificationSender, modificationType).readBuffer()}.
     *           {@code Binary.build} writes each string argument as UTF-8 bytes sequentially.
     */
    private static byte[] buildUseCaseInfo(
            String stanzaId,
            Jid originalSender,
            Jid addonSender,
            MessageAddonType useCaseType
    ) {
        var stanzaIdBytes = stanzaId.getBytes(StandardCharsets.UTF_8);
        var originalSenderBytes = originalSender.toString().getBytes(StandardCharsets.UTF_8);
        var addonSenderBytes = addonSender.toString().getBytes(StandardCharsets.UTF_8);
        var useCaseBytes = useCaseType.value().getBytes(StandardCharsets.UTF_8);

        var info = new byte[stanzaIdBytes.length + originalSenderBytes.length + addonSenderBytes.length + useCaseBytes.length];
        var offset = 0;

        System.arraycopy(stanzaIdBytes, 0, info, offset, stanzaIdBytes.length);
        offset += stanzaIdBytes.length;

        System.arraycopy(originalSenderBytes, 0, info, offset, originalSenderBytes.length);
        offset += originalSenderBytes.length;

        System.arraycopy(addonSenderBytes, 0, info, offset, addonSenderBytes.length);
        offset += addonSenderBytes.length;

        System.arraycopy(useCaseBytes, 0, info, offset, useCaseBytes.length);

        return info;
    }

    /**
     * Builds the AAD (Additional Authenticated Data) for types that require it.
     * <p>
     * Format: {@code stanzaId + "\0" + addonSenderJid} encoded as UTF-8.
     * This prevents substitution attacks where an attacker might try
     * to copy an encrypted vote/response from one user to another.
     *
     * @param stanzaId    the parent message's stanza ID
     * @param addonSender the JID of the add-on sender
     * @return the AAD bytes
     *
     * @implNote WAWebAddonEncryption.d: returns {@code stanzaId + "\0" + addOnSenderJid} for
     *           PollVote and EventResponse types, passed to
     *           {@code WACryptoAesGcm.gcmEncrypt/gcmDecrypt} as the AAD parameter.
     */
    private static byte[] buildAad(String stanzaId, Jid addonSender) {
        var stanzaIdBytes = stanzaId.getBytes(StandardCharsets.UTF_8);
        var senderBytes = addonSender.toString().getBytes(StandardCharsets.UTF_8);

        var aad = new byte[stanzaIdBytes.length + 1 + senderBytes.length];
        var offset = 0;

        System.arraycopy(stanzaIdBytes, 0, aad, offset, stanzaIdBytes.length);
        offset += stanzaIdBytes.length;

        aad[offset] = 0x00; // WAWebAddonEncryption.d: "\0" separator
        offset++;

        System.arraycopy(senderBytes, 0, aad, offset, senderBytes.length);

        return aad;
    }
}
