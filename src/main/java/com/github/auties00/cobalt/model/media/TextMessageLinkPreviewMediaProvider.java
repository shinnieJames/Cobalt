package com.github.auties00.cobalt.model.media;

import com.github.auties00.cobalt.model.message.standard.TextMessage;

import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;

public final class TextMessageLinkPreviewMediaProvider implements MediaProvider {
    private final TextMessage textMessage;
    private String mediaUrl;

    public TextMessageLinkPreviewMediaProvider(TextMessage textMessage) {
        this.textMessage = Objects.requireNonNull(textMessage, "textMessage cannot be null");
    }

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
        return textMessage.thumbnailDirectPath();
    }

    @Override
    public void setMediaDirectPath(String mediaDirectPath) {
        textMessage.setThumbnailDirectPath(mediaDirectPath);
    }

    @Override
    public Optional<byte[]> mediaKey() {
        return textMessage.mediaKey();
    }

    @Override
    public void setMediaKey(byte[] bytes) {
        textMessage.setMediaKey(bytes);
    }

    @Override
    public void setMediaKeyTimestamp(Long timestamp) {
        textMessage.setMediaKeyTimestampSeconds(timestamp);
    }

    @Override
    public Optional<byte[]> mediaSha256() {
        return textMessage.thumbnailSha256();
    }

    @Override
    public void setMediaSha256(byte[] bytes) {
        textMessage.setThumbnailSha256(bytes);
    }

    @Override
    public Optional<byte[]> mediaEncryptedSha256() {
        return textMessage.thumbnailEncSha256();
    }

    @Override
    public void setMediaEncryptedSha256(byte[] bytes) {
        textMessage.setThumbnailEncSha256(bytes);
    }

    @Override
    public OptionalLong mediaSize() {
        return OptionalLong.empty();
    }

    @Override
    public void setMediaSize(long mediaSize) {
        // Not serialized on TextMessage previews.
    }

    @Override
    public MediaPath mediaPath() {
        return MediaPath.THUMBNAIL_LINK;
    }
}
