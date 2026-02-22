package com.github.auties00.cobalt.model.message.media;

import com.github.auties00.cobalt.model.message.context.ContextInfo;

import java.time.Instant;

import com.github.auties00.cobalt.model.mixin.InstantSecondsMixin;
import it.auties.protobuf.annotation.*;
import it.auties.protobuf.model.*;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;

@ProtobufMessage(name = "Message.StickerMessage")
public final class StickerMessage implements MediaMessage {
    @ProtobufProperty(index = 1, type = ProtobufType.STRING)
    String url;

    @ProtobufProperty(index = 2, type = ProtobufType.BYTES)
    byte[] fileSha256;

    @ProtobufProperty(index = 3, type = ProtobufType.BYTES)
    byte[] fileEncSha256;

    @ProtobufProperty(index = 4, type = ProtobufType.BYTES)
    byte[] mediaKey;

    @ProtobufProperty(index = 5, type = ProtobufType.STRING)
    String mimetype;

    @ProtobufProperty(index = 6, type = ProtobufType.UINT32)
    Integer height;

    @ProtobufProperty(index = 7, type = ProtobufType.UINT32)
    Integer width;

    @ProtobufProperty(index = 8, type = ProtobufType.STRING)
    String directPath;

    @ProtobufProperty(index = 9, type = ProtobufType.UINT64)
    Long fileLength;

    @ProtobufProperty(index = 10, type = ProtobufType.INT64, mixins = InstantSecondsMixin.class)
    Instant mediaKeyTimestamp;

    @ProtobufProperty(index = 11, type = ProtobufType.UINT32)
    Integer firstFrameLength;

    @ProtobufProperty(index = 12, type = ProtobufType.BYTES)
    byte[] firstFrameSidecar;

    @ProtobufProperty(index = 13, type = ProtobufType.BOOL)
    Boolean isAnimated;

    @ProtobufProperty(index = 16, type = ProtobufType.BYTES)
    byte[] pngThumbnail;

    @ProtobufProperty(index = 17, type = ProtobufType.MESSAGE)
    ContextInfo contextInfo;

    @ProtobufProperty(index = 18, type = ProtobufType.INT64, mixins = InstantSecondsMixin.class)
    Instant stickerSentTs;

    @ProtobufProperty(index = 19, type = ProtobufType.BOOL)
    Boolean isAvatar;

    @ProtobufProperty(index = 20, type = ProtobufType.BOOL)
    Boolean isAiSticker;

    @ProtobufProperty(index = 21, type = ProtobufType.BOOL)
    Boolean isLottie;

    @ProtobufProperty(index = 22, type = ProtobufType.STRING)
    String accessibilityLabel;

    @ProtobufProperty(index = 23, type = ProtobufType.ENUM)
    MediaMessageKeyDomain mediaKeyDomain;


    StickerMessage(String url, byte[] fileSha256, byte[] fileEncSha256, byte[] mediaKey, String mimetype, Integer height, Integer width, String directPath, Long fileLength, Instant mediaKeyTimestamp, Integer firstFrameLength, byte[] firstFrameSidecar, Boolean isAnimated, byte[] pngThumbnail, ContextInfo contextInfo, Instant stickerSentTs, Boolean isAvatar, Boolean isAiSticker, Boolean isLottie, String accessibilityLabel, MediaMessageKeyDomain mediaKeyDomain) {
        this.url = url;
        this.fileSha256 = fileSha256;
        this.fileEncSha256 = fileEncSha256;
        this.mediaKey = mediaKey;
        this.mimetype = mimetype;
        this.height = height;
        this.width = width;
        this.directPath = directPath;
        this.fileLength = fileLength;
        this.mediaKeyTimestamp = mediaKeyTimestamp;
        this.firstFrameLength = firstFrameLength;
        this.firstFrameSidecar = firstFrameSidecar;
        this.isAnimated = isAnimated;
        this.pngThumbnail = pngThumbnail;
        this.contextInfo = contextInfo;
        this.stickerSentTs = stickerSentTs;
        this.isAvatar = isAvatar;
        this.isAiSticker = isAiSticker;
        this.isLottie = isLottie;
        this.accessibilityLabel = accessibilityLabel;
        this.mediaKeyDomain = mediaKeyDomain;
    }

    public Optional<String> url() {
        return Optional.ofNullable(url);
    }

    public Optional<byte[]> fileSha256() {
        return Optional.ofNullable(fileSha256);
    }

    public Optional<byte[]> fileEncSha256() {
        return Optional.ofNullable(fileEncSha256);
    }

    public Optional<byte[]> mediaKey() {
        return Optional.ofNullable(mediaKey);
    }

    public Optional<String> mimetype() {
        return Optional.ofNullable(mimetype);
    }

    public OptionalInt height() {
        return height == null ? OptionalInt.empty() : OptionalInt.of(height);
    }

    public OptionalInt width() {
        return width == null ? OptionalInt.empty() : OptionalInt.of(width);
    }

    public Optional<String> directPath() {
        return Optional.ofNullable(directPath);
    }

    public OptionalLong fileLength() {
        return fileLength == null ? OptionalLong.empty() : OptionalLong.of(fileLength);
    }

    public Optional<Instant> mediaKeyTimestamp() {
        return Optional.ofNullable(mediaKeyTimestamp);
    }

    public OptionalInt firstFrameLength() {
        return firstFrameLength == null ? OptionalInt.empty() : OptionalInt.of(firstFrameLength);
    }

    public Optional<byte[]> firstFrameSidecar() {
        return Optional.ofNullable(firstFrameSidecar);
    }

    public boolean isAnimated() {
        return isAnimated != null && isAnimated;
    }

    public Optional<byte[]> pngThumbnail() {
        return Optional.ofNullable(pngThumbnail);
    }

    public Optional<ContextInfo> contextInfo() {
        return Optional.ofNullable(contextInfo);
    }

    public Optional<Instant> stickerSentTs() {
        return Optional.ofNullable(stickerSentTs);
    }

    public boolean isAvatar() {
        return isAvatar != null && isAvatar;
    }

    public boolean isAiSticker() {
        return isAiSticker != null && isAiSticker;
    }

    public boolean isLottie() {
        return isLottie != null && isLottie;
    }

    public Optional<String> accessibilityLabel() {
        return Optional.ofNullable(accessibilityLabel);
    }

    public Optional<MediaMessageKeyDomain> mediaKeyDomain() {
        return Optional.ofNullable(mediaKeyDomain);
    }

    public StickerMessage setUrl(String url) {
        this.url = url;
        return this;
    }

    public StickerMessage setFileSha256(byte[] fileSha256) {
        this.fileSha256 = fileSha256;
        return this;
    }

    public StickerMessage setFileEncSha256(byte[] fileEncSha256) {
        this.fileEncSha256 = fileEncSha256;
        return this;
    }

    public StickerMessage setMediaKey(byte[] mediaKey) {
        this.mediaKey = mediaKey;
        return this;
    }

    public StickerMessage setMimetype(String mimetype) {
        this.mimetype = mimetype;
        return this;
    }

    public StickerMessage setHeight(Integer height) {
        this.height = height;
        return this;
    }

    public StickerMessage setWidth(Integer width) {
        this.width = width;
        return this;
    }

    public StickerMessage setDirectPath(String directPath) {
        this.directPath = directPath;
        return this;
    }

    public StickerMessage setFileLength(Long fileLength) {
        this.fileLength = fileLength;
        return this;
    }

    public StickerMessage setMediaKeyTimestamp(Instant mediaKeyTimestamp) {
        this.mediaKeyTimestamp = mediaKeyTimestamp;
        return this;
    }

    public StickerMessage setFirstFrameLength(Integer firstFrameLength) {
        this.firstFrameLength = firstFrameLength;
        return this;
    }

    public StickerMessage setFirstFrameSidecar(byte[] firstFrameSidecar) {
        this.firstFrameSidecar = firstFrameSidecar;
        return this;
    }

    public StickerMessage setAnimated(Boolean isAnimated) {
        this.isAnimated = isAnimated;
        return this;
    }

    public StickerMessage setPngThumbnail(byte[] pngThumbnail) {
        this.pngThumbnail = pngThumbnail;
        return this;
    }

    public StickerMessage setContextInfo(ContextInfo contextInfo) {
        this.contextInfo = contextInfo;
        return this;
    }

    public StickerMessage setStickerSentTs(Instant stickerSentTs) {
        this.stickerSentTs = stickerSentTs;
        return this;
    }

    public StickerMessage setAvatar(Boolean isAvatar) {
        this.isAvatar = isAvatar;
        return this;
    }

    public StickerMessage setAiSticker(Boolean isAiSticker) {
        this.isAiSticker = isAiSticker;
        return this;
    }

    public StickerMessage setLottie(Boolean isLottie) {
        this.isLottie = isLottie;
        return this;
    }

    public StickerMessage setAccessibilityLabel(String accessibilityLabel) {
        this.accessibilityLabel = accessibilityLabel;
        return this;
    }

    public StickerMessage setMediaKeyDomain(MediaMessageKeyDomain mediaKeyDomain) {
        this.mediaKeyDomain = mediaKeyDomain;
        return this;
    }
}
