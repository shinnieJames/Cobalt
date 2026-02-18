package com.github.auties00.cobalt.model.media;

import it.auties.protobuf.annotation.*;
import it.auties.protobuf.model.*;
import java.util.Optional;
import java.util.OptionalLong;

/**
 * A reference to an externally stored encrypted blob used during application
 * state synchronization.
 *
 * <p>When a synchronization snapshot or patch exceeds the inline size threshold,
 * the server stores the mutation data as an encrypted blob on the media CDN and
 * provides this reference so the client can download and decrypt it. The client
 * uses the {@code mediaKey} together with the {@code directPath} (or
 * {@code handle}) to fetch the blob, then verifies integrity against
 * {@code fileSha256} (plaintext) and {@code fileEncSha256} (ciphertext) before
 * processing the contained mutations.
 *
 * <p>This message is defined in {@code WAWebProtobufsServerSync.pb} and appears
 * inside {@code SyncdSnapshot} and {@code SyncdPatch} structures.
 */
@ProtobufMessage(name = "ExternalBlobReference")
public final class ExternalBlobReference {
    /**
     * The symmetric encryption key used to decrypt the downloaded blob.
     */
    @ProtobufProperty(index = 1, type = ProtobufType.BYTES)
    byte[] mediaKey;

    /**
     * The CDN direct path from which the encrypted blob can be fetched.
     */
    @ProtobufProperty(index = 2, type = ProtobufType.STRING)
    String directPath;

    /**
     * An opaque server-assigned handle that identifies this blob on the CDN.
     */
    @ProtobufProperty(index = 3, type = ProtobufType.STRING)
    String handle;

    /**
     * The size of the encrypted blob in bytes.
     */
    @ProtobufProperty(index = 4, type = ProtobufType.UINT64)
    Long fileSizeBytes;

    /**
     * The SHA-256 digest of the plaintext (decrypted) blob content, used for
     * integrity verification after decryption.
     */
    @ProtobufProperty(index = 5, type = ProtobufType.BYTES)
    byte[] fileSha256;

    /**
     * The SHA-256 digest of the encrypted blob content, used for integrity
     * verification before decryption.
     */
    @ProtobufProperty(index = 6, type = ProtobufType.BYTES)
    byte[] fileEncSha256;

    /**
     * Constructs a new {@code ExternalBlobReference} with the given field values.
     *
     * @param mediaKey      the symmetric encryption key for the blob
     * @param directPath    the CDN direct path to the blob
     * @param handle        the opaque server-assigned blob handle
     * @param fileSizeBytes the size of the encrypted blob in bytes
     * @param fileSha256    the SHA-256 digest of the plaintext blob
     * @param fileEncSha256 the SHA-256 digest of the encrypted blob
     */
    ExternalBlobReference(byte[] mediaKey, String directPath, String handle, Long fileSizeBytes, byte[] fileSha256, byte[] fileEncSha256) {
        this.mediaKey = mediaKey;
        this.directPath = directPath;
        this.handle = handle;
        this.fileSizeBytes = fileSizeBytes;
        this.fileSha256 = fileSha256;
        this.fileEncSha256 = fileEncSha256;
    }

    /**
     * Returns the symmetric encryption key used to decrypt the downloaded blob.
     *
     * @return an {@link Optional} containing the media key, or empty if not set
     */
    public Optional<byte[]> mediaKey() {
        return Optional.ofNullable(mediaKey);
    }

    /**
     * Returns the CDN direct path from which the encrypted blob can be fetched.
     *
     * @return an {@link Optional} containing the direct path, or empty if not set
     */
    public Optional<String> directPath() {
        return Optional.ofNullable(directPath);
    }

    /**
     * Returns the opaque server-assigned handle that identifies this blob on the CDN.
     *
     * @return an {@link Optional} containing the handle, or empty if not set
     */
    public Optional<String> handle() {
        return Optional.ofNullable(handle);
    }

    /**
     * Returns the size of the encrypted blob in bytes.
     *
     * @return an {@link OptionalLong} containing the file size, or empty if not set
     */
    public OptionalLong fileSizeBytes() {
        return fileSizeBytes == null ? OptionalLong.empty() : OptionalLong.of(fileSizeBytes);
    }

    /**
     * Returns the SHA-256 digest of the plaintext blob content.
     *
     * @return an {@link Optional} containing the SHA-256 hash, or empty if not set
     */
    public Optional<byte[]> fileSha256() {
        return Optional.ofNullable(fileSha256);
    }

    /**
     * Returns the SHA-256 digest of the encrypted blob content.
     *
     * @return an {@link Optional} containing the encrypted SHA-256 hash, or empty if not set
     */
    public Optional<byte[]> fileEncSha256() {
        return Optional.ofNullable(fileEncSha256);
    }

    /**
     * Sets the symmetric encryption key for the blob.
     *
     * @param mediaKey the encryption key
     * @return this instance for chaining
     */
    public ExternalBlobReference setMediaKey(byte[] mediaKey) {
        this.mediaKey = mediaKey;
        return this;
    }

    /**
     * Sets the CDN direct path to the blob.
     *
     * @param directPath the direct path
     * @return this instance for chaining
     */
    public ExternalBlobReference setDirectPath(String directPath) {
        this.directPath = directPath;
        return this;
    }

    /**
     * Sets the opaque server-assigned blob handle.
     *
     * @param handle the blob handle
     * @return this instance for chaining
     */
    public ExternalBlobReference setHandle(String handle) {
        this.handle = handle;
        return this;
    }

    /**
     * Sets the size of the encrypted blob in bytes.
     *
     * @param fileSizeBytes the file size in bytes
     * @return this instance for chaining
     */
    public ExternalBlobReference setFileSizeBytes(Long fileSizeBytes) {
        this.fileSizeBytes = fileSizeBytes;
        return this;
    }

    /**
     * Sets the SHA-256 digest of the plaintext blob content.
     *
     * @param fileSha256 the plaintext SHA-256 hash
     * @return this instance for chaining
     */
    public ExternalBlobReference setFileSha256(byte[] fileSha256) {
        this.fileSha256 = fileSha256;
        return this;
    }

    /**
     * Sets the SHA-256 digest of the encrypted blob content.
     *
     * @param fileEncSha256 the encrypted SHA-256 hash
     * @return this instance for chaining
     */
    public ExternalBlobReference setFileEncSha256(byte[] fileEncSha256) {
        this.fileEncSha256 = fileEncSha256;
        return this;
    }
}
