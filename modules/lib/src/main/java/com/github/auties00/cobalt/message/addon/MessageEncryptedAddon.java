package com.github.auties00.cobalt.message.addon;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;

import java.util.Objects;

/**
 * Immutable pair of the AES-GCM ciphertext and IV produced by the inner
 * addon encryption layer.
 *
 * @apiNote Returned by {@link MessageAddonEncryption#encrypt} and consumed
 * by the per-addon protobuf builders ({@code EncCommentMessage},
 * {@code EncReactionMessage}, {@code PollEncValue}) which split this pair
 * into the {@code encPayload}/{@code encIv} wire fields. The ciphertext
 * includes the trailing 16-byte GCM authentication tag.
 *
 * @param ciphertext the AES-GCM ciphertext including the 16-byte auth tag
 * @param iv         the 12-byte initialisation vector used for this
 *                   ciphertext
 */
@WhatsAppWebModule(moduleName = "WAWebAddonEncryption")
public record MessageEncryptedAddon(byte[] ciphertext, byte[] iv) {
    /**
     * Expected size of the AES-GCM initialisation vector, in bytes.
     */
    private static final int AES_GCM_IV_SIZE = 12;

    /**
     * Validates the IV length and rejects {@code null} components.
     *
     * @apiNote Defends against accidental construction with a malformed IV;
     * the inner addon ciphers always use a 12-byte IV, and a mismatch here
     * would later surface as a more confusing
     * {@link java.security.InvalidAlgorithmParameterException} inside the
     * cipher.
     *
     * @throws NullPointerException     if {@code ciphertext} or {@code iv}
     *                                  is {@code null}
     * @throws IllegalArgumentException if {@code iv.length} is not exactly
     *                                  12
     */
    @WhatsAppWebExport(moduleName = "WAWebAddonEncryption", exports = "encryptAddOn",
            adaptation = WhatsAppAdaptation.ADAPTED)
    public MessageEncryptedAddon {
        Objects.requireNonNull(ciphertext, "ciphertext cannot be null");
        Objects.requireNonNull(iv, "iv cannot be null");
        if (iv.length != AES_GCM_IV_SIZE) {
            throw new IllegalArgumentException("IV must be " + AES_GCM_IV_SIZE + " bytes");
        }
    }
}
