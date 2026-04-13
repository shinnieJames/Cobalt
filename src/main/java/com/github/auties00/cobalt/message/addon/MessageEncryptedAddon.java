package com.github.auties00.cobalt.message.addon;

import java.util.Objects;

/**
 * Represents an encrypted add-on message containing the AES-GCM ciphertext and IV.
 * <p>
 * This record holds the result of inner (dual) encryption performed by
 * {@link MessageAddonEncryption#encrypt}. The ciphertext includes the 16-byte
 * GCM authentication tag appended by the cipher.
 *
 * @param ciphertext the AES-GCM encrypted content (includes 16-byte auth tag)
 * @param iv         the 12-byte initialization vector
 *
 * @implNote WAWebAddonEncryption.encryptAddOn: returns {@code {encPayload: L}} where
 *           {@code L} is the output of {@code WACryptoAesGcm.gcmEncrypt}, and the
 *           IV is passed alongside as a separate field.
 */
public record MessageEncryptedAddon(byte[] ciphertext, byte[] iv) {
    /**
     * The expected size of the AES-GCM initialization vector in bytes.
     *
     * @implNote WACryptoAesGcm: standard 12-byte IV for AES-GCM.
     */
    private static final int AES_GCM_IV_SIZE = 12;

    /**
     * Constructs a new encrypted add-on message with validation.
     *
     * @param ciphertext the AES-GCM encrypted content (includes 16-byte auth tag)
     * @param iv         the 12-byte initialization vector
     * @throws NullPointerException     if {@code ciphertext} or {@code iv} is {@code null}
     * @throws IllegalArgumentException if {@code iv} is not 12 bytes
     *
     * @implNote WAWebAddonEncryption.encryptAddOn: produces the ciphertext via
     *           {@code WACryptoAesGcm.gcmEncrypt} and the IV is a caller-provided parameter.
     */
    public MessageEncryptedAddon {
        Objects.requireNonNull(ciphertext, "ciphertext cannot be null");
        Objects.requireNonNull(iv, "iv cannot be null");
        if (iv.length != AES_GCM_IV_SIZE) {
            throw new IllegalArgumentException("IV must be " + AES_GCM_IV_SIZE + " bytes");
        }
    }
}
