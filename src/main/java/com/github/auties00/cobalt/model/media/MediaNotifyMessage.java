package com.github.auties00.cobalt.model.media;

import it.auties.protobuf.annotation.*;
import it.auties.protobuf.model.*;
import java.util.Optional;
import java.util.OptionalLong;

/**
 * A server-originated notification that informs the client about the
 * availability of a media file through an express download path.
 *
 * <p>When a media file is available on the CDN via an optimized express path,
 * the server sends this notification so the client can bypass the standard
 * download flow and fetch the file directly from the express path URL. The
 * client uses the encrypted file hash to match this notification to the
 * corresponding media message and the file length for download validation.
 *
 * <p>This message is defined in {@code WAWebProtobufsE2E.pb} and appears at
 * field index 21 inside the {@code ProtocolMessage} structure.
 */
@ProtobufMessage(name = "MediaNotifyMessage")
public final class MediaNotifyMessage {
    /**
     * The express path URL from which the media file can be downloaded
     * through the optimized CDN route.
     */
    @ProtobufProperty(index = 1, type = ProtobufType.STRING)
    String expressPathUrl;

    /**
     * The SHA-256 digest of the encrypted media file, used to correlate
     * this notification with the corresponding media message.
     */
    @ProtobufProperty(index = 2, type = ProtobufType.BYTES)
    byte[] fileEncSha256;

    /**
     * The total length of the media file in bytes.
     */
    @ProtobufProperty(index = 3, type = ProtobufType.UINT64)
    Long fileLength;

    /**
     * Constructs a new {@code MediaNotifyMessage} with the given field values.
     *
     * @param expressPathUrl the express path URL for optimized download
     * @param fileEncSha256  the SHA-256 digest of the encrypted file
     * @param fileLength     the total file length in bytes
     */
    MediaNotifyMessage(String expressPathUrl, byte[] fileEncSha256, Long fileLength) {
        this.expressPathUrl = expressPathUrl;
        this.fileEncSha256 = fileEncSha256;
        this.fileLength = fileLength;
    }

    /**
     * Returns the express path URL from which the media file can be downloaded.
     *
     * @return an {@link Optional} containing the express path URL, or empty if not set
     */
    public Optional<String> expressPathUrl() {
        return Optional.ofNullable(expressPathUrl);
    }

    /**
     * Returns the SHA-256 digest of the encrypted media file.
     *
     * @return an {@link Optional} containing the encrypted file hash, or empty if not set
     */
    public Optional<byte[]> fileEncSha256() {
        return Optional.ofNullable(fileEncSha256);
    }

    /**
     * Returns the total length of the media file in bytes.
     *
     * @return an {@link OptionalLong} containing the file length, or empty if not set
     */
    public OptionalLong fileLength() {
        return fileLength == null ? OptionalLong.empty() : OptionalLong.of(fileLength);
    }

    /**
     * Sets the express path URL for optimized download.
     *
     * @param expressPathUrl the express path URL
     */
    public void setExpressPathUrl(String expressPathUrl) {
        this.expressPathUrl = expressPathUrl;
    }

    /**
     * Sets the SHA-256 digest of the encrypted file.
     *
     * @param fileEncSha256 the encrypted file hash
     */
    public void setFileEncSha256(byte[] fileEncSha256) {
        this.fileEncSha256 = fileEncSha256;
    }

    /**
     * Sets the total file length in bytes.
     *
     * @param fileLength the file length
     */
    public void setFileLength(Long fileLength) {
        this.fileLength = fileLength;
    }
}
