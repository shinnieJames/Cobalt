package com.github.auties00.cobalt.model.media;

import com.github.auties00.cobalt.model.message.system.history.HistorySyncNotification;
import com.github.auties00.cobalt.model.sync.action.media.StickerAction;
import com.github.auties00.cobalt.model.message.media.MediaMessage;
import com.github.auties00.cobalt.model.preference.Sticker;

import java.time.Instant;
import java.util.Optional;
import java.util.OptionalLong;

/**
 * A unified interface for accessing media metadata across different
 * message and data types that carry downloadable media content.
 *
 * <p>This sealed interface provides a common set of accessors and mutators
 * for the fields that all media-bearing types share: a download URL, a CDN
 * direct path, an encryption key, SHA-256 hashes for both plaintext and
 * ciphertext, a file size, and a {@link MediaPath} that describes the CDN
 * route and encryption key label.
 *
 * <p>Implementations of this interface include end-to-end encrypted media
 * messages ({@link MediaMessage}), application state synchronization blobs
 * ({@link ExternalBlobReference}), history sync notification payloads
 * ({@link HistorySyncNotification}), and sticker-related structures
 * ({@link StickerAction}, {@link Sticker}).
 */
public sealed interface MediaProvider
        permits StickerAction, MediaMessage, Sticker, ExternalBlobReference, HistorySyncNotification {
    /**
     * Returns the CDN URL from which the encrypted media file can be
     * downloaded.
     *
     * @return an {@link Optional} containing the media URL, or empty if
     *         not set
     */
    Optional<String> mediaUrl();

    /**
     * Sets the CDN URL for the media file.
     *
     * @param mediaUrl the media URL
     */
    void setMediaUrl(String mediaUrl);

    /**
     * Returns the CDN direct path from which the encrypted media file can
     * be fetched.
     *
     * @return an {@link Optional} containing the direct path, or empty if
     *         not set
     */
    Optional<String> mediaDirectPath();

    /**
     * Sets the CDN direct path for the media file.
     *
     * @param mediaDirectPath the direct path
     */
    void setMediaDirectPath(String mediaDirectPath);

    /**
     * Returns the symmetric encryption key used to decrypt the media file.
     *
     * @return an {@link Optional} containing the media key, or empty if
     *         not set
     */
    Optional<byte[]> mediaKey();

    /**
     * Sets the symmetric encryption key for the media file.
     *
     * @param bytes the media key bytes
     */
    void setMediaKey(byte[] bytes);

    /**
     * Sets the epoch-second timestamp at which the media key was generated.
     *
     * @param timestamp the media key timestamp, or
     *        {@code null} to clear
     */
    void setMediaKeyTimestamp(Instant timestamp);

    /**
     * Returns the SHA-256 digest of the plaintext (decrypted) media file,
     * used for integrity verification after decryption.
     *
     * @return an {@link Optional} containing the SHA-256 hash, or empty if
     *         not set
     */
    Optional<byte[]> mediaSha256();

    /**
     * Sets the SHA-256 digest of the plaintext media file.
     *
     * @param bytes the plaintext SHA-256 hash
     */
    void setMediaSha256(byte[] bytes);

    /**
     * Returns the SHA-256 digest of the encrypted media file, used for
     * integrity verification before decryption.
     *
     * @return an {@link Optional} containing the encrypted SHA-256 hash,
     *         or empty if not set
     */
    Optional<byte[]> mediaEncryptedSha256();

    /**
     * Sets the SHA-256 digest of the encrypted media file.
     *
     * @param bytes the encrypted SHA-256 hash
     */
    void setMediaEncryptedSha256(byte[] bytes);

    /**
     * Returns the size of the media file in bytes.
     *
     * @return an {@link OptionalLong} containing the file size, or empty
     *         if not set
     */
    OptionalLong mediaSize();

    /**
     * Sets the size of the media file in bytes.
     *
     * @param mediaSize the file size in bytes
     */
    void setMediaSize(long mediaSize);

    /**
     * Returns the {@link MediaPath} that describes the CDN route and
     * encryption key derivation label for this media type.
     *
     * @return the media path for this provider, never {@code null}
     */
    MediaPath mediaPath();
}
