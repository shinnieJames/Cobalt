package com.github.auties00.cobalt.model.message.media;

import com.github.auties00.cobalt.model.message.context.ContextInfo;
import com.github.auties00.cobalt.model.message.context.ContextualMessage;
import com.github.auties00.cobalt.model.mixin.InstantSecondsMixin;
import it.auties.protobuf.annotation.ProtobufEnum;
import it.auties.protobuf.annotation.ProtobufEnumIndex;
import it.auties.protobuf.annotation.ProtobufMessage;
import it.auties.protobuf.annotation.ProtobufProperty;
import it.auties.protobuf.model.ProtobufType;

import java.time.Instant;
import java.util.*;

@ProtobufMessage(name = "Message.StickerPackMessage")
public final class StickerPackMessage implements ContextualMessage {
    @ProtobufProperty(index = 1, type = ProtobufType.STRING)
    String stickerPackId;

    @ProtobufProperty(index = 2, type = ProtobufType.STRING)
    String name;

    @ProtobufProperty(index = 3, type = ProtobufType.STRING)
    String publisher;

    @ProtobufProperty(index = 4, type = ProtobufType.MESSAGE)
    List<Sticker> stickers;

    @ProtobufProperty(index = 5, type = ProtobufType.UINT64)
    Long fileLength;

    @ProtobufProperty(index = 6, type = ProtobufType.BYTES)
    byte[] fileSha256;

    @ProtobufProperty(index = 7, type = ProtobufType.BYTES)
    byte[] fileEncSha256;

    @ProtobufProperty(index = 8, type = ProtobufType.BYTES)
    byte[] mediaKey;

    @ProtobufProperty(index = 9, type = ProtobufType.STRING)
    String directPath;

    @ProtobufProperty(index = 10, type = ProtobufType.STRING)
    String caption;

    @ProtobufProperty(index = 11, type = ProtobufType.MESSAGE)
    ContextInfo contextInfo;

    @ProtobufProperty(index = 12, type = ProtobufType.STRING)
    String packDescription;

    @ProtobufProperty(index = 13, type = ProtobufType.INT64, mixins = InstantSecondsMixin.class)
    Instant mediaKeyTimestamp;

    @ProtobufProperty(index = 14, type = ProtobufType.STRING)
    String trayIconFileName;

    @ProtobufProperty(index = 15, type = ProtobufType.STRING)
    String thumbnailDirectPath;

    @ProtobufProperty(index = 16, type = ProtobufType.BYTES)
    byte[] thumbnailSha256;

    @ProtobufProperty(index = 17, type = ProtobufType.BYTES)
    byte[] thumbnailEncSha256;

    @ProtobufProperty(index = 18, type = ProtobufType.UINT32)
    Integer thumbnailHeight;

    @ProtobufProperty(index = 19, type = ProtobufType.UINT32)
    Integer thumbnailWidth;

    @ProtobufProperty(index = 20, type = ProtobufType.STRING)
    String imageDataHash;

    @ProtobufProperty(index = 21, type = ProtobufType.UINT64)
    Long stickerPackSize;

    @ProtobufProperty(index = 22, type = ProtobufType.ENUM)
    StickerPackOrigin stickerPackOrigin;


    StickerPackMessage(String stickerPackId, String name, String publisher, List<Sticker> stickers, Long fileLength, byte[] fileSha256, byte[] fileEncSha256, byte[] mediaKey, String directPath, String caption, ContextInfo contextInfo, String packDescription, Instant mediaKeyTimestamp, String trayIconFileName, String thumbnailDirectPath, byte[] thumbnailSha256, byte[] thumbnailEncSha256, Integer thumbnailHeight, Integer thumbnailWidth, String imageDataHash, Long stickerPackSize, StickerPackOrigin stickerPackOrigin) {
        this.stickerPackId = stickerPackId;
        this.name = name;
        this.publisher = publisher;
        this.stickers = stickers;
        this.fileLength = fileLength;
        this.fileSha256 = fileSha256;
        this.fileEncSha256 = fileEncSha256;
        this.mediaKey = mediaKey;
        this.directPath = directPath;
        this.caption = caption;
        this.contextInfo = contextInfo;
        this.packDescription = packDescription;
        this.mediaKeyTimestamp = mediaKeyTimestamp;
        this.trayIconFileName = trayIconFileName;
        this.thumbnailDirectPath = thumbnailDirectPath;
        this.thumbnailSha256 = thumbnailSha256;
        this.thumbnailEncSha256 = thumbnailEncSha256;
        this.thumbnailHeight = thumbnailHeight;
        this.thumbnailWidth = thumbnailWidth;
        this.imageDataHash = imageDataHash;
        this.stickerPackSize = stickerPackSize;
        this.stickerPackOrigin = stickerPackOrigin;
    }

    public Optional<String> stickerPackId() {
        return Optional.ofNullable(stickerPackId);
    }

    public Optional<String> name() {
        return Optional.ofNullable(name);
    }

    public Optional<String> publisher() {
        return Optional.ofNullable(publisher);
    }

    public List<Sticker> stickers() {
        return stickers == null ? List.of() : Collections.unmodifiableList(stickers);
    }

    public OptionalLong fileLength() {
        return fileLength == null ? OptionalLong.empty() : OptionalLong.of(fileLength);
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

    public Optional<String> directPath() {
        return Optional.ofNullable(directPath);
    }

    public Optional<String> caption() {
        return Optional.ofNullable(caption);
    }

    public Optional<ContextInfo> contextInfo() {
        return Optional.ofNullable(contextInfo);
    }

    public Optional<String> packDescription() {
        return Optional.ofNullable(packDescription);
    }

    public Optional<Instant> mediaKeyTimestamp() {
        return Optional.ofNullable(mediaKeyTimestamp);
    }

    public Optional<String> trayIconFileName() {
        return Optional.ofNullable(trayIconFileName);
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

    public OptionalInt thumbnailHeight() {
        return thumbnailHeight == null ? OptionalInt.empty() : OptionalInt.of(thumbnailHeight);
    }

    public OptionalInt thumbnailWidth() {
        return thumbnailWidth == null ? OptionalInt.empty() : OptionalInt.of(thumbnailWidth);
    }

    public Optional<String> imageDataHash() {
        return Optional.ofNullable(imageDataHash);
    }

    public OptionalLong stickerPackSize() {
        return stickerPackSize == null ? OptionalLong.empty() : OptionalLong.of(stickerPackSize);
    }

    public Optional<StickerPackOrigin> stickerPackOrigin() {
        return Optional.ofNullable(stickerPackOrigin);
    }

    public void setStickerPackId(String stickerPackId) {
        this.stickerPackId = stickerPackId;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setPublisher(String publisher) {
        this.publisher = publisher;
    }

    public void setStickers(List<Sticker> stickers) {
        this.stickers = stickers;
    }

    public void setFileLength(Long fileLength) {
        this.fileLength = fileLength;
    }

    public void setFileSha256(byte[] fileSha256) {
        this.fileSha256 = fileSha256;
    }

    public void setFileEncSha256(byte[] fileEncSha256) {
        this.fileEncSha256 = fileEncSha256;
    }

    public void setMediaKey(byte[] mediaKey) {
        this.mediaKey = mediaKey;
    }

    public void setDirectPath(String directPath) {
        this.directPath = directPath;
    }

    public void setCaption(String caption) {
        this.caption = caption;
    }

    public void setContextInfo(ContextInfo contextInfo) {
        this.contextInfo = contextInfo;
    }

    public void setPackDescription(String packDescription) {
        this.packDescription = packDescription;
    }

    public void setMediaKeyTimestamp(Instant mediaKeyTimestamp) {
        this.mediaKeyTimestamp = mediaKeyTimestamp;
    }

    public void setTrayIconFileName(String trayIconFileName) {
        this.trayIconFileName = trayIconFileName;
    }

    public void setThumbnailDirectPath(String thumbnailDirectPath) {
        this.thumbnailDirectPath = thumbnailDirectPath;
    }

    public void setThumbnailSha256(byte[] thumbnailSha256) {
        this.thumbnailSha256 = thumbnailSha256;
    }

    public void setThumbnailEncSha256(byte[] thumbnailEncSha256) {
        this.thumbnailEncSha256 = thumbnailEncSha256;
    }

    public void setThumbnailHeight(Integer thumbnailHeight) {
        this.thumbnailHeight = thumbnailHeight;
    }

    public void setThumbnailWidth(Integer thumbnailWidth) {
        this.thumbnailWidth = thumbnailWidth;
    }

    public void setImageDataHash(String imageDataHash) {
        this.imageDataHash = imageDataHash;
    }

    public void setStickerPackSize(Long stickerPackSize) {
        this.stickerPackSize = stickerPackSize;
    }

    public void setStickerPackOrigin(StickerPackOrigin stickerPackOrigin) {
        this.stickerPackOrigin = stickerPackOrigin;
    }

    @ProtobufEnum(name = "Message.StickerPackMessage.StickerPackOrigin")
    public static enum StickerPackOrigin {
        FIRST_PARTY(0),
        THIRD_PARTY(1),
        USER_CREATED(2);

        StickerPackOrigin(@ProtobufEnumIndex int index) {
            this.index = index;
        }

        final int index;

        public int index() {
            return this.index;
        }
    }

    @ProtobufMessage(name = "Message.StickerPackMessage.Sticker")
    public static final class Sticker {
        @ProtobufProperty(index = 1, type = ProtobufType.STRING)
        String fileName;

        @ProtobufProperty(index = 2, type = ProtobufType.BOOL)
        Boolean isAnimated;

        @ProtobufProperty(index = 3, type = ProtobufType.STRING)
        List<String> emojis;

        @ProtobufProperty(index = 4, type = ProtobufType.STRING)
        String accessibilityLabel;

        @ProtobufProperty(index = 5, type = ProtobufType.BOOL)
        Boolean isLottie;

        @ProtobufProperty(index = 6, type = ProtobufType.STRING)
        String mimetype;


        Sticker(String fileName, Boolean isAnimated, List<String> emojis, String accessibilityLabel, Boolean isLottie, String mimetype) {
            this.fileName = fileName;
            this.isAnimated = isAnimated;
            this.emojis = emojis;
            this.accessibilityLabel = accessibilityLabel;
            this.isLottie = isLottie;
            this.mimetype = mimetype;
        }

        public Optional<String> fileName() {
            return Optional.ofNullable(fileName);
        }

        public boolean isAnimated() {
            return isAnimated != null && isAnimated;
        }

        public List<String> emojis() {
            return emojis == null ? List.of() : Collections.unmodifiableList(emojis);
        }

        public Optional<String> accessibilityLabel() {
            return Optional.ofNullable(accessibilityLabel);
        }

        public boolean isLottie() {
            return isLottie != null && isLottie;
        }

        public Optional<String> mimetype() {
            return Optional.ofNullable(mimetype);
        }

        public void setFileName(String fileName) {
            this.fileName = fileName;
    }

        public void setAnimated(Boolean isAnimated) {
            this.isAnimated = isAnimated;
    }

        public void setEmojis(List<String> emojis) {
            this.emojis = emojis;
    }

        public void setAccessibilityLabel(String accessibilityLabel) {
            this.accessibilityLabel = accessibilityLabel;
    }

        public void setLottie(Boolean isLottie) {
            this.isLottie = isLottie;
    }

        public void setMimetype(String mimetype) {
            this.mimetype = mimetype;
    }
    }
}
