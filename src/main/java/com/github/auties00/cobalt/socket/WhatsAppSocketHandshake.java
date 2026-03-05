package com.github.auties00.cobalt.socket;

import com.github.auties00.cobalt.util.GcmUtils;

import javax.crypto.*;
import javax.crypto.spec.HKDFParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import javax.security.auth.DestroyFailedException;
import java.nio.charset.StandardCharsets;
import java.security.*;

/**
 * A Noise protocol handshake helper for the WhatsApp Noise XX key exchange.
 *
 * <p>This class manages the cryptographic state needed during the Noise XX
 * handshake: a chained hash, a salt (for HKDF key derivation), a symmetric
 * cipher key, and a nonce counter.  Each call to {@link #cipher(byte[], boolean)}
 * encrypts or decrypts a payload and updates the chained hash, advancing the
 * handshake state.
 *
 * <p>After the handshake completes, {@link #finish()} derives the final
 * read and write keys (64 bytes total: 32 bytes write key followed by 32
 * bytes read key).
 *
 * <p>Instances are {@link AutoCloseable} and should be used in a
 * try-with-resources block to ensure key material is destroyed promptly.
 */
final class WhatsAppSocketHandshake implements AutoCloseable {
    /**
     * The Noise protocol name, null-padded to 32 bytes.
     */
    private static final byte[] NOISE_PROTOCOL = "Noise_XX_25519_AESGCM_SHA256\0\0\0\0".getBytes(StandardCharsets.UTF_8);

    /**
     * Empty IKM used by {@link #finish()} to derive final keys.
     */
    private static final byte[] FINISH_KEY = new byte[0];

    /**
     * The HKDF-SHA256 key derivation function instance.
     */
    private final KDF kdf;

    /**
     * The SHA-256 message digest for hash chaining.
     */
    private final MessageDigest hashDigest;

    /**
     * The AES/GCM/NoPadding cipher for handshake payloads.
     */
    private final Cipher cipher;

    /**
     * The current chained hash value.
     */
    private byte[] hash;

    /**
     * The current HKDF salt, derived from key mixing operations.
     */
    private SecretKeySpec salt;

    /**
     * The current symmetric cipher key.
     */
    private SecretKeySpec cryptoKey;

    /**
     * The nonce counter for the current cipher key.
     */
    private long counter;

    /**
     * Creates a new handshake helper and initializes the chained hash with
     * the given prologue.
     *
     * @param prologue the protocol prologue bytes (version header)
     * @throws NoSuchAlgorithmException if HKDF-SHA256 or SHA-256 is unavailable
     * @throws NoSuchPaddingException   if AES/GCM/NoPadding is unavailable
     */
    WhatsAppSocketHandshake(byte[] prologue) throws NoSuchAlgorithmException, NoSuchPaddingException {
        this.kdf = KDF.getInstance("HKDF-SHA256");
        this.hashDigest = MessageDigest.getInstance("SHA-256");
        this.cipher = Cipher.getInstance("AES/GCM/NoPadding");
        this.hash = NOISE_PROTOCOL;
        this.salt = new SecretKeySpec(NOISE_PROTOCOL, "AES");
        this.cryptoKey = new SecretKeySpec(NOISE_PROTOCOL, 0, 32, "AES");
        this.counter = 0;
        updateHash(prologue);
    }

    /**
     * Updates the chained hash by hashing the current hash concatenated
     * with the given data.
     *
     * @param data the data to mix into the hash
     */
    public void updateHash(byte[] data) {
        hashDigest.update(hash);
        hashDigest.update(data);
        this.hash = hashDigest.digest();
    }

    /**
     * Encrypts or decrypts a payload using the current cipher key and
     * updates the chained hash.
     *
     * <p>The current hash is used as AAD (authenticated additional data).
     * After encryption, the ciphertext is mixed into the hash; after
     * decryption, the original ciphertext (input) is mixed.
     *
     * @param text    the plaintext (if encrypting) or ciphertext (if decrypting)
     * @param encrypt {@code true} to encrypt, {@code false} to decrypt
     * @return the ciphertext (if encrypting) or plaintext (if decrypting)
     * @throws IllegalBlockSizeException          if the text length is invalid
     * @throws BadPaddingException                if authentication fails (decrypt)
     * @throws InvalidAlgorithmParameterException if the nonce is invalid
     * @throws InvalidKeyException                if the cipher key is invalid
     */
    byte[] cipher(byte[] text, boolean encrypt) throws IllegalBlockSizeException, BadPaddingException, InvalidAlgorithmParameterException, InvalidKeyException {
        cipher.init(
                encrypt ? Cipher.ENCRYPT_MODE : Cipher.DECRYPT_MODE,
                cryptoKey,
                GcmUtils.createNonce(counter++)
        );
        cipher.updateAAD(hash);
        var result = cipher.doFinal(text);
        updateHash(encrypt ? result : text);
        return result;
    }

    /**
     * Derives the final 64-byte key material (32 bytes write key + 32
     * bytes read key) from the current salt using HKDF with an empty IKM.
     *
     * @return the 64-byte key material
     * @throws GeneralSecurityException if key derivation fails
     */
    byte[] finish() throws GeneralSecurityException {
        var params = HKDFParameterSpec.ofExtract()
                .addSalt(salt)
                .addIKM(FINISH_KEY)
                .thenExpand(null, 64);
        return kdf.deriveData(params);
    }

    /**
     * Mixes the given key material into the handshake state using HKDF.
     *
     * <p>This updates both the salt and the cipher key, and resets the
     * nonce counter to zero.
     *
     * @param bytes the key material to mix in (typically a shared secret)
     * @throws GeneralSecurityException if key derivation fails
     */
    void mixIntoKey(byte[] bytes) throws GeneralSecurityException {
        var params = HKDFParameterSpec.ofExtract()
                .addSalt(salt)
                .addIKM(bytes)
                .thenExpand(null, 64);
        var expanded = kdf.deriveData(params);
        this.salt = new SecretKeySpec(expanded, 0, 32, "AES");
        this.cryptoKey = new SecretKeySpec(expanded, 32, 32, "AES");
        this.counter = 0;
    }

    @Override
    public void close() {
        this.hash = null;
        this.salt = null;
        try {
            cryptoKey.destroy();
        } catch (DestroyFailedException _) {

        }
        this.cryptoKey = null;
        this.counter = 0;
    }
}
