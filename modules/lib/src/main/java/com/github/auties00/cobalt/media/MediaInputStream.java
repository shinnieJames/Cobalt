package com.github.auties00.cobalt.media;

import com.github.auties00.cobalt.exception.WhatsAppMediaException;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;

import javax.crypto.Cipher;
import javax.crypto.KDF;
import javax.crypto.Mac;
import javax.crypto.spec.HKDFParameterSpec;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Objects;

/**
 * Shared base class for the streaming media encryption and decryption
 * pipelines.
 *
 * <p>Encapsulates the cryptographic primitives used by both
 * {@link MediaUploadInputStream} (the encrypt-and-HMAC pipeline) and
 * {@link MediaDownloadInputStream} (the HMAC-and-decrypt pipeline):
 * HKDF-SHA256 key derivation, AES-CBC cipher creation with PKCS5 padding,
 * HMAC-SHA256 initialization, and SHA-256 hashing.
 *
 * <p>Key derivation follows WhatsApp's media crypto protocol: a 32-byte
 * random media key is HKDF-expanded (with no salt and the media-type
 * specific info string) into {@value #EXPANDED_SIZE} bytes and then
 * sliced into four sub-keys: a 16-byte IV, a 32-byte AES cipher key, a
 * 32-byte HMAC key, and a 32-byte reference key used for media previews.
 *
 * @implNote WAMediaCrypto: {@code computeMediaKeys},
 * {@code encryptAndHmac}, {@code hmacAndDecrypt}, {@code IV_LENGTH}, and
 * {@code HMAC_LENGTH}. The derivation follows the extract-then-expand
 * pattern of WA Web's {@code WACryptoHkdf.extractAndExpand}.
 */
@WhatsAppWebModule(moduleName = "WAMediaCrypto")
abstract class MediaInputStream extends InputStream {
    /**
     * The default buffer size in bytes used for streaming reads and
     * writes. Chosen to match the typical {@link InputStream} buffer
     * size and to keep cipher block boundaries aligned.
     *
     * @implNote WAMediaCrypto: internal buffer sizing for
     * {@code encryptAndHmac} and {@code hmacAndDecrypt}.
     */
    static final int BUFFER_LENGTH = 8192;

    /**
     * The number of trailing HMAC bytes appended to the ciphertext.
     *
     * <p>WhatsApp computes a full 32-byte HMAC-SHA256 over {@code IV ||
     * ciphertext} but only publishes the first 10 bytes; this truncation
     * is sufficient for integrity while reducing overhead for media that
     * is often transferred over constrained links.
     *
     * @implNote WAMediaCrypto.HMAC_LENGTH: module constant {@code v = 10}.
     */
    @WhatsAppWebExport(moduleName = "WAMediaCrypto", exports = "HMAC_LENGTH",
            adaptation = WhatsAppAdaptation.DIRECT)
    static final int MAC_LENGTH = 10;

    /**
     * The total expanded key material size in bytes produced by HKDF
     * during media key derivation.
     *
     * <p>The 112 bytes are partitioned as:
     * <ul>
     *   <li>bytes 0 through 15: initialization vector (IV)</li>
     *   <li>bytes 16 through 47: AES-CBC cipher key</li>
     *   <li>bytes 48 through 79: HMAC-SHA256 key</li>
     *   <li>bytes 80 through 111: reference key used for media previews</li>
     * </ul>
     *
     * @implNote WAMediaCrypto.computeMediaKeys: {@code p} helper that
     * expands the media key to 112 bytes.
     */
    @WhatsAppWebExport(moduleName = "WAMediaCrypto", exports = "computeMediaKeys",
            adaptation = WhatsAppAdaptation.DIRECT)
    static final int EXPANDED_SIZE = 112;

    /**
     * The symmetric key length in bytes for both the AES cipher key and
     * the HMAC key, each occupying 32 bytes of the expanded material.
     *
     * @implNote WAMediaCrypto.computeMediaKeys: cipherKey and hmacKey
     * are both 32 bytes wide in the output partition.
     */
    @WhatsAppWebExport(moduleName = "WAMediaCrypto", exports = "computeMediaKeys",
            adaptation = WhatsAppAdaptation.DIRECT)
    static final int KEY_LENGTH = 32;

    /**
     * The initialization vector length in bytes for AES-CBC encryption.
     *
     * @implNote WAMediaCrypto.IV_LENGTH: module constant {@code b = 16}.
     */
    @WhatsAppWebExport(moduleName = "WAMediaCrypto", exports = "IV_LENGTH",
            adaptation = WhatsAppAdaptation.DIRECT)
    static final int IV_LENGTH = 16;

    /**
     * The AES-CBC block size in bytes.
     *
     * <p>Used both when sizing the ciphertext staging buffer so that
     * {@code Cipher.doFinal} can emit one trailing PKCS5 padding block
     * without reallocation, and as the stride length that the partial
     * chunk variant of {@code hmacAndDecrypt} relies on when re-chaining
     * the IV between chunks.
     *
     * @implNote WAMediaCrypto.CBC_BLOCK_SIZE: module constant {@code C = 16}.
     * Cobalt also exposes this value at runtime via {@link javax.crypto.Cipher#getBlockSize()}
     * on the AES cipher instance; the two are guaranteed to match for
     * {@code AES/CBC/PKCS5Padding}.
     */
    @WhatsAppWebExport(moduleName = "WAMediaCrypto", exports = "CBC_BLOCK_SIZE",
            adaptation = WhatsAppAdaptation.DIRECT)
    static final int CBC_BLOCK_SIZE = 16;

    /**
     * The underlying raw input stream providing the source data.
     *
     * <p>For the upload pipeline this carries plaintext to be encrypted;
     * for the download pipeline it carries the ciphertext-plus-HMAC
     * payload delivered by the CDN.
     *
     * @implNote WAMediaCrypto: the plaintext source for
     * {@code encryptAndHmac} and the ciphertext source for
     * {@code hmacAndDecrypt}.
     */
    final InputStream rawInputStream;

    /**
     * Constructs a new media input stream wrapping the specified raw
     * input stream.
     *
     * @param rawInputStream the underlying input stream, must not be {@code null}
     * @throws NullPointerException if {@code rawInputStream} is {@code null}
     */
    MediaInputStream(InputStream rawInputStream) {
        this.rawInputStream = Objects.requireNonNull(rawInputStream, "rawInputStream must not be null");
    }

    /**
     * Derives the expanded key material for a media payload from the raw
     * 32-byte media key and the media-type specific info string.
     *
     * <p>Performs HKDF-SHA256 extract-then-expand with no explicit salt
     * (which defaults to 32 zero bytes per RFC 5869) and the UTF-8
     * encoding of the key name as the info parameter. Callers slice the
     * returned {@value #EXPANDED_SIZE}-byte array into the IV, AES cipher
     * key, HMAC key, and reference key.
     *
     * @implNote WAMediaCrypto.computeMediaKeys: invokes
     * {@code WACryptoHkdf.extractAndExpand(mediaKey, info, 112)}.
     * @param mediaKey     the 32-byte raw media key
     * @param mediaKeyName the HKDF info string identifying the media type,
     *                     for example {@code "WhatsApp Image Keys"}
     * @return the {@value #EXPANDED_SIZE}-byte expanded key material
     * @throws WhatsAppMediaException if the HKDF derivation fails
     */
    @WhatsAppWebExport(moduleName = "WAMediaCrypto", exports = "computeMediaKeys",
            adaptation = WhatsAppAdaptation.DIRECT)
    byte[] deriveMediaKeyData(byte[] mediaKey, String mediaKeyName) throws WhatsAppMediaException {
        try {
            // WAMediaCrypto.computeMediaKeys
            // Uses JDK's KDF API to perform HKDF-SHA256 extract then expand,
            // matching WACryptoHkdf.extractAndExpand(mediaKey, info, 112)
            var hkdf = KDF.getInstance("HKDF-SHA256");
            var params = HKDFParameterSpec.ofExtract()
                    .addIKM(mediaKey)
                    .thenExpand(mediaKeyName.getBytes(StandardCharsets.UTF_8), EXPANDED_SIZE);
            return hkdf.deriveData(params);
        } catch (GeneralSecurityException e) {
            throw new WhatsAppMediaException("Cannot derive media key data", e);
        }
    }

    /**
     * Creates a fresh SHA-256 {@link MessageDigest} for plaintext or
     * ciphertext hashing.
     *
     * @implNote WAMediaCrypto: SHA-256 is used to compute the
     * {@code fileSha256} and {@code fileEncSha256} values recorded on the
     * media message protobuf.
     * @return a fresh {@link MessageDigest} for SHA-256
     * @throws WhatsAppMediaException if the SHA-256 algorithm is not available
     */
    @WhatsAppWebExport(moduleName = "WAMediaCrypto",
            exports = {"encryptAndHmac", "hmacAndDecrypt"},
            adaptation = WhatsAppAdaptation.DIRECT)
    MessageDigest newHash() throws WhatsAppMediaException {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (GeneralSecurityException exception) {
            throw new WhatsAppMediaException("Cannot create new hash", exception);
        }
    }

    /**
     * Creates and initialises a new AES-CBC cipher with PKCS5 padding.
     *
     * @implNote WAMediaCrypto.encryptAndHmac uses
     * {@code WACryptoAesCbc.AesCbcStream} for streaming encryption;
     * WAMediaCrypto.hmacAndDecrypt uses
     * {@code WACryptoAesCbc.aesCbcDecrypt} for batch decryption.
     * @param mode the cipher mode, either {@link Cipher#ENCRYPT_MODE} or
     *             {@link Cipher#DECRYPT_MODE}
     * @param key  the AES secret key specification
     * @param iv   the initialization vector
     * @return the initialized {@link Cipher}
     * @throws WhatsAppMediaException if cipher creation or initialization fails
     */
    @WhatsAppWebExport(moduleName = "WAMediaCrypto",
            exports = {"encryptAndHmac", "hmacAndDecrypt"},
            adaptation = WhatsAppAdaptation.DIRECT)
    Cipher newCipher(int mode, SecretKeySpec key, IvParameterSpec iv) throws WhatsAppMediaException {
        try {
            var cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(mode, key, iv);
            return cipher;
        } catch (GeneralSecurityException exception) {
            throw new WhatsAppMediaException("Cannot create new cipher", exception);
        }
    }

    /**
     * Creates and initialises a new HMAC-SHA256 {@link Mac} bound to the
     * supplied HMAC key.
     *
     * @implNote WAMediaCrypto.encryptAndHmac uses
     * {@code WACryptoHmac.encodeKeySha256} combined with
     * {@code WACryptoHmac.sign}; WAMediaCrypto.hmacAndDecrypt uses
     * {@code WACryptoHmac.hmacSha256}.
     * @param key the HMAC-SHA256 secret key specification
     * @return the initialized {@link Mac}
     * @throws WhatsAppMediaException if MAC creation or initialization fails
     */
    @WhatsAppWebExport(moduleName = "WAMediaCrypto",
            exports = {"encryptAndHmac", "hmacAndDecrypt"},
            adaptation = WhatsAppAdaptation.DIRECT)
    Mac newMac(SecretKeySpec key) throws WhatsAppMediaException {
        try {
            var mac = Mac.getInstance("HmacSHA256");
            mac.init(key);
            return mac;
        } catch (GeneralSecurityException exception) {
            throw new WhatsAppMediaException("Cannot create new mac", exception);
        }
    }

    /**
     * Closes the underlying raw input stream.
     *
     * @throws IOException if an I/O error occurs while closing
     */
    @Override
    public void close() throws IOException {
        rawInputStream.close();
    }
}
