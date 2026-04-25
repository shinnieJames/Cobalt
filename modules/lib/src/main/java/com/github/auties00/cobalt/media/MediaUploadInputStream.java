package com.github.auties00.cobalt.media;

import com.github.auties00.cobalt.exception.WhatsAppMediaException;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.media.MediaProvider;
import com.github.auties00.cobalt.util.DataUtils;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.io.InputStream;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Optional;

/**
 * Streams a media payload while transparently encrypting it in
 * preparation for upload to the WhatsApp CDN.
 *
 * <p>Two sealed variants cover the two kinds of media that WhatsApp
 * transfers:
 * <ul>
 *   <li>{@link Ciphertext}: used for media types with a non-empty key
 *       name in their {@link com.github.auties00.cobalt.model.media.MediaPath},
 *       which require end-to-end encryption (images, videos, audio,
 *       documents, stickers, and similar user-facing media).</li>
 *   <li>{@link Plaintext}: used for media types transmitted in the
 *       clear (newsletter media, profile pictures, business cover
 *       photos).</li>
 * </ul>
 *
 * <p>The encrypted stream format produced by {@link Ciphertext} is
 * {@code ciphertext || HMAC[0:10]}: the ciphertext is AES-CBC encrypted
 * plaintext with PKCS5 padding, and the trailing 10 bytes are the
 * truncated HMAC-SHA256 computed over {@code IV || ciphertext}. The IV
 * itself is not included in the stream; it is derived from the media
 * key via HKDF and transmitted separately in the message protobuf so
 * that recipients can reproduce the same derivation.
 *
 * @implNote WAMediaCrypto.encryptAndHmac: the batch encryption routine,
 * adapted here to a streaming input stream.
 * WAWebCryptoEncryptMedia: the WA Web wrapper that creates the media
 * keys, drives the encryption, and returns the hashes plus the key
 * material for the outgoing protobuf.
 */
@WhatsAppWebModule(moduleName = "WAMediaCrypto")
@WhatsAppWebModule(moduleName = "WAWebCryptoEncryptMedia")
public abstract sealed class MediaUploadInputStream extends MediaInputStream {
    /**
     * Constructs a new media upload input stream wrapping the given raw
     * plaintext stream.
     *
     * @param rawInputStream the underlying plaintext input stream
     */
    MediaUploadInputStream(InputStream rawInputStream) {
        super(rawInputStream);
    }

    /**
     * Returns the total number of plaintext bytes that have been read
     * and processed from the underlying stream so far.
     *
     * <p>Populated into the outgoing message protobuf's {@code fileLength}
     * field so that recipients can pre-allocate buffers and report
     * accurate progress.
     *
     * @implNote WAWebCryptoEncryptMedia: the plaintext byte count
     * recorded on the protobuf.
     * @return the plaintext file length in bytes
     */
    @WhatsAppWebExport(moduleName = "WAWebCryptoEncryptMedia", exports = "default",
            adaptation = WhatsAppAdaptation.DIRECT)
    public abstract long fileLength();

    /**
     * Returns the SHA-256 digest of the plaintext content.
     *
     * <p>Available only after the stream is fully consumed (reads return
     * {@code -1}). The value populates the outgoing message protobuf's
     * {@code fileSha256} field so that recipients can verify the
     * decrypted bytes after download.
     *
     * @implNote WAMediaCrypto.hmacAndDecrypt returns the corresponding
     * {@code plaintextHash} via
     * {@code WAHashUtils.toPlaintextHash(SHA-256(plaintext))}.
     * @return the plaintext SHA-256 hash
     * @throws IllegalStateException if the stream has not been fully consumed
     */
    @WhatsAppWebExport(moduleName = "WAWebCryptoEncryptMedia", exports = "default",
            adaptation = WhatsAppAdaptation.DIRECT)
    public abstract byte[] fileSha256();

    /**
     * Returns the SHA-256 digest of the encrypted payload
     * ({@code ciphertext || HMAC[0:10]}).
     *
     * <p>Available only after the stream is fully consumed. Populates
     * the outgoing message protobuf's {@code fileEncSha256} field; empty
     * for plaintext uploads that skip encryption.
     *
     * @implNote WAWebCryptoEncryptMedia.encryptMedia returns
     * {@code hash} (encFilehash) via
     * {@code WAMediaCalculateFilehash.calculateFilehash(ciphertext + hmac[0:10])}.
     * @return an {@link Optional} containing the encrypted SHA-256 hash, or
     *         empty for unencrypted media
     * @throws IllegalStateException if the stream has not been fully consumed
     */
    @WhatsAppWebExport(moduleName = "WAWebCryptoEncryptMedia", exports = "default",
            adaptation = WhatsAppAdaptation.DIRECT)
    public abstract Optional<byte[]> fileEncSha256();

    /**
     * Returns the 32-byte random media key used for HKDF key derivation.
     *
     * <p>The media key is stored in the outgoing message protobuf so
     * that recipients can reproduce the same IV, cipher key, and HMAC
     * key when decrypting. Empty for plaintext uploads.
     *
     * @implNote WAWebCryptoEncryptMedia.encryptMedia: generates the
     * random mediaKey and feeds it to
     * {@code WAWebCryptoCreateMediaKeys}.
     * @return an {@link Optional} containing the media key, or empty for
     *         unencrypted media
     */
    @WhatsAppWebExport(moduleName = "WAWebCryptoEncryptMedia", exports = "default",
            adaptation = WhatsAppAdaptation.DIRECT)
    public abstract Optional<byte[]> fileKey();

    /**
     * Creates the appropriate upload stream variant for the supplied
     * provider.
     *
     * <p>When the provider's media path advertises a key name a
     * {@link Ciphertext} stream is returned that encrypts the content
     * on the fly; otherwise a {@link Plaintext} stream is returned that
     * passes the content through while still computing the plaintext
     * SHA-256 hash.
     *
     * @implNote WAWebCryptoEncryptMedia.encryptMedia: dispatches to the
     * encryption path when the media type advertises a key derivation
     * info string.
     * @param provider    the media provider describing the media type
     * @param inputStream the raw plaintext input stream
     * @return the appropriate upload stream
     * @throws WhatsAppMediaException if cipher initialization fails
     */
    @WhatsAppWebExport(moduleName = "WAWebCryptoEncryptMedia", exports = "default",
            adaptation = WhatsAppAdaptation.DIRECT)
    static MediaUploadInputStream of(MediaProvider provider, InputStream inputStream) throws WhatsAppMediaException {
        // WAWebCryptoEncryptMedia.default
        // Selects the encrypted or plaintext variant based on whether the
        // media type carries an HKDF info string
        var keyName = provider.mediaPath()
                .keyName();
        if (keyName.isPresent()) {
            return new Ciphertext(inputStream, keyName.get());
        } else {
            return new Plaintext(inputStream);
        }
    }

    /**
     * An encrypted media upload stream that performs AES-CBC encryption
     * combined with HMAC-SHA256 authentication.
     *
     * <p>The encryption pipeline:
     * <ol>
     *   <li>Generates a random 32-byte media key.</li>
     *   <li>Derives the IV (16 bytes), cipher key (32 bytes), and HMAC
     *       key (32 bytes) via HKDF-SHA256.</li>
     *   <li>Encrypts the plaintext with AES-CBC using the derived IV and
     *       cipher key.</li>
     *   <li>Computes HMAC-SHA256 over {@code IV || ciphertext} and
     *       truncates the tag to 10 bytes.</li>
     *   <li>Emits {@code ciphertext || HMAC[0:10]} to the caller.</li>
     *   <li>Records the SHA-256 hashes of both the plaintext and the
     *       encrypted output for the outgoing protobuf.</li>
     * </ol>
     *
     * @implNote WAMediaCrypto.encryptAndHmac combined with
     * WAWebCryptoEncryptMedia.encryptMedia.
     */
    @WhatsAppWebModule(moduleName = "WAMediaCrypto")
    @WhatsAppWebModule(moduleName = "WAWebCryptoEncryptMedia")
    private static final class Ciphertext extends MediaUploadInputStream {
        /**
         * Accumulates the SHA-256 digest of the plaintext as bytes are
         * read from the underlying stream. The final digest value is
         * written back through {@link #fileSha256()}.
         *
         * @implNote WAWebCryptoEncryptMedia: computes fileSha256 alongside
         * the encryption.
         */
        @WhatsAppWebExport(moduleName = "WAWebCryptoEncryptMedia", exports = "default",
                adaptation = WhatsAppAdaptation.DIRECT)
        private final MessageDigest plaintextDigest;

        /**
         * Accumulates the SHA-256 digest of the encrypted output
         * ({@code ciphertext || HMAC[0:10]}). Produces the value returned
         * by {@link #fileEncSha256()}.
         *
         * @implNote WAWebCryptoEncryptMedia: returns {@code hash} computed
         * by {@code WAMediaCalculateFilehash.calculateFilehash(ciphertext + hmac[0:10])}.
         */
        @WhatsAppWebExport(moduleName = "WAWebCryptoEncryptMedia", exports = "default",
                adaptation = WhatsAppAdaptation.DIRECT)
        private final MessageDigest ciphertextDigest;

        /**
         * The HMAC-SHA256 instance computing the authentication tag over
         * {@code IV || ciphertext}. Only the first 10 bytes of the final
         * tag are written to the output stream.
         *
         * @implNote WAMediaCrypto.encryptAndHmac:
         * {@code WACryptoHmac.sign(encodeKeySha256(hmacKey), ivCiphertext)}.
         */
        @WhatsAppWebExport(moduleName = "WAMediaCrypto", exports = "encryptAndHmac",
                adaptation = WhatsAppAdaptation.DIRECT)
        private final Mac ciphertextMac;

        /**
         * The AES-CBC cipher in encrypt mode.
         *
         * @implNote WAMediaCrypto.encryptAndHmac:
         * {@code WACryptoAesCbc.AesCbcStream(l, "encrypt", cipherKey, iv)}.
         */
        @WhatsAppWebExport(moduleName = "WAMediaCrypto", exports = "encryptAndHmac",
                adaptation = WhatsAppAdaptation.DIRECT)
        private final Cipher cipher;

        /**
         * Scratch buffer for staging plaintext chunks read from the
         * underlying stream before passing them to the cipher.
         *
         * @implNote WAMediaCrypto.encryptAndHmac: WA Web uses 64 KB
         * chunks; Cobalt uses {@value #BUFFER_LENGTH}-byte chunks to
         * keep the resident working set small.
         */
        @WhatsAppWebExport(moduleName = "WAMediaCrypto", exports = "encryptAndHmac",
                adaptation = WhatsAppAdaptation.ADAPTED)
        private final byte[] plaintextBuffer;

        /**
         * Scratch buffer for holding the cipher output after encrypting
         * a plaintext chunk. Sized to accommodate one extra AES block
         * for PKCS5 padding.
         *
         * @implNote WAMediaCrypto.encryptAndHmac: equivalent of the
         * cipher output staging buffer between {@code AesCbcStream.append}
         * calls.
         */
        @WhatsAppWebExport(moduleName = "WAMediaCrypto", exports = "encryptAndHmac",
                adaptation = WhatsAppAdaptation.ADAPTED)
        private final byte[] ciphertextBuffer;

        /**
         * The output buffer exposed to callers via {@link #read()} and
         * {@link #read(byte[], int, int)}. Holds the bytes destined for
         * the HTTP request body.
         *
         * @implNote WAMediaCrypto.encryptAndHmac: corresponds to the
         * {@code Binary} buffer that accumulates
         * {@code iv + ciphertext + hmac}; Cobalt omits the IV because it
         * is transmitted separately.
         */
        @WhatsAppWebExport(moduleName = "WAMediaCrypto", exports = "encryptAndHmac",
                adaptation = WhatsAppAdaptation.ADAPTED)
        private final byte[] outputBuffer;

        /**
         * The randomly generated 32-byte media key used as the HKDF
         * secret for deriving the IV, cipher key, and HMAC key.
         *
         * @implNote WAWebCryptoEncryptMedia.encryptMedia: generates the
         * random mediaKey and persists it on the outgoing protobuf.
         */
        @WhatsAppWebExport(moduleName = "WAWebCryptoEncryptMedia", exports = "default",
                adaptation = WhatsAppAdaptation.DIRECT)
        private final byte[] mediaKey;

        /**
         * The finalised SHA-256 digest of the plaintext, populated when
         * the stream is exhausted.
         *
         * @implNote WAWebCryptoEncryptMedia: the plaintext hash returned
         * alongside the encrypted payload.
         */
        @WhatsAppWebExport(moduleName = "WAWebCryptoEncryptMedia", exports = "default",
                adaptation = WhatsAppAdaptation.DIRECT)
        private byte[] plaintextHash;

        /**
         * The finalised SHA-256 digest of
         * {@code ciphertext || HMAC[0:10]}, populated when the stream
         * is exhausted.
         *
         * @implNote WAWebCryptoEncryptMedia: the encFilehash returned by
         * {@code encryptMedia}.
         */
        @WhatsAppWebExport(moduleName = "WAWebCryptoEncryptMedia", exports = "default",
                adaptation = WhatsAppAdaptation.DIRECT)
        private byte[] ciphertextHash;

        /**
         * The running count of plaintext bytes read from the underlying
         * stream. Used to populate the outgoing protobuf's fileLength.
         *
         * @implNote WAWebCryptoEncryptMedia: tracked for the
         * {@code fileLength} protobuf field.
         */
        @WhatsAppWebExport(moduleName = "WAWebCryptoEncryptMedia", exports = "default",
                adaptation = WhatsAppAdaptation.DIRECT)
        private long plaintextLength;

        /**
         * Flag indicating that the stream has been fully processed and
         * the final HMAC trailer plus hashes have been written.
         */
        private boolean finalized;

        /**
         * The current read cursor within {@link #outputBuffer}.
         */
        private int outputPosition;

        /**
         * The number of valid bytes currently available in
         * {@link #outputBuffer}.
         */
        private int outputLimit;

        /**
         * Constructs a new encrypted upload stream for the given media
         * type.
         *
         * <p>Generates a random 32-byte media key, HKDF-derives the IV,
         * cipher key, and HMAC key, initializes the AES-CBC cipher in
         * encrypt mode, and primes the HMAC with the IV bytes so that
         * the authentication tag is computed over
         * {@code IV || ciphertext}.
         *
         * @implNote WAMediaCrypto.encryptAndHmac: calls
         * {@code computeMediaKeys}, initialises {@code AesCbcStream},
         * writes the IV into the binary buffer, and prepares the HMAC.
         * @param rawInputStream the plaintext input stream to encrypt
         * @param keyName        the HKDF info string for the media type
         * @throws WhatsAppMediaException if key derivation or cipher initialization fails
         */
        @WhatsAppWebExport(moduleName = "WAMediaCrypto", exports = "encryptAndHmac",
                adaptation = WhatsAppAdaptation.ADAPTED)
        @WhatsAppWebExport(moduleName = "WAWebCryptoEncryptMedia", exports = "default",
                adaptation = WhatsAppAdaptation.ADAPTED)
        public Ciphertext(InputStream rawInputStream, String keyName) throws WhatsAppMediaException {
            super(rawInputStream);

            this.plaintextDigest = newHash();
            this.ciphertextDigest = newHash();

            // WAWebCryptoEncryptMedia.default
            // Generates the random 32-byte media key that seeds the HKDF
            // derivation for the IV, cipher key, and HMAC key
            this.mediaKey = DataUtils.randomByteArray(32);

            // WAMediaCrypto.computeMediaKeys
            // Expands the media key into 112 bytes and slices the IV
            // (0..16), cipher key (16..48), and HMAC key (48..80)
            var expanded = deriveMediaKeyData(mediaKey, keyName);
            var iv = new IvParameterSpec(expanded, 0, IV_LENGTH);
            var cipherKey = new SecretKeySpec(expanded, IV_LENGTH, KEY_LENGTH, "AES");
            var macKey = new SecretKeySpec(expanded, IV_LENGTH + KEY_LENGTH, KEY_LENGTH, "HmacSHA256");

            // WAMediaCrypto.encryptAndHmac
            // Initialises the AES-CBC cipher in encrypt mode and the
            // HMAC-SHA256 instance used to authenticate the output
            this.cipher = newCipher(Cipher.ENCRYPT_MODE, cipherKey, iv);
            this.ciphertextMac = newMac(macKey);

            // WAMediaCrypto.encryptAndHmac
            // Feeds the IV bytes into the HMAC so that the authentication
            // tag is computed over iv + ciphertext
            ciphertextMac.update(expanded, 0, IV_LENGTH);

            this.plaintextBuffer = new byte[BUFFER_LENGTH];

            // WAMediaCrypto.CBC_BLOCK_SIZE
            // Reserves one extra AES block so doFinal's padded output
            // fits in the ciphertext buffer without reallocation. For
            // streaming cipher.update the output never exceeds BUFFER_LENGTH
            // because BUFFER_LENGTH is block-aligned (8192 = 512 * 16) so
            // the cipher's internal carry stays at zero across calls.
            this.ciphertextBuffer = new byte[BUFFER_LENGTH + CBC_BLOCK_SIZE];
            this.outputBuffer = new byte[BUFFER_LENGTH];
            this.plaintextLength = 0;
        }

        /**
         * Reads a single byte of the encrypted payload.
         *
         * @implNote ADAPTED: WAMediaCrypto.encryptAndHmac returns the full
         * {@code ivCiphertextHmac} buffer at once; Cobalt streams the
         * same output byte by byte.
         * @return the next byte of encrypted data, or {@code -1} if the stream
         *         is exhausted
         * @throws IOException if an I/O or encryption error occurs
         */
        @WhatsAppWebExport(moduleName = "WAMediaCrypto", exports = "encryptAndHmac",
                adaptation = WhatsAppAdaptation.ADAPTED)
        @Override
        public int read() throws IOException {
            ensureDataAvailable();
            if (outputPosition >= outputLimit) {
                return -1;
            }

            return outputBuffer[outputPosition++] & 0xFF;
        }

        /**
         * Reads up to {@code len} bytes of the encrypted payload into
         * the supplied array.
         *
         * @implNote ADAPTED: WAMediaCrypto.encryptAndHmac streaming
         * adaptation.
         * @param b   the destination buffer
         * @param off the start offset in the destination buffer
         * @param len the maximum number of bytes to read
         * @return the number of bytes actually read, or {@code -1} if the
         *         stream is exhausted
         * @throws IOException if an I/O or encryption error occurs
         */
        @WhatsAppWebExport(moduleName = "WAMediaCrypto", exports = "encryptAndHmac",
                adaptation = WhatsAppAdaptation.ADAPTED)
        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            ensureDataAvailable();
            if (outputPosition >= outputLimit) {
                return -1;
            }

            var available = outputLimit - outputPosition;
            var toRead = Math.min(len, available);
            System.arraycopy(outputBuffer, outputPosition, b, off, toRead);
            outputPosition += toRead;
            return toRead;
        }

        /**
         * Ensures that {@link #outputBuffer} contains data to return to
         * the caller.
         *
         * <p>Reads plaintext chunks from the underlying stream, encrypts
         * each chunk with AES-CBC, updates the HMAC and digest
         * accumulators, and fills the output buffer with the resulting
         * ciphertext. On end-of-stream the method finalises the cipher,
         * appends the truncated 10-byte HMAC, and computes the final
         * hashes so that {@link #fileSha256()} and {@link #fileEncSha256()}
         * can return.
         *
         * @implNote WAMediaCrypto.encryptAndHmac: the main encryption
         * for-loop invokes {@code AesCbcStream.append} on each chunk
         * then {@code AesCbcStream.finalize}, computes
         * {@code sign(encodeKeySha256(hmacKey), ivCiphertext)}, writes
         * the first 10 HMAC bytes, and derives
         * {@code SHA-256(ciphertext + hmac[0:10])}.
         * @throws IOException if an I/O or encryption error occurs
         */
        @WhatsAppWebExport(moduleName = "WAMediaCrypto", exports = "encryptAndHmac",
                adaptation = WhatsAppAdaptation.ADAPTED)
        private void ensureDataAvailable() throws IOException {
            try {
                while (outputPosition >= outputLimit && !finalized) {
                    this.outputPosition = 0;
                    this.outputLimit = 0;

                    var plaintextRead = rawInputStream.read(plaintextBuffer, 0, plaintextBuffer.length);
                    if (plaintextRead == -1) {
                        rawInputStream.close();

                        // WAMediaCrypto.encryptAndHmac
                        // Finalises the cipher (s.finalize) and consumes the
                        // last padded block through the normal chunk path
                        var finalCiphertextLen = cipher.doFinal(ciphertextBuffer, 0);
                        processChunk(finalCiphertextLen);

                        // WAMediaCrypto.encryptAndHmac
                        // Computes the full 32-byte HMAC (sign(f, _)) and
                        // extends the encFilehash digest over the 10 bytes
                        // that will be written to the wire
                        var mac = ciphertextMac.doFinal();
                        ciphertextDigest.update(mac, 0, MAC_LENGTH);

                        // WAMediaCrypto.encryptAndHmac
                        // Appends the 10-byte truncated HMAC to the output
                        // (l.writeByteArray(new Uint8Array(g, 0, v)))
                        var macSpace = outputBuffer.length - outputLimit;
                        var macToCopy = Math.min(MAC_LENGTH, macSpace);
                        System.arraycopy(mac, 0, outputBuffer, outputLimit, macToCopy);
                        outputLimit += macToCopy;

                        // WAWebCryptoEncryptMedia.default
                        // Captures the final plaintext and encrypted hashes
                        // so the accessor methods can return them
                        plaintextHash = plaintextDigest.digest();
                        ciphertextHash = ciphertextDigest.digest();

                        finalized = true;
                        break;
                    }

                    // WAWebCryptoEncryptMedia.default
                    // Extends the plaintext digest and the byte counter
                    // with the newly read chunk
                    plaintextDigest.update(plaintextBuffer, 0, plaintextRead);
                    plaintextLength += plaintextRead;

                    // WAMediaCrypto.encryptAndHmac
                    // Encrypts the chunk via cipher.update (s.append) and
                    // routes the output through processChunk
                    var ciphertextLen = cipher.update(plaintextBuffer, 0, plaintextRead, ciphertextBuffer, 0);
                    processChunk(ciphertextLen);
                }
            } catch (GeneralSecurityException exception) {
                throw new IOException("Cannot encrypt data", exception);
            }
        }

        /**
         * Processes a chunk of ciphertext that was produced by the
         * cipher: extends the encFilehash digest, extends the HMAC
         * accumulator, and copies the ciphertext into the caller-facing
         * {@link #outputBuffer}.
         *
         * @implNote WAMediaCrypto.encryptAndHmac: mirrors the body of the
         * inner for-loop where ciphertext is appended to the {@code Binary}
         * buffer and reused for SHA-256 and HMAC computation.
         * @param length the number of valid ciphertext bytes in the buffer
         */
        @WhatsAppWebExport(moduleName = "WAMediaCrypto", exports = "encryptAndHmac",
                adaptation = WhatsAppAdaptation.ADAPTED)
        private void processChunk(int length) {
            if (length <= 0) {
                return;
            }

            // WAMediaCrypto.encryptAndHmac
            // Extends SHA-256(ciphertext) and HMAC(iv + ciphertext) with
            // the newly produced ciphertext bytes
            ciphertextDigest.update(ciphertextBuffer, 0, length);
            ciphertextMac.update(ciphertextBuffer, 0, length);

            // WAMediaCrypto.encryptAndHmac
            // Copies the ciphertext into the caller-facing output buffer
            var toCopy = Math.min(length, outputBuffer.length);
            System.arraycopy(ciphertextBuffer, 0, outputBuffer, 0, toCopy);
            outputLimit = toCopy;
        }

        /**
         * Returns the total number of plaintext bytes read from the
         * underlying stream.
         *
         * @implNote WAWebCryptoEncryptMedia: the plaintext
         * {@code byteLength} recorded on the outgoing protobuf.
         * @return the plaintext file length in bytes
         */
        @WhatsAppWebExport(moduleName = "WAWebCryptoEncryptMedia", exports = "default",
                adaptation = WhatsAppAdaptation.DIRECT)
        @Override
        public long fileLength() {
            return plaintextLength;
        }

        /**
         * Returns the finalised SHA-256 digest of the plaintext.
         *
         * @implNote WAMediaCrypto.hmacAndDecrypt: equivalent to the
         * {@code plaintextHash} value computed by
         * {@code WAHashUtils.toPlaintextHash(SHA-256(plaintext))}.
         * @return the plaintext SHA-256 hash
         * @throws IllegalStateException if the stream has not been fully consumed
         */
        @WhatsAppWebExport(moduleName = "WAWebCryptoEncryptMedia", exports = "default",
                adaptation = WhatsAppAdaptation.DIRECT)
        @Override
        public byte[] fileSha256() {
            if (plaintextHash == null) {
                throw new IllegalStateException("Cannot get file SHA-256 hash before the file has been fully read");
            }

            return plaintextHash;
        }

        /**
         * Returns the finalised SHA-256 digest of the encrypted payload
         * ({@code ciphertext || HMAC[0:10]}).
         *
         * @implNote WAWebCryptoEncryptMedia: returns the encFilehash value
         * computed via
         * {@code WAMediaCalculateFilehash.calculateFilehash(ciphertext + hmac[0:10])}.
         * @return an {@link Optional} containing the encrypted SHA-256 hash
         * @throws IllegalStateException if the stream has not been fully consumed
         */
        @WhatsAppWebExport(moduleName = "WAWebCryptoEncryptMedia", exports = "default",
                adaptation = WhatsAppAdaptation.DIRECT)
        @Override
        public Optional<byte[]> fileEncSha256() {
            if (ciphertextHash == null) {
                throw new IllegalStateException("Cannot get file encrypted SHA-256 hash before the file has been fully read");
            }

            return Optional.of(ciphertextHash);
        }

        /**
         * Returns the randomly generated 32-byte media key.
         *
         * @implNote WAWebCryptoEncryptMedia.encryptMedia: the random
         * mediaKey generated for this upload and persisted in the
         * outgoing message protobuf.
         * @return an {@link Optional} containing the media key
         */
        @WhatsAppWebExport(moduleName = "WAWebCryptoEncryptMedia", exports = "default",
                adaptation = WhatsAppAdaptation.DIRECT)
        @Override
        public Optional<byte[]> fileKey() {
            return Optional.of(mediaKey);
        }
    }

    /**
     * A plaintext upload stream that passes the content through
     * unchanged while computing the SHA-256 digest needed for the
     * outgoing message protobuf.
     *
     * <p>Used for media types that do not participate in end-to-end
     * encryption, such as newsletter media, profile pictures, and
     * business cover photos.
     *
     * @implNote WAWebCryptoEncryptMedia: the no-encryption branch used
     * for media types without a key derivation info string.
     */
    @WhatsAppWebModule(moduleName = "WAWebCryptoEncryptMedia")
    private static final class Plaintext extends MediaUploadInputStream {
        /**
         * Accumulates the SHA-256 digest of the plaintext content. The
         * value is consumed by {@link #fileSha256()} once the stream is
         * exhausted.
         *
         * @implNote WAWebCryptoEncryptMedia: the plaintext hash recorded
         * on the outgoing protobuf for integrity verification.
         */
        @WhatsAppWebExport(moduleName = "WAWebCryptoEncryptMedia", exports = "default",
                adaptation = WhatsAppAdaptation.DIRECT)
        private final MessageDigest plaintextDigest;

        /**
         * The running count of plaintext bytes read.
         *
         * @implNote WAWebCryptoEncryptMedia: byte count recorded on the
         * outgoing protobuf.
         */
        @WhatsAppWebExport(moduleName = "WAWebCryptoEncryptMedia", exports = "default",
                adaptation = WhatsAppAdaptation.DIRECT)
        private long plaintextLength;

        /**
         * The finalised SHA-256 digest of the plaintext, populated once
         * the stream is exhausted.
         *
         * @implNote WAWebCryptoEncryptMedia: fileSha256 result.
         */
        @WhatsAppWebExport(moduleName = "WAWebCryptoEncryptMedia", exports = "default",
                adaptation = WhatsAppAdaptation.DIRECT)
        private byte[] plaintextHash;

        /**
         * Flag indicating that the stream has been fully consumed and
         * that the SHA-256 digest has been computed.
         */
        private boolean finalized;

        /**
         * Constructs a new plaintext upload stream.
         *
         * @implNote WAWebCryptoEncryptMedia: the no-encryption path used
         * for media types without a key derivation info string.
         * @param rawInputStream the underlying input stream to pass through
         */
        @WhatsAppWebExport(moduleName = "WAWebCryptoEncryptMedia", exports = "default",
                adaptation = WhatsAppAdaptation.DIRECT)
        public Plaintext(InputStream rawInputStream) {
            super(rawInputStream);
            try {
                this.plaintextDigest = MessageDigest.getInstance("SHA-256");
                this.plaintextLength = 0;
            } catch (GeneralSecurityException exception) {
                throw new InternalError("Cannot initialize stream", exception);
            }
        }

        /**
         * Reads a single byte from the underlying stream while extending
         * the plaintext digest.
         *
         * @implNote ADAPTED: WAWebCryptoEncryptMedia: streaming
         * adaptation of the hash-while-reading loop.
         * @return the next byte of data, or {@code -1} if end of stream
         * @throws IOException if an I/O error occurs
         */
        @WhatsAppWebExport(moduleName = "WAWebCryptoEncryptMedia", exports = "default",
                adaptation = WhatsAppAdaptation.ADAPTED)
        @Override
        public int read() throws IOException {
            var ch = rawInputStream.read();
            if (ch != -1) {
                plaintextDigest.update((byte) ch);
                plaintextLength++;
            } else if (!finalized) {
                finalized = true;
                plaintextHash = plaintextDigest.digest();
            }
            return ch;
        }

        /**
         * Reads up to {@code len} bytes from the underlying stream while
         * extending the plaintext digest.
         *
         * @implNote ADAPTED: WAWebCryptoEncryptMedia: streaming
         * adaptation of the hash-while-reading loop.
         * @param b   the destination buffer
         * @param off the start offset in the destination buffer
         * @param len the maximum number of bytes to read
         * @return the number of bytes read, or {@code -1} if end of stream
         * @throws IOException if an I/O error occurs
         */
        @WhatsAppWebExport(moduleName = "WAWebCryptoEncryptMedia", exports = "default",
                adaptation = WhatsAppAdaptation.ADAPTED)
        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            var result = rawInputStream.read(b, off, len);
            if (result != -1) {
                plaintextDigest.update(b, off, result);
                plaintextLength += result;
            } else if (!finalized) {
                finalized = true;
                plaintextHash = plaintextDigest.digest();
            }
            return result;
        }

        /**
         * Returns the total number of plaintext bytes read from the
         * underlying stream.
         *
         * @implNote WAWebCryptoEncryptMedia: byte count recorded on the
         * outgoing protobuf.
         * @return the plaintext file length in bytes
         */
        @WhatsAppWebExport(moduleName = "WAWebCryptoEncryptMedia", exports = "default",
                adaptation = WhatsAppAdaptation.DIRECT)
        @Override
        public long fileLength() {
            return plaintextLength;
        }

        /**
         * Returns the finalised SHA-256 digest of the plaintext.
         *
         * @implNote WAWebCryptoEncryptMedia: plaintext hash written to
         * the outgoing protobuf.
         * @return the plaintext SHA-256 hash
         * @throws IllegalStateException if the stream has not been fully consumed
         */
        @WhatsAppWebExport(moduleName = "WAWebCryptoEncryptMedia", exports = "default",
                adaptation = WhatsAppAdaptation.DIRECT)
        @Override
        public byte[] fileSha256() {
            if (plaintextHash == null) {
                throw new IllegalStateException("Cannot get file SHA-256 hash before the file has been fully read");
            }

            return plaintextHash;
        }

        /**
         * Returns an empty optional because plaintext uploads never
         * produce an encrypted hash.
         *
         * @implNote WAWebCryptoEncryptMedia: no encryption path implies no
         * encFilehash.
         * @return an empty {@link Optional}
         */
        @WhatsAppWebExport(moduleName = "WAWebCryptoEncryptMedia", exports = "default",
                adaptation = WhatsAppAdaptation.DIRECT)
        @Override
        public Optional<byte[]> fileEncSha256() {
            return Optional.empty();
        }

        /**
         * Returns an empty optional because plaintext uploads never
         * generate a media key.
         *
         * @implNote WAWebCryptoEncryptMedia: no encryption path implies no
         * mediaKey.
         * @return an empty {@link Optional}
         */
        @WhatsAppWebExport(moduleName = "WAWebCryptoEncryptMedia", exports = "default",
                adaptation = WhatsAppAdaptation.DIRECT)
        @Override
        public Optional<byte[]> fileKey() {
            return Optional.empty();
        }
    }
}
