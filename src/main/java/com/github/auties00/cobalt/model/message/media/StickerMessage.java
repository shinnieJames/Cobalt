package com.github.auties00.cobalt.model.message.media;

import com.github.auties00.cobalt.model.media.MediaPath;
import com.github.auties00.cobalt.model.message.context.ContextInfo;
import com.github.auties00.cobalt.model.mixin.InstantSecondsMixin;
import it.auties.protobuf.annotation.*;
import it.auties.protobuf.model.*;

import java.time.Instant;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;

@ProtobufMessage(name = "Message.StickerMessage")
public final class StickerMessage implements MediaMessage {
    @ProtobufProperty(index = 1, type = ProtobufType.STRING)
    String mediaUrl;

    @ProtobufProperty(index = 2, type = ProtobufType.BYTES)
    byte[] mediaSha256;

    @ProtobufProperty(index = 3, type = ProtobufType.BYTES)
    byte[] mediaEncryptedSha256;

    @ProtobufProperty(index = 4, type = ProtobufType.BYTES)
    byte[] mediaKey;

    @ProtobufProperty(index = 5, type = ProtobufType.STRING)
    String mimetype;

    @ProtobufProperty(index = 6, type = ProtobufType.UINT32)
    Integer height;

    @ProtobufProperty(index = 7, type = ProtobufType.UINT32)
    Integer width;

    @ProtobufProperty(index = 8, type = ProtobufType.STRING)
    String mediaDirectPath;

    @ProtobufProperty(index = 9, type = ProtobufType.UINT64)
    Long mediaSize;

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


    StickerMessage(String mediaUrl, byte[] mediaSha256, byte[] mediaEncryptedSha256, byte[] mediaKey, String mimetype, Integer height, Integer width, String mediaDirectPath, Long mediaSize, Instant mediaKeyTimestamp, Integer firstFrameLength, byte[] firstFrameSidecar, Boolean isAnimated, byte[] pngThumbnail, ContextInfo contextInfo, Instant stickerSentTs, Boolean isAvatar, Boolean isAiSticker, Boolean isLottie, String accessibilityLabel, MediaMessageKeyDomain mediaKeyDomain) {
        this.mediaUrl = mediaUrl;
        this.mediaSha256 = mediaSha256;
        this.mediaEncryptedSha256 = mediaEncryptedSha256;
        this.mediaKey = mediaKey;
        this.mimetype = mimetype;
        this.height = height;
        this.width = width;
        this.mediaDirectPath = mediaDirectPath;
        this.mediaSize = mediaSize;
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
        return Optional.ofNullable(mediaUrl);
    }

    @Override
    public Optional<String> mediaUrl() {
        return Optional.ofNullable(mediaUrl);
    }

    public Optional<byte[]> fileSha256() {
        return Optional.ofNullable(mediaSha256);
    }

    @Override
    public Optional<byte[]> mediaSha256() {
        return Optional.ofNullable(mediaSha256);
    }

    public Optional<byte[]> fileEncSha256() {
        return Optional.ofNullable(mediaEncryptedSha256);
    }

    @Override
    public Optional<byte[]> mediaEncryptedSha256() {
        return Optional.ofNullable(mediaEncryptedSha256);
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
        return Optional.ofNullable(mediaDirectPath);
    }

    @Override
    public Optional<String> mediaDirectPath() {
        return Optional.ofNullable(mediaDirectPath);
    }

    public OptionalLong fileLength() {
        return mediaSize == null ? OptionalLong.empty() : OptionalLong.of(mediaSize);
    }

    @Override
    public OptionalLong mediaSize() {
        return mediaSize == null ? OptionalLong.empty() : OptionalLong.of(mediaSize);
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

    @Override
    public MediaPath mediaPath() {
        return MediaPath.STICKER;
    }

    @Override
    public void setMediaUrl(String mediaUrl) {
        this.mediaUrl = mediaUrl;
    }

    @Override
    public void setMediaSha256(byte[] mediaSha256) {
        this.mediaSha256 = mediaSha256;
    }

    @Override
    public void setMediaEncryptedSha256(byte[] mediaEncryptedSha256) {
        this.mediaEncryptedSha256 = mediaEncryptedSha256;
    }

    @Override
    public void setMediaKey(byte[] mediaKey) {
        this.mediaKey = mediaKey;
    }

    public void setMimetype(String mimetype) {
        this.mimetype = mimetype;
    }

    public void setHeight(Integer height) {
        this.height = height;
    }

    public void setWidth(Integer width) {
        this.width = width;
    }

    @Override
    public void setMediaDirectPath(String mediaDirectPath) {
        this.mediaDirectPath = mediaDirectPath;
    }

    @Override
    public void setMediaSize(long mediaSize) {
        this.mediaSize = mediaSize;
    }

    @Override
    public void setMediaKeyTimestamp(Instant mediaKeyTimestamp) {
        this.mediaKeyTimestamp = mediaKeyTimestamp;
    }

    public void setFirstFrameLength(Integer firstFrameLength) {
        this.firstFrameLength = firstFrameLength;
    }

    public void setFirstFrameSidecar(byte[] firstFrameSidecar) {
        this.firstFrameSidecar = firstFrameSidecar;
    }

    public void setAnimated(Boolean isAnimated) {
        this.isAnimated = isAnimated;
    }

    public void setPngThumbnail(byte[] pngThumbnail) {
        this.pngThumbnail = pngThumbnail;
    }

    public void setContextInfo(ContextInfo contextInfo) {
        this.contextInfo = contextInfo;
    }

    public void setStickerSentTs(Instant stickerSentTs) {
        this.stickerSentTs = stickerSentTs;
    }

    public void setAvatar(Boolean isAvatar) {
        this.isAvatar = isAvatar;
    }

    public void setAiSticker(Boolean isAiSticker) {
        this.isAiSticker = isAiSticker;
    }

    public void setLottie(Boolean isLottie) {
        this.isLottie = isLottie;
    }

    public void setAccessibilityLabel(String accessibilityLabel) {
        this.accessibilityLabel = accessibilityLabel;
    }

    public void setMediaKeyDomain(MediaMessageKeyDomain mediaKeyDomain) {
        this.mediaKeyDomain = mediaKeyDomain;
    }
}
