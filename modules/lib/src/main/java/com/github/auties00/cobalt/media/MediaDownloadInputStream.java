package com.github.auties00.cobalt.media;

import com.github.auties00.cobalt.exception.WhatsAppMediaException;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebExport;
import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;
import com.github.auties00.cobalt.meta.model.WhatsAppAdaptation;
import com.github.auties00.cobalt.model.media.MediaProvider;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.io.InputStream;
import java.net.http.HttpClient;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Objects;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

/**
 * Streams, decrypts and verifies a media payload downloaded from the
 * WhatsApp CDN.
 *
 * <p>The payload delivered by the CDN has the shape
 * {@code ciphertext || HMAC[0:10]}, where the ciphertext is AES-CBC
 * encrypted and the trailing 10 bytes are the truncated HMAC-SHA256
 * computed over {@code IV || ciphertext}. The IV itself is not part of
 * the payload; it is derived from the media key via HKDF.
 *
 * <p>The stream drives an internal state machine that progresses through
 * four stages:
 * <ol>
 *   <li>{@link State#READ_DATA}: reads and decrypts ciphertext bytes,
 *       updating the HMAC and digest accumulators.</li>
 *   <li>{@link State#READ_MAC}: reads the trailing 10-byte HMAC.</li>
 *   <li>{@link State#VALIDATE_ALL}: verifies the HMAC, ciphertext hash,
 *       and plaintext hash.</li>
 *   <li>{@link State#DONE}: all data consumed and validated.</li>
 * </ol>
 *
 * <p>For media types marked as inflatable (such as app-state blobs and
 * history sync payloads) the decrypted content is additionally
 * decompressed via zlib inflate before being exposed to callers.
 *
 * @implNote WAMediaCrypto.hmacAndDecrypt: batch verify-and-decrypt
 * procedure, adapted here to an incremental streaming state machine.
 * WAWebCryptoDecryptMedia: the WA Web wrapper that concatenates the IV
 * with the ciphertext before HMACing and performs the optional
 * encFilehash verification.
 */
@WhatsAppWebModule(moduleName = "WAMediaCrypto")
@WhatsAppWebModule(moduleName = "WAWebCryptoDecryptMedia")
final class MediaDownloadInputStream extends MediaInputStream {
    /**
     * The HTTP client backing the download connection. Owned by this
     * stream and closed together with it so that the underlying socket
     * is released once the caller finishes consuming the payload.
     */
    private final HttpClient client;

    /**
     * The zlib inflater used to decompress inflatable media types
     * ({@code md-app-state} and {@code md-msg-hist}), or {@code null}
     * when no decompression is needed.
     *
     * @implNote ADAPTED: WAWebMmsMediaTypes: the inflatable flag on
     * media types that indicate zlib-compressed payloads.
     */
    @WhatsAppWebExport(moduleName = "WAWebMmsMediaTypes", exports = "MEDIA_TYPES",
            adaptation = WhatsAppAdaptation.ADAPTED)
    private final Inflater inflater;

    /**
     * The primary I/O buffer used for staging raw ciphertext reads and
     * the decrypted plaintext output.
     *
     * @implNote WAMediaCrypto.hmacAndDecrypt: corresponds to the
     * ciphertext and plaintext working buffers.
     */
    @WhatsAppWebExport(moduleName = "WAMediaCrypto", exports = "hmacAndDecrypt",
            adaptation = WhatsAppAdaptation.ADAPTED)
    private final byte[] buffer;

    /**
     * The current read cursor within {@link #buffer}, advanced every
     * time bytes are handed back to the caller.
     */
    private int bufferOffset;

    /**
     * The number of valid decrypted bytes available in {@link #buffer}.
     */
    private int bufferLimit;

    /**
     * The secondary buffer that holds decompressed output for inflatable
     * media types, or {@code null} when the media type is not
     * inflatable.
     *
     * @implNote ADAPTED: the zlib decompression buffer has no direct WA Web
     * counterpart because WA Web decompresses in a worker thread.
     */
    private final byte[] inflatedBuffer;

    /**
     * The current read cursor within {@link #inflatedBuffer}.
     */
    private int inflatedOffset;

    /**
     * The number of valid decompressed bytes available in
     * {@link #inflatedBuffer}.
     */
    private int inflatedLimit;

    /**
     * The buffer accumulating the trailing HMAC bytes read from the
     * stream, or {@code null} for unencrypted media.
     *
     * @implNote WAMediaCrypto.hmacAndDecrypt: corresponds to the mac
     * parameter {@code a} which is the 10-byte HMAC sliced from the
     * downloaded payload.
     */
    @WhatsAppWebExport(moduleName = "WAMediaCrypto", exports = "hmacAndDecrypt",
            adaptation = WhatsAppAdaptation.DIRECT)
    private final byte[] macBuffer;

    /**
     * The number of HMAC bytes accumulated into {@link #macBuffer} so
     * far; reaches {@link #MAC_LENGTH} when the trailer has been fully
     * read.
     */
    private int macBufferOffset;

    /**
     * The SHA-256 digest that accumulates the decrypted plaintext, or
     * {@code null} when no expected plaintext hash is provided.
     *
     * @implNote WAMediaCrypto.hmacAndDecrypt: plaintextHash is compared
     * against the expected value after decryption.
     */
    @WhatsAppWebExport(moduleName = "WAMediaCrypto", exports = "hmacAndDecrypt",
            adaptation = WhatsAppAdaptation.DIRECT)
    private final MessageDigest plaintextDigest;

    /**
     * The expected SHA-256 hash of the plaintext used for integrity
     * verification, or {@code null} when absent.
     *
     * @implNote WAMediaCrypto.hmacAndDecrypt: the {@code toPlaintextHash}
     * comparand.
     */
    @WhatsAppWebExport(moduleName = "WAMediaCrypto", exports = "hmacAndDecrypt",
            adaptation = WhatsAppAdaptation.DIRECT)
    private final byte[] expectedPlaintextSha256;

    /**
     * The SHA-256 digest that accumulates the encrypted payload
     * ({@code ciphertext || HMAC[0:10]}), or {@code null} when no
     * expected ciphertext hash was provided.
     *
     * @implNote WAWebCryptoDecryptMedia: optional encFilehash
     * verification over the wire payload.
     */
    @WhatsAppWebExport(moduleName = "WAWebCryptoDecryptMedia", exports = "default",
            adaptation = WhatsAppAdaptation.DIRECT)
    private final MessageDigest ciphertextDigest;

    /**
     * The expected SHA-256 hash of the encrypted payload used for
     * integrity verification, or {@code null} when absent.
     *
     * @implNote WAWebCryptoDecryptMedia: the {@code encFilehash}
     * comparand supplied in the message protobuf.
     */
    @WhatsAppWebExport(moduleName = "WAWebCryptoDecryptMedia", exports = "default",
            adaptation = WhatsAppAdaptation.DIRECT)
    private final byte[] expectedCiphertextSha256;

    /**
     * The AES-CBC cipher used for decryption, or {@code null} when the
     * media type does not use end-to-end encryption.
     *
     * @implNote WAMediaCrypto.hmacAndDecrypt: invokes
     * {@code WACryptoAesCbc.aesCbcDecrypt(cipherKey, iv, ciphertext)}.
     */
    @WhatsAppWebExport(moduleName = "WAMediaCrypto", exports = "hmacAndDecrypt",
            adaptation = WhatsAppAdaptation.DIRECT)
    private final Cipher cipher;

    /**
     * The HMAC-SHA256 instance used to verify the authenticity of the
     * downloaded payload, or {@code null} for unencrypted media.
     *
     * @implNote WAMediaCrypto.hmacAndDecrypt: invokes
     * {@code WACryptoHmac.hmacSha256(hmacKey, iv + ciphertext)}.
     */
    @WhatsAppWebExport(moduleName = "WAMediaCrypto", exports = "hmacAndDecrypt",
            adaptation = WhatsAppAdaptation.DIRECT)
    private final Mac mac;

    /**
     * The number of ciphertext bytes remaining before the HMAC trailer
     * begins. Decremented as data is consumed from the raw stream.
     *
     * @implNote WAWebCryptoDecryptMedia:
     * {@code ciphertext = ciphertextHmac.subarray(0, -10)}.
     */
    @WhatsAppWebExport(moduleName = "WAWebCryptoDecryptMedia", exports = "default",
            adaptation = WhatsAppAdaptation.DIRECT)
    private long remainingText;

    /**
     * The current state of the decryption and verification state
     * machine.
     *
     * @implNote ADAPTED: WAMediaCrypto.hmacAndDecrypt is a batch
     * operation; Cobalt drives the same steps incrementally via an
     * explicit state machine so the output can be streamed.
     */
    @WhatsAppWebExport(moduleName = "WAMediaCrypto", exports = "hmacAndDecrypt",
            adaptation = WhatsAppAdaptation.ADAPTED)
    private State state;

    /**
     * Constructs a new media download input stream that transparently
     * decrypts and verifies the payload from the given raw stream.
     *
     * <p>If the provider carries a media key and key name, HKDF-derives
     * the IV, cipher key, and HMAC key and primes the HMAC with the IV
     * bytes so that the HMAC is taken over {@code IV || ciphertext}. The
     * payload is expected to carry {@code payloadLength - MAC_LENGTH}
     * bytes of ciphertext followed by the 10-byte HMAC trailer.
     *
     * <p>When the provider has no media key the stream passes through
     * the raw bytes without decryption, still optionally verifying the
     * plaintext SHA-256 hash when one was supplied.
     *
     * @implNote WAMediaCrypto.hmacAndDecrypt: derives keys via
     * {@code computeMediaKeys}, splits ciphertext from the HMAC trailer,
     * verifies the HMAC, performs AES-CBC decryption, and verifies the
     * plaintext hash. WAWebCryptoDecryptMedia:
     * {@code concat(iv, ciphertextHmac.subarray(0, -10))} is the HMAC
     * input, and the 10-byte truncation is enforced via
     * {@code hmacSha256(macKey, data, 10)}.
     * @param client         the HTTP client managing the download connection
     * @param rawInputStream the raw input stream from the CDN
     * @param payloadLength  the total payload length in bytes (ciphertext + HMAC)
     * @param provider       the media provider with decryption metadata
     * @throws WhatsAppMediaException if key derivation or cipher initialization fails
     */
    @WhatsAppWebExport(moduleName = "WAMediaCrypto", exports = "hmacAndDecrypt",
            adaptation = WhatsAppAdaptation.ADAPTED)
    @WhatsAppWebExport(moduleName = "WAWebCryptoDecryptMedia", exports = "default",
            adaptation = WhatsAppAdaptation.ADAPTED)
    MediaDownloadInputStream(HttpClient client, InputStream rawInputStream, long payloadLength, MediaProvider provider) throws WhatsAppMediaException {
        super(rawInputStream);
        Objects.requireNonNull(client, "client cannot be null");
        Objects.requireNonNull(rawInputStream, "rawInputStream must not be null");
        Objects.requireNonNull(provider, "provider must not be null");

        this.client = client;

        // ADAPTED: WAWebMmsMediaTypes.MEDIA_TYPES
        // Allocates an inflater only for inflatable media types such as
        // md-app-state and md-msg-hist
        this.inflater = provider.mediaPath().inflatable() ? new Inflater() : null;

        this.buffer = new byte[BUFFER_LENGTH];
        this.inflatedBuffer = isInflatable() ? new byte[BUFFER_LENGTH] : null;

        // WAMediaCrypto.hmacAndDecrypt
        // Enables plaintextHash verification when the provider carries the
        // expected fileSha256 value
        this.expectedPlaintextSha256 = provider.mediaSha256().orElse(null);
        this.plaintextDigest = expectedPlaintextSha256 != null ? newHash() : null;

        var hasKeyName = provider.mediaPath().keyName().isPresent();
        var hasMediaKey = provider.mediaKey().isPresent();

        if (hasKeyName != hasMediaKey) {
            throw new WhatsAppMediaException.Download("Media key and key name must both be present or both be absent");
        } else if (hasKeyName) {
            // WAWebCryptoDecryptMedia.default
            // Enables encFilehash verification when the expected encrypted
            // SHA-256 is available
            this.expectedCiphertextSha256 = provider.mediaEncryptedSha256().orElse(null);
            this.ciphertextDigest = expectedCiphertextSha256 != null ? newHash() : null;

            var mediaKey = provider.mediaKey()
                    .orElseThrow(() -> new WhatsAppMediaException.Download("Media key must be present"));
            var keyName = provider.mediaPath().keyName()
                    .orElseThrow(() -> new WhatsAppMediaException.Download("Key name must be present"));

            // WAMediaCrypto.computeMediaKeys
            // HKDF-expands the 32-byte media key into 112 bytes and slices
            // it into IV (0..16), cipher key (16..48), and HMAC key (48..80)
            var expanded = deriveMediaKeyData(mediaKey, keyName);
            var iv = new IvParameterSpec(expanded, 0, IV_LENGTH);
            var cipherKey = new SecretKeySpec(expanded, IV_LENGTH, KEY_LENGTH, "AES");
            var macKey = new SecretKeySpec(expanded, IV_LENGTH + KEY_LENGTH, KEY_LENGTH, "HmacSHA256");

            // WAMediaCrypto.hmacAndDecrypt
            // Initializes the AES-CBC decrypt cipher and the HMAC-SHA256
            // instance used to verify the authentication tag
            this.cipher = newCipher(Cipher.DECRYPT_MODE, cipherKey, iv);
            this.mac = newMac(macKey);

            // WAWebCryptoDecryptMedia.default
            // Primes the HMAC with the IV bytes so that the authentication
            // tag is computed over concat(iv, ciphertext)
            this.mac.update(expanded, 0, IV_LENGTH);

            // WAWebCryptoDecryptMedia.default
            // Reserves space for the trailing MAC bytes and records how
            // many ciphertext bytes precede them
            this.remainingText = payloadLength - MAC_LENGTH;
            this.macBuffer = new byte[MAC_LENGTH];
        } else {
            this.expectedCiphertextSha256 = null;
            this.ciphertextDigest = null;
            this.cipher = null;
            this.mac = null;
            this.macBuffer = null;
            this.remainingText = payloadLength;
        }

        this.state = State.READ_DATA;
    }

    /**
     * Reads a single decrypted (and optionally decompressed) byte.
     *
     * @implNote ADAPTED: WAMediaCrypto.hmacAndDecrypt is a batch
     * operation that returns the full plaintext; this method returns one
     * byte at a time by driving the state machine incrementally.
     * @return the next byte of decrypted data, or {@code -1} if the stream
     *         is exhausted and validated
     * @throws WhatsAppMediaException.Download if decryption, decompression, or
     *         validation fails
     */
    @WhatsAppWebExport(moduleName = "WAMediaCrypto", exports = "hmacAndDecrypt",
            adaptation = WhatsAppAdaptation.ADAPTED)
    @Override
    public int read() throws WhatsAppMediaException.Download {
        if (isDone()) {
            return -1;
        } else if (isInflatable()) {
            return inflatedBuffer[inflatedOffset++] & 0xFF;
        } else {
            return buffer[bufferOffset++] & 0xFF;
        }
    }

    /**
     * Reads up to {@code len} decrypted (and optionally decompressed)
     * bytes into the specified array.
     *
     * @implNote ADAPTED: WAMediaCrypto.hmacAndDecrypt is a batch
     * operation; this streaming adaptation drains the internal buffers
     * on demand.
     * @param b   the destination buffer
     * @param off the start offset in the destination buffer
     * @param len the maximum number of bytes to read
     * @return the number of bytes read, or {@code -1} if the stream is
     *         exhausted and validated
     * @throws WhatsAppMediaException.Download if decryption, decompression, or
     *         validation fails
     */
    @WhatsAppWebExport(moduleName = "WAMediaCrypto", exports = "hmacAndDecrypt",
            adaptation = WhatsAppAdaptation.ADAPTED)
    @Override
    public int read(byte[] b, int off, int len) throws WhatsAppMediaException.Download {
        if (isDone()) {
            return -1;
        } else if (isInflatable()) {
            var toRead = Math.min(len, inflatedLimit - inflatedOffset);
            System.arraycopy(inflatedBuffer, inflatedOffset, b, off, toRead);
            inflatedOffset += toRead;
            return toRead;
        } else {
            var toRead = Math.min(len, bufferLimit - bufferOffset);
            System.arraycopy(buffer, bufferOffset, b, off, toRead);
            bufferOffset += toRead;
            return toRead;
        }
    }

    /**
     * Drives the decryption and verification state machine forward until
     * output data becomes available or the stream is fully consumed and
     * validated.
     *
     * <p>The state machine transitions:
     * <ul>
     *   <li>{@link State#READ_DATA}: reads ciphertext, updates the HMAC
     *       and digests, decrypts via AES-CBC, and optionally feeds the
     *       inflater.</li>
     *   <li>{@link State#READ_MAC}: reads the trailing 10-byte HMAC and
     *       extends the ciphertext digest with it so that the digest
     *       matches the on-wire payload.</li>
     *   <li>{@link State#VALIDATE_ALL}: verifies the ciphertext hash, the
     *       HMAC (using constant-time comparison to avoid timing side
     *       channels), and the plaintext hash.</li>
     * </ul>
     *
     * @implNote WAMediaCrypto.hmacAndDecrypt: performs HMAC size
     * validation, computes {@code hmacSha256(hmacKey, iv + ciphertext)},
     * constant-time compares the truncated tag, runs
     * {@code aesCbcDecrypt(cipherKey, iv, ciphertext)}, and finally
     * verifies the plaintext hash. WAWebCryptoDecryptMedia: concatenates
     * the IV with the ciphertext, feeds the 10-byte truncated HMAC into
     * {@code WACryptoPrimitives.verify}, and performs the optional
     * plaintext hash check.
     * @return {@code true} if the stream is fully consumed and validated,
     *         {@code false} if output data is available for reading
     * @throws WhatsAppMediaException.Download if any decryption or validation
     *         step fails
     */
    @WhatsAppWebExport(moduleName = "WAMediaCrypto", exports = "hmacAndDecrypt",
            adaptation = WhatsAppAdaptation.ADAPTED)
    @WhatsAppWebExport(moduleName = "WAWebCryptoDecryptMedia", exports = "default",
            adaptation = WhatsAppAdaptation.ADAPTED)
    private boolean isDone() throws WhatsAppMediaException.Download {
        try {
            var inflatable = isInflatable();
            while ((inflatable ? inflatedOffset >= inflatedLimit : bufferOffset >= bufferLimit) && state != State.DONE) {
                if (inflatable && !inflater.needsInput() && !inflater.finished()) {
                    inflatedOffset = 0;
                    inflatedLimit = inflater.inflate(inflatedBuffer);
                } else {
                    switch (state) {
                        case READ_DATA -> {
                            if (remainingText > 0) {
                                // WAMediaCrypto.hmacAndDecrypt
                                // Reads the next ciphertext chunk from the
                                // raw stream, bounded by the buffer size
                                var toRead = (int) Math.min(buffer.length, remainingText);
                                var read = rawInputStream.read(buffer, 0, toRead);
                                if (read == -1) {
                                    throw new WhatsAppMediaException.Download("Unexpected end of stream: expected " + remainingText + " more bytes");
                                }
                                remainingText -= read;

                                if (isEncrypted()) {
                                    if (ciphertextDigest != null) {
                                        // WAWebCryptoDecryptMedia.default
                                        // Extends the encFilehash digest
                                        // with the newly read ciphertext
                                        ciphertextDigest.update(buffer, 0, read);
                                    }

                                    // WAMediaCrypto.hmacAndDecrypt
                                    // Feeds the ciphertext chunk into the
                                    // HMAC over iv + ciphertext
                                    mac.update(buffer, 0, read);

                                    // WAMediaCrypto.hmacAndDecrypt
                                    // Decrypts in place producing the next
                                    // batch of plaintext bytes
                                    bufferOffset = 0;
                                    bufferLimit = cipher.update(buffer, 0, read, buffer, 0);
                                } else {
                                    bufferOffset = 0;
                                    bufferLimit = read;
                                }

                                if (plaintextDigest != null) {
                                    // WAMediaCrypto.hmacAndDecrypt
                                    // Extends the plaintext SHA-256 digest
                                    // over the decrypted bytes
                                    plaintextDigest.update(buffer, 0, bufferLimit);
                                }

                                if (inflatable) {
                                    // ADAPTED: WAWebMmsMediaTypes.MEDIA_TYPES
                                    // Feeds the decrypted bytes to the zlib
                                    // inflater for inflatable media types
                                    inflater.setInput(buffer, 0, bufferLimit);

                                    inflatedOffset = 0;
                                    inflatedLimit = inflater.inflate(inflatedBuffer);
                                }
                            } else {
                                if (isEncrypted()) {
                                    // WAMediaCrypto.hmacAndDecrypt
                                    // Finalises the cipher and drains the
                                    // last padded plaintext block
                                    bufferOffset = 0;
                                    bufferLimit = cipher.doFinal(buffer, 0);

                                    if (plaintextDigest != null) {
                                        plaintextDigest.update(buffer, 0, bufferLimit);
                                    }

                                    if (inflatable) {
                                        inflater.setInput(buffer, 0, bufferLimit);

                                        inflatedOffset = 0;
                                        inflatedLimit = inflater.inflate(inflatedBuffer);
                                    }

                                    state = State.READ_MAC;
                                } else {
                                    if (!inflatable || inflater.finished()) {
                                        state = State.VALIDATE_ALL;
                                    }
                                }
                            }
                        }

                        case READ_MAC -> {
                            // WAMediaCrypto.hmacAndDecrypt
                            // Extracts the 10-byte MAC trailer from the
                            // downloaded payload, accumulating across reads
                            var toRead = MAC_LENGTH - macBufferOffset;
                            if (toRead > 0) {
                                var read = rawInputStream.read(macBuffer, macBufferOffset, toRead);
                                if (read == -1) {
                                    throw new WhatsAppMediaException.Download("Unexpected end of stream: expected " + toRead + " more bytes");
                                }
                                macBufferOffset += read;
                            }

                            if (macBufferOffset == MAC_LENGTH) {
                                if (ciphertextDigest != null) {
                                    // WAWebCryptoDecryptMedia.default
                                    // encFilehash covers ciphertext + the
                                    // 10-byte HMAC trailer
                                    ciphertextDigest.update(macBuffer);
                                }

                                if (!inflatable || inflater.finished()) {
                                    state = State.VALIDATE_ALL;
                                }
                            }
                        }

                        case VALIDATE_ALL -> {
                            if (isEncrypted()) {
                                if (ciphertextDigest != null) {
                                    // WAWebCryptoDecryptMedia.default
                                    // Compares the running encFilehash
                                    // against the expected value in
                                    // constant time
                                    var actualCiphertextSha256 = ciphertextDigest.digest();
                                    if (!MessageDigest.isEqual(expectedCiphertextSha256, actualCiphertextSha256)) {
                                        throw new WhatsAppMediaException.Download("Ciphertext SHA256 hash doesn't match the expected value");
                                    }
                                }

                                // WAMediaCrypto.hmacAndDecrypt
                                // Finalises the HMAC and compares its first
                                // 10 bytes against the trailer in constant
                                // time (N function in the WA Web source).
                                // WA Web throws a HmacValidationError("hmacAndDecrypt
                                // hmac mismatch") on failure; per Cobalt's
                                // sealed exception model the equivalent
                                // signal is WhatsAppMediaException.Download.
                                // WA Web also rejects trailers outside 10..32
                                // bytes with HmacValidationError("Bad hmac
                                // size"); Cobalt always reads a fixed
                                // MAC_LENGTH trailer so the size check is
                                // structurally enforced at buffer allocation
                                // time rather than as a runtime throw.
                                var actualCiphertextMac = mac.doFinal();
                                if (!MessageDigest.isEqual(
                                        Arrays.copyOf(macBuffer, MAC_LENGTH),
                                        Arrays.copyOf(actualCiphertextMac, MAC_LENGTH))) {
                                    throw new WhatsAppMediaException.Download("Mac doesn't match the expected value");
                                }
                            }

                            if (plaintextDigest != null) {
                                // WAMediaCrypto.hmacAndDecrypt
                                // Verifies the plaintext hash so that the
                                // decrypted bytes match the sender's copy
                                var actualPlaintextSha256 = plaintextDigest.digest();
                                if (!MessageDigest.isEqual(expectedPlaintextSha256, actualPlaintextSha256)) {
                                    throw new WhatsAppMediaException.Download("Plaintext SHA256 hash doesn't match the expected value");
                                }
                            }

                            state = State.DONE;
                        }
                    }
                }
            }

            return state == State.DONE;
        } catch (IOException exception) {
            throw new WhatsAppMediaException.Download("Cannot read data", exception);
        } catch (GeneralSecurityException exception) {
            throw new WhatsAppMediaException.Download("Cannot decrypt data", exception);
        } catch (DataFormatException exception) {
            throw new WhatsAppMediaException.Download("Cannot inflate data", exception);
        }
    }

    /**
     * Returns whether this stream is processing encrypted media.
     *
     * @implNote WAMediaCrypto.hmacAndDecrypt: the encrypted path requires
     * an initialised cipher.
     * @return {@code true} if the cipher is initialized, {@code false} for
     *         unencrypted media
     */
    private boolean isEncrypted() {
        return cipher != null;
    }

    /**
     * Returns whether this stream applies zlib decompression after
     * decryption.
     *
     * @implNote ADAPTED: WAWebMmsMediaTypes: the inflatable flag on the
     * media type metadata.
     * @return {@code true} if the inflater is initialized, {@code false}
     *         otherwise
     */
    private boolean isInflatable() {
        return inflater != null;
    }

    /**
     * Closes the underlying raw stream, the owned HTTP client, and the
     * zlib inflater if one was allocated.
     *
     * @throws IOException if an I/O error occurs while closing
     */
    @Override
    public void close() throws IOException {
        super.close();
        client.close();
        if (inflater != null) {
            inflater.end();
        }
    }

    /**
     * The explicit states of the decryption and verification state
     * machine driven by {@link #isDone()}.
     *
     * @implNote ADAPTED: WAMediaCrypto.hmacAndDecrypt is a batch
     * operation; Cobalt splits it into READ_DATA, READ_MAC, VALIDATE_ALL,
     * and DONE so that decryption can be interleaved with output.
     */
    private enum State {
        /**
         * Ciphertext is being read from the raw stream, decrypted, and
         * optionally inflated for the caller.
         *
         * @implNote WAMediaCrypto.hmacAndDecrypt: the
         * {@code aesCbcDecrypt(cipherKey, iv, ciphertext)} pass over the
         * payload.
         */
        READ_DATA,

        /**
         * The trailing 10-byte HMAC is being pulled from the raw stream.
         *
         * @implNote WAMediaCrypto.hmacAndDecrypt: MAC extraction,
         * corresponding to the {@code a} parameter.
         */
        READ_MAC,

        /**
         * HMAC, ciphertext hash, and plaintext hash are being verified.
         *
         * @implNote WAMediaCrypto.hmacAndDecrypt:
         * {@code N(hmac[0:a.length], a)} constant-time comparison and
         * the plaintext hash check.
         */
        VALIDATE_ALL,

        /**
         * All data has been consumed and verified; the stream is
         * exhausted.
         *
         * @implNote WAMediaCrypto.hmacAndDecrypt: the function return
         * point after all checks succeed.
         */
        DONE
    }
}
