package com.github.auties00.cobalt.model.media;

import it.auties.protobuf.annotation.ProtobufEnum;
import it.auties.protobuf.annotation.ProtobufEnumIndex;
import it.auties.protobuf.annotation.ProtobufMessage;
import it.auties.protobuf.annotation.ProtobufProperty;
import it.auties.protobuf.model.ProtobufType;

import java.util.*;

/**
 * An alternative transcoded version of a video message, representing the
 * same video content at a different quality level or resolution.
 *
 * <p>This message is defined in {@code WAWebProtobufsE2E.pb} and appears as
 * a repeated field (index 27) inside {@code Message.VideoMessage}. When a
 * video is uploaded, the server may produce multiple transcoded versions at
 * different quality levels. Each {@code ProcessedVideo} describes one such
 * version, including its CDN location, file metadata, and encoding
 * parameters.
 *
 * <p>The client uses the {@code quality} field to select the most
 * appropriate version for the current network conditions and device
 * capabilities, and the {@code capabilities} list to determine whether a
 * given version is compatible with the playback environment.
 */
@ProtobufMessage(name = "ProcessedVideo")
public final class ProcessedVideo {
    /**
     * The CDN direct path from which this transcoded video version can be
     * fetched.
     */
    @ProtobufProperty(index = 1, type = ProtobufType.STRING)
    String directPath;

    /**
     * The SHA-256 digest of the transcoded video file, used for integrity
     * verification.
     */
    @ProtobufProperty(index = 2, type = ProtobufType.BYTES)
    byte[] fileSha256;

    /**
     * The height of the transcoded video in pixels.
     */
    @ProtobufProperty(index = 3, type = ProtobufType.UINT32)
    Integer height;

    /**
     * The width of the transcoded video in pixels.
     */
    @ProtobufProperty(index = 4, type = ProtobufType.UINT32)
    Integer width;

    /**
     * The total length of the transcoded video file in bytes.
     */
    @ProtobufProperty(index = 5, type = ProtobufType.UINT64)
    Long fileLength;

    /**
     * The video bitrate in bits per second.
     */
    @ProtobufProperty(index = 6, type = ProtobufType.UINT32)
    Integer bitrate;

    /**
     * The quality tier of this transcoded video version.
     */
    @ProtobufProperty(index = 7, type = ProtobufType.ENUM)
    VideoQuality quality;

    /**
     * The list of capability identifiers that describe the codec or
     * feature requirements for playing this video version.
     */
    @ProtobufProperty(index = 8, type = ProtobufType.STRING)
    List<String> capabilities;

    /**
     * Constructs a new {@code ProcessedVideo} with the given field values.
     *
     * @param directPath   the CDN direct path to the transcoded video
     * @param fileSha256   the SHA-256 digest of the file
     * @param height       the video height in pixels
     * @param width        the video width in pixels
     * @param fileLength   the file length in bytes
     * @param bitrate      the video bitrate in bits per second
     * @param quality      the quality tier
     * @param capabilities the list of playback capability identifiers
     */
    ProcessedVideo(String directPath, byte[] fileSha256, Integer height, Integer width, Long fileLength, Integer bitrate, VideoQuality quality, List<String> capabilities) {
        this.directPath = directPath;
        this.fileSha256 = fileSha256;
        this.height = height;
        this.width = width;
        this.fileLength = fileLength;
        this.bitrate = bitrate;
        this.quality = quality;
        this.capabilities = capabilities;
    }

    /**
     * Returns the CDN direct path from which this transcoded video version
     * can be fetched.
     *
     * @return an {@link Optional} containing the direct path, or empty if not set
     */
    public Optional<String> directPath() {
        return Optional.ofNullable(directPath);
    }

    /**
     * Returns the SHA-256 digest of the transcoded video file.
     *
     * @return an {@link Optional} containing the SHA-256 hash, or empty if not set
     */
    public Optional<byte[]> fileSha256() {
        return Optional.ofNullable(fileSha256);
    }

    /**
     * Returns the height of the transcoded video in pixels.
     *
     * @return an {@link OptionalInt} containing the height, or empty if not set
     */
    public OptionalInt height() {
        return height == null ? OptionalInt.empty() : OptionalInt.of(height);
    }

    /**
     * Returns the width of the transcoded video in pixels.
     *
     * @return an {@link OptionalInt} containing the width, or empty if not set
     */
    public OptionalInt width() {
        return width == null ? OptionalInt.empty() : OptionalInt.of(width);
    }

    /**
     * Returns the total length of the transcoded video file in bytes.
     *
     * @return an {@link OptionalLong} containing the file length, or empty if not set
     */
    public OptionalLong fileLength() {
        return fileLength == null ? OptionalLong.empty() : OptionalLong.of(fileLength);
    }

    /**
     * Returns the video bitrate in bits per second.
     *
     * @return an {@link OptionalInt} containing the bitrate, or empty if not set
     */
    public OptionalInt bitrate() {
        return bitrate == null ? OptionalInt.empty() : OptionalInt.of(bitrate);
    }

    /**
     * Returns the quality tier of this transcoded video version.
     *
     * @return an {@link Optional} containing the video quality, or empty if not set
     */
    public Optional<VideoQuality> quality() {
        return Optional.ofNullable(quality);
    }

    /**
     * Returns the list of capability identifiers that describe the playback
     * requirements for this video version.
     *
     * @return an unmodifiable list of capability strings, or an empty list
     *         if none are set
     */
    public List<String> capabilities() {
        return capabilities == null ? List.of() : Collections.unmodifiableList(capabilities);
    }

    /**
     * Sets the CDN direct path to the transcoded video.
     *
     * @param directPath the direct path
     */
    public void setDirectPath(String directPath) {
        this.directPath = directPath;
    }

    /**
     * Sets the SHA-256 digest of the transcoded video file.
     *
     * @param fileSha256 the SHA-256 hash
     */
    public void setFileSha256(byte[] fileSha256) {
        this.fileSha256 = fileSha256;
    }

    /**
     * Sets the height of the transcoded video in pixels.
     *
     * @param height the video height
     */
    public void setHeight(Integer height) {
        this.height = height;
    }

    /**
     * Sets the width of the transcoded video in pixels.
     *
     * @param width the video width
     */
    public void setWidth(Integer width) {
        this.width = width;
    }

    /**
     * Sets the total file length of the transcoded video in bytes.
     *
     * @param fileLength the file length
     */
    public void setFileLength(Long fileLength) {
        this.fileLength = fileLength;
    }

    /**
     * Sets the video bitrate in bits per second.
     *
     * @param bitrate the bitrate
     */
    public void setBitrate(Integer bitrate) {
        this.bitrate = bitrate;
    }

    /**
     * Sets the quality tier of this transcoded video version.
     *
     * @param quality the video quality
     */
    public void setQuality(VideoQuality quality) {
        this.quality = quality;
    }

    /**
     * Sets the list of playback capability identifiers.
     *
     * @param capabilities the capability strings
     */
    public void setCapabilities(List<String> capabilities) {
        this.capabilities = capabilities;
    }

    /**
     * The quality tier of a processed (transcoded) video version, indicating
     * the target resolution and bitrate range.
     *
     * <p>The client uses this value to select the most appropriate video
     * version based on current network conditions, available bandwidth, and
     * device display capabilities.
     */
    @ProtobufEnum(name = "ProcessedVideo.VideoQuality")
    public enum VideoQuality {
        /**
         * The singleton instance for an undefined quality tier, indicating
         * that the quality level was not specified by the server.
         * This has the numeric value of {@code 0}.
         */
        UNDEFINED(0),

        /**
         * The singleton instance for low quality video, targeting reduced
         * resolution and bitrate for bandwidth-constrained environments.
         * This has the numeric value of {@code 1}.
         */
        LOW(1),

        /**
         * The singleton instance for medium quality video, providing a
         * balance between visual quality and file size.
         * This has the numeric value of {@code 2}.
         */
        MID(2),

        /**
         * The singleton instance for high quality video, targeting the
         * highest available resolution and bitrate.
         * This has the numeric value of {@code 3}.
         */
        HIGH(3);

        /**
         * Constructs a new {@code VideoQuality} with the given protobuf index.
         *
         * @param index the protobuf enum index
         */
        VideoQuality(@ProtobufEnumIndex int index) {
            this.index = index;
        }

        /**
         * The protobuf enum index of this quality tier.
         */
        final int index;

        /**
         * Returns the protobuf enum index of this quality tier.
         *
         * @return the numeric index
         */
        public int index() {
            return this.index;
        }
    }
}
