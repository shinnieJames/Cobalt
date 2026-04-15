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
 *
 * @implNote WANoiseHandshake.NoiseHandshake
 */
final class WhatsAppSocketHandshake implements AutoCloseable {
    /**
     * The Noise protocol name, null-padded to 32 bytes.
     *
     * <p>Since this value is exactly 32 bytes, it is used directly as the
     * initial hash, salt, and cipher key without SHA-256 hashing.
     *
     * @implNote WANoiseHandshake.NoiseHandshake.start — the protocol name
     *     is used directly when its byte length equals 32
     */
    private static final byte[] NOISE_PROTOCOL = "Noise_XX_25519_AESGCM_SHA256\0\0\0\0".getBytes(StandardCharsets.UTF_8);

    /**
     * Empty IKM used by {@link #finish()} to derive final keys.
     *
     * @implNote WANoiseHandshake.finish — corresponds to
     *     {@code new Uint8Array(0)} passed as IKM to HKDF
     */
    private static final byte[] FINISH_KEY = new byte[0];

    /**
     * The HKDF-SHA256 key derivation function instance.
     *
     * @implNote WANoiseHandshake — corresponds to
     *     {@code WACryptoHkdf.extractWithSaltAndExpand} calls
     */
    private final KDF kdf;

    /**
     * The SHA-256 message digest for hash chaining.
     *
     * @implNote WANoiseHandshake.authenticate — corresponds to
     *     {@code WACryptoSha256.sha256} calls used in authenticate
     */
    private final MessageDigest hashDigest;

    /**
     * The AES/GCM/NoPadding cipher for handshake payloads.
     *
     * @implNote WANoiseHandshake — corresponds to
     *     {@code WACryptoDependencies.getCrypto().subtle.encrypt/decrypt}
     *     with {@code AES-GCM} algorithm
     */
    private final Cipher cipher;

    /**
     * The current chained hash value.
     *
     * @implNote WANoiseHandshake.NoiseHandshake — field {@code $2}
     */
    private byte[] hash;

    /**
     * The current HKDF salt, derived from key mixing operations.
     *
     * @implNote WANoiseHandshake.NoiseHandshake — field {@code $3}
     */
    private SecretKeySpec salt;

    /**
     * The current symmetric cipher key.
     *
     * @implNote WANoiseHandshake.NoiseHandshake — field {@code $4},
     *     stored as raw bytes rather than a {@code CryptoKey} object
     */
    private SecretKeySpec cryptoKey;

    /**
     * The nonce counter for the current cipher key.
     *
     * @implNote WANoiseHandshake.NoiseHandshake — field {@code $5}
     */
    private long counter;

    /**
     * Creates a new handshake helper and initializes the chained hash with
     * the given prologue.
     *
     * <p>This constructor merges the WA Web {@code constructor} and {@code start}
     * methods: it sets the hash, salt, and cipher key to the Noise protocol
     * name (which is exactly 32 bytes, so no SHA-256 hashing is needed),
     * then mixes in the prologue via {@link #updateHash(byte[])}.
     *
     * @implNote WANoiseHandshake.NoiseHandshake.constructor and
     *     WANoiseHandshake.NoiseHandshake.start
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
     * @implNote WANoiseHandshake.NoiseHandshake.authenticate — computes
     *     {@code SHA-256(hash || data)} and stores as the new hash
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
     * @implNote WANoiseHandshake.NoiseHandshake.encrypt and
     *     WANoiseHandshake.NoiseHandshake.decrypt — unified into a single
     *     method with a boolean flag; uses AES-GCM with a 12-byte nonce
     *     (4 zero bytes followed by the big-endian 64-bit counter)
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
     * <p>In WA Web, this method returns a {@code NoiseSocket} wrapping the
     * derived encrypt and decrypt keys. In Cobalt, the raw 64-byte key
     * material is returned and the caller constructs the cipher state.
     *
     * @implNote WANoiseHandshake.NoiseHandshake.finish — calls
     *     {@code WACryptoHkdf.extractWithSaltAndExpand(IKM=empty, salt, "", 64)},
     *     then splits into encrypt key (bytes 0-31) and decrypt key (bytes 32-63)
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
     * @implNote WANoiseHandshake.NoiseHandshake.mixIntoKey — calls
     *     {@code WACryptoHkdf.extractWithSaltAndExpand(IKM=bytes, salt, "", 64)},
     *     then assigns bytes 0-31 as the new salt and bytes 32-63 as the new
     *     cipher key, and resets the counter to zero
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

    /**
     * Destroys all key material held by this handshake instance.
     *
     * <p>Sets the hash and salt references to {@code null}, attempts to
     * destroy the cipher key via {@link SecretKeySpec#destroy()}, and resets
     * the counter to zero.
     *
     * @implNote NO_WA_BASIS — Java-specific {@link AutoCloseable} adaptation
     *     for deterministic key material cleanup
     */
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
