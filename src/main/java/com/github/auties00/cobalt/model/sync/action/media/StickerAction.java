package com.github.auties00.cobalt.model.sync.action.media;

import com.github.auties00.cobalt.model.sync.SyncAction;

import it.auties.protobuf.annotation.*;
import it.auties.protobuf.model.*;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;

@ProtobufMessage(name = "SyncActionValue.StickerAction")
public final class StickerAction implements SyncAction {
    @ProtobufProperty(index = 1, type = ProtobufType.STRING)
    String url;

    @ProtobufProperty(index = 2, type = ProtobufType.BYTES)
    byte[] fileEncSha256;

    @ProtobufProperty(index = 3, type = ProtobufType.BYTES)
    byte[] mediaKey;

    @ProtobufProperty(index = 4, type = ProtobufType.STRING)
    String mimetype;

    @ProtobufProperty(index = 5, type = ProtobufType.UINT32)
    Integer height;

    @ProtobufProperty(index = 6, type = ProtobufType.UINT32)
    Integer width;

    @ProtobufProperty(index = 7, type = ProtobufType.STRING)
    String directPath;

    @ProtobufProperty(index = 8, type = ProtobufType.UINT64)
    Long fileLength;

    @ProtobufProperty(index = 9, type = ProtobufType.BOOL)
    Boolean isFavorite;

    @ProtobufProperty(index = 10, type = ProtobufType.UINT32)
    Integer deviceIdHint;

    @ProtobufProperty(index = 11, type = ProtobufType.BOOL)
    Boolean isLottie;

    @ProtobufProperty(index = 12, type = ProtobufType.STRING)
    String imageHash;

    @ProtobufProperty(index = 13, type = ProtobufType.BOOL)
    Boolean isAvatarSticker;


    StickerAction(String url, byte[] fileEncSha256, byte[] mediaKey, String mimetype, Integer height, Integer width, String directPath, Long fileLength, Boolean isFavorite, Integer deviceIdHint, Boolean isLottie, String imageHash, Boolean isAvatarSticker) {
        this.url = url;
        this.fileEncSha256 = fileEncSha256;
        this.mediaKey = mediaKey;
        this.mimetype = mimetype;
        this.height = height;
        this.width = width;
        this.directPath = directPath;
        this.fileLength = fileLength;
        this.isFavorite = isFavorite;
        this.deviceIdHint = deviceIdHint;
        this.isLottie = isLottie;
        this.imageHash = imageHash;
        this.isAvatarSticker = isAvatarSticker;
    }

    public Optional<String> url() {
        return Optional.ofNullable(url);
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

    public boolean isFavorite() {
        return isFavorite != null && isFavorite;
    }

    public OptionalInt deviceIdHint() {
        return deviceIdHint == null ? OptionalInt.empty() : OptionalInt.of(deviceIdHint);
    }

    public boolean isLottie() {
        return isLottie != null && isLottie;
    }

    public Optional<String> imageHash() {
        return Optional.ofNullable(imageHash);
    }

    public boolean isAvatarSticker() {
        return isAvatarSticker != null && isAvatarSticker;
    }

    public StickerAction setUrl(String url) {
        this.url = url;
        return this;
    }

    public StickerAction setFileEncSha256(byte[] fileEncSha256) {
        this.fileEncSha256 = fileEncSha256;
        return this;
    }

    public StickerAction setMediaKey(byte[] mediaKey) {
        this.mediaKey = mediaKey;
        return this;
    }

    public StickerAction setMimetype(String mimetype) {
        this.mimetype = mimetype;
        return this;
    }

    public StickerAction setHeight(Integer height) {
        this.height = height;
        return this;
    }

    public StickerAction setWidth(Integer width) {
        this.width = width;
        return this;
    }

    public StickerAction setDirectPath(String directPath) {
        this.directPath = directPath;
        return this;
    }

    public StickerAction setFileLength(Long fileLength) {
        this.fileLength = fileLength;
        return this;
    }

    public StickerAction setFavorite(Boolean isFavorite) {
        this.isFavorite = isFavorite;
        return this;
    }

    public StickerAction setDeviceIdHint(Integer deviceIdHint) {
        this.deviceIdHint = deviceIdHint;
        return this;
    }

    public StickerAction setLottie(Boolean isLottie) {
        this.isLottie = isLottie;
        return this;
    }

    public StickerAction setImageHash(String imageHash) {
        this.imageHash = imageHash;
        return this;
    }

    public StickerAction setAvatarSticker(Boolean isAvatarSticker) {
        this.isAvatarSticker = isAvatarSticker;
        return this;
    }
}
