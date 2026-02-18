package com.github.auties00.cobalt.model.message.util;

import com.github.auties00.cobalt.model.message.Message;

import java.time.Instant;
import it.auties.protobuf.annotation.*;
import it.auties.protobuf.model.*;
import java.util.Optional;
import java.util.OptionalInt;

@ProtobufMessage(name = "Message.MMSThumbnailMetadata")
public final class MMSThumbnailMetadata implements Message {
    @ProtobufProperty(index = 1, type = ProtobufType.STRING)
    String thumbnailDirectPath;

    @ProtobufProperty(index = 2, type = ProtobufType.BYTES)
    byte[] thumbnailSha256;

    @ProtobufProperty(index = 3, type = ProtobufType.BYTES)
    byte[] thumbnailEncSha256;

    @ProtobufProperty(index = 4, type = ProtobufType.BYTES)
    byte[] mediaKey;

    @ProtobufProperty(index = 5, type = ProtobufType.INT64, mixins = InstantProtobufMixin.class)
    Instant mediaKeyTimestamp;

    @ProtobufProperty(index = 6, type = ProtobufType.UINT32)
    Integer thumbnailHeight;

    @ProtobufProperty(index = 7, type = ProtobufType.UINT32)
    Integer thumbnailWidth;

    @ProtobufProperty(index = 8, type = ProtobufType.ENUM)
    MediaKeyDomain mediaKeyDomain;


    MMSThumbnailMetadata(String thumbnailDirectPath, byte[] thumbnailSha256, byte[] thumbnailEncSha256, byte[] mediaKey, Instant mediaKeyTimestamp, Integer thumbnailHeight, Integer thumbnailWidth, MediaKeyDomain mediaKeyDomain) {
        this.thumbnailDirectPath = thumbnailDirectPath;
        this.thumbnailSha256 = thumbnailSha256;
        this.thumbnailEncSha256 = thumbnailEncSha256;
        this.mediaKey = mediaKey;
        this.mediaKeyTimestamp = mediaKeyTimestamp;
        this.thumbnailHeight = thumbnailHeight;
        this.thumbnailWidth = thumbnailWidth;
        this.mediaKeyDomain = mediaKeyDomain;
    }

    public Optional<String> thumbnailDirectPath() {
        return Optional.ofNullable(thumbnailDirectPath);
    }

    public Optional<byte[]> thumbnailSha256() {
        return Optional.ofNullable(thumbnailSha256);
    }

    public Optional<byte[]> thumbnailEncSha256() {
        return Optional.ofNullable(thumbnailEncSha256);
    }

    public Optional<byte[]> mediaKey() {
        return Optional.ofNullable(mediaKey);
    }

    public Optional<Instant> mediaKeyTimestamp() {
        return Optional.ofNullable(mediaKeyTimestamp);
    }

    public OptionalInt thumbnailHeight() {
        return thumbnailHeight == null ? OptionalInt.empty() : OptionalInt.of(thumbnailHeight);
    }

    public OptionalInt thumbnailWidth() {
        return thumbnailWidth == null ? OptionalInt.empty() : OptionalInt.of(thumbnailWidth);
    }

    public Optional<MediaKeyDomain> mediaKeyDomain() {
        return Optional.ofNullable(mediaKeyDomain);
    }

    public MMSThumbnailMetadata setThumbnailDirectPath(String thumbnailDirectPath) {
        this.thumbnailDirectPath = thumbnailDirectPath;
        return this;
    }

    public MMSThumbnailMetadata setThumbnailSha256(byte[] thumbnailSha256) {
        this.thumbnailSha256 = thumbnailSha256;
        return this;
    }

    public MMSThumbnailMetadata setThumbnailEncSha256(byte[] thumbnailEncSha256) {
        this.thumbnailEncSha256 = thumbnailEncSha256;
        return this;
    }

    public MMSThumbnailMetadata setMediaKey(byte[] mediaKey) {
        this.mediaKey = mediaKey;
        return this;
    }

    public MMSThumbnailMetadata setMediaKeyTimestamp(Instant mediaKeyTimestamp) {
        this.mediaKeyTimestamp = mediaKeyTimestamp;
        return this;
    }

    public MMSThumbnailMetadata setThumbnailHeight(Integer thumbnailHeight) {
        this.thumbnailHeight = thumbnailHeight;
        return this;
    }

    public MMSThumbnailMetadata setThumbnailWidth(Integer thumbnailWidth) {
        this.thumbnailWidth = thumbnailWidth;
        return this;
    }

    public MMSThumbnailMetadata setMediaKeyDomain(MediaKeyDomain mediaKeyDomain) {
        this.mediaKeyDomain = mediaKeyDomain;
        return this;
    }
}
