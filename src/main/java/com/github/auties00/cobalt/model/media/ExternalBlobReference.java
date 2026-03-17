package com.github.auties00.cobalt.model.media;

import it.auties.protobuf.annotation.*;
import it.auties.protobuf.model.*;

import java.time.Instant;
import java.util.Optional;
import java.util.OptionalLong;

@ProtobufMessage(name = "ExternalBlobReference")
public final class ExternalBlobReference implements MediaProvider {
    @ProtobufProperty(index = 1, type = ProtobufType.BYTES)
    byte[] mediaKey;

    @ProtobufProperty(index = 2, type = ProtobufType.STRING)
    String mediaDirectPath;

    @ProtobufProperty(index = 3, type = ProtobufType.STRING)
    String handle;

    @ProtobufProperty(index = 4, type = ProtobufType.UINT64)
    Long mediaSize;

    @ProtobufProperty(index = 5, type = ProtobufType.BYTES)
    byte[] mediaSha256;

    @ProtobufProperty(index = 6, type = ProtobufType.BYTES)
    byte[] mediaEncryptedSha256;


    ExternalBlobReference(byte[] mediaKey, String mediaDirectPath, String handle, Long mediaSize, byte[] mediaSha256, byte[] mediaEncryptedSha256) {
        this.mediaKey = mediaKey;
        this.mediaDirectPath = mediaDirectPath;
        this.handle = handle;
        this.mediaSize = mediaSize;
        this.mediaSha256 = mediaSha256;
        this.mediaEncryptedSha256 = mediaEncryptedSha256;
    }

    @Override
    public Optional<byte[]> mediaKey() {
        return Optional.ofNullable(mediaKey);
    }

    public Optional<String> directPath() {
        return Optional.ofNullable(mediaDirectPath);
    }

    @Override
    public Optional<String> mediaDirectPath() {
        return Optional.ofNullable(mediaDirectPath);
    }

    public Optional<String> handle() {
        return Optional.ofNullable(handle);
    }

    public OptionalLong fileSizeBytes() {
        return mediaSize == null ? OptionalLong.empty() : OptionalLong.of(mediaSize);
    }

    @Override
    public OptionalLong mediaSize() {
        return mediaSize == null ? OptionalLong.empty() : OptionalLong.of(mediaSize);
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

    @Override
    public Optional<String> mediaUrl() {
        return Optional.empty();
    }

    @Override
    public MediaPath mediaPath() {
        return MediaPath.APP_STATE;
    }

    @Override
    public void setMediaKey(byte[] mediaKey) {
        this.mediaKey = mediaKey;
    }

    @Override
    public void setMediaDirectPath(String mediaDirectPath) {
        this.mediaDirectPath = mediaDirectPath;
    }

    public void setHandle(String handle) {
        this.handle = handle;
    }

    @Override
    public void setMediaSize(long mediaSize) {
        this.mediaSize = mediaSize;
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
    public void setMediaUrl(String mediaUrl) {
    }

    @Override
    public void setMediaKeyTimestamp(Instant timestamp) {
    }
}
