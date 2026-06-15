package com.github.auties00.cobalt.model.media;

import java.util.Optional;
import java.util.OptionalLong;

public final class ThumbnailLinkMediaProvider implements MediaProvider {
    private String mediaUrl;
    private String mediaDirectPath;
    private byte[] mediaKey;
    private byte[] mediaSha256;
    private byte[] mediaEncryptedSha256;
    private Long mediaKeyTimestampSeconds;
    private Long mediaSize;

    @Override
    public Optional<String> mediaUrl() {
        return Optional.ofNullable(mediaUrl);
    }

    @Override
    public void setMediaUrl(String mediaUrl) {
        this.mediaUrl = mediaUrl;
    }

    @Override
    public Optional<String> mediaDirectPath() {
        return Optional.ofNullable(mediaDirectPath);
    }

    @Override
    public void setMediaDirectPath(String mediaDirectPath) {
        this.mediaDirectPath = mediaDirectPath;
    }

    @Override
    public Optional<byte[]> mediaKey() {
        return Optional.ofNullable(mediaKey);
    }

    @Override
    public void setMediaKey(byte[] bytes) {
        this.mediaKey = bytes;
    }

    @Override
    public void setMediaKeyTimestamp(Long timestamp) {
        this.mediaKeyTimestampSeconds = timestamp;
    }

    @Override
    public Optional<byte[]> mediaSha256() {
        return Optional.ofNullable(mediaSha256);
    }

    @Override
    public void setMediaSha256(byte[] bytes) {
        this.mediaSha256 = bytes;
    }

    @Override
    public Optional<byte[]> mediaEncryptedSha256() {
        return Optional.ofNullable(mediaEncryptedSha256);
    }

    @Override
    public void setMediaEncryptedSha256(byte[] bytes) {
        this.mediaEncryptedSha256 = bytes;
    }

    @Override
    public OptionalLong mediaSize() {
        return mediaSize == null ? OptionalLong.empty() : OptionalLong.of(mediaSize);
    }

    @Override
    public void setMediaSize(long mediaSize) {
        this.mediaSize = mediaSize;
    }

    @Override
    public MediaPath mediaPath() {
        return MediaPath.THUMBNAIL_LINK;
    }

    public OptionalLong mediaKeyTimestampSeconds() {
        return mediaKeyTimestampSeconds == null ? OptionalLong.empty() : OptionalLong.of(mediaKeyTimestampSeconds);
    }
}
