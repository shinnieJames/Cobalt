package com.github.auties00.cobalt.model.message.media;

import com.github.auties00.cobalt.model.media.MediaPath;
import com.github.auties00.cobalt.model.message.context.ContextInfo;
import com.github.auties00.cobalt.model.message.interactive.InteractiveHeader;
import com.github.auties00.cobalt.model.message.interactive.InteractiveMessage;
import com.github.auties00.cobalt.model.message.interactive.TemplateMessage;
import com.github.auties00.cobalt.model.mixin.InstantSecondsMixin;
import it.auties.protobuf.annotation.ProtobufMessage;
import it.auties.protobuf.annotation.ProtobufProperty;
import it.auties.protobuf.model.ProtobufType;

import java.time.Instant;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;

@ProtobufMessage(name = "Message.DocumentMessage")
public final class DocumentMessage implements InteractiveHeader, InteractiveMessage.MediaSpec, TemplateMessage.Title, TemplateMessage.TitleSpec, MediaMessage {
    @ProtobufProperty(index = 1, type = ProtobufType.STRING)
    String mediaUrl;

    @ProtobufProperty(index = 2, type = ProtobufType.STRING)
    String mimetype;

    @ProtobufProperty(index = 3, type = ProtobufType.STRING)
    String title;

    @ProtobufProperty(index = 4, type = ProtobufType.BYTES)
    byte[] mediaSha256;

    @ProtobufProperty(index = 5, type = ProtobufType.UINT64)
    Long mediaSize;

    @ProtobufProperty(index = 6, type = ProtobufType.UINT32)
    Integer pageCount;

    @ProtobufProperty(index = 7, type = ProtobufType.BYTES)
    byte[] mediaKey;

    @ProtobufProperty(index = 8, type = ProtobufType.STRING)
    String fileName;

    @ProtobufProperty(index = 9, type = ProtobufType.BYTES)
    byte[] mediaEncryptedSha256;

    @ProtobufProperty(index = 10, type = ProtobufType.STRING)
    String mediaDirectPath;

    @ProtobufProperty(index = 11, type = ProtobufType.INT64, mixins = InstantSecondsMixin.class)
    Instant mediaKeyTimestamp;

    @ProtobufProperty(index = 12, type = ProtobufType.BOOL)
    Boolean contactVcard;

    @ProtobufProperty(index = 13, type = ProtobufType.STRING)
    String thumbnailDirectPath;

    @ProtobufProperty(index = 14, type = ProtobufType.BYTES)
    byte[] thumbnailSha256;

    @ProtobufProperty(index = 15, type = ProtobufType.BYTES)
    byte[] thumbnailEncSha256;

    @ProtobufProperty(index = 16, type = ProtobufType.BYTES)
    byte[] jpegThumbnail;

    @ProtobufProperty(index = 17, type = ProtobufType.MESSAGE)
    ContextInfo contextInfo;

    @ProtobufProperty(index = 18, type = ProtobufType.UINT32)
    Integer thumbnailHeight;

    @ProtobufProperty(index = 19, type = ProtobufType.UINT32)
    Integer thumbnailWidth;

    @ProtobufProperty(index = 20, type = ProtobufType.STRING)
    String caption;

    @ProtobufProperty(index = 21, type = ProtobufType.STRING)
    String accessibilityLabel;

    @ProtobufProperty(index = 22, type = ProtobufType.ENUM)
    MediaMessageKeyDomain mediaKeyDomain;


    DocumentMessage(String mediaUrl, String mimetype, String title, byte[] mediaSha256, Long mediaSize, Integer pageCount, byte[] mediaKey, String fileName, byte[] mediaEncryptedSha256, String mediaDirectPath, Instant mediaKeyTimestamp, Boolean contactVcard, String thumbnailDirectPath, byte[] thumbnailSha256, byte[] thumbnailEncSha256, byte[] jpegThumbnail, ContextInfo contextInfo, Integer thumbnailHeight, Integer thumbnailWidth, String caption, String accessibilityLabel, MediaMessageKeyDomain mediaKeyDomain) {
        this.mediaUrl = mediaUrl;
        this.mimetype = mimetype;
        this.title = title;
        this.mediaSha256 = mediaSha256;
        this.mediaSize = mediaSize;
        this.pageCount = pageCount;
        this.mediaKey = mediaKey;
        this.fileName = fileName;
        this.mediaEncryptedSha256 = mediaEncryptedSha256;
        this.mediaDirectPath = mediaDirectPath;
        this.mediaKeyTimestamp = mediaKeyTimestamp;
        this.contactVcard = contactVcard;
        this.thumbnailDirectPath = thumbnailDirectPath;
        this.thumbnailSha256 = thumbnailSha256;
        this.thumbnailEncSha256 = thumbnailEncSha256;
        this.jpegThumbnail = jpegThumbnail;
        this.contextInfo = contextInfo;
        this.thumbnailHeight = thumbnailHeight;
        this.thumbnailWidth = thumbnailWidth;
        this.caption = caption;
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

    public Optional<String> mimetype() {
        return Optional.ofNullable(mimetype);
    }

    public Optional<String> title() {
        return Optional.ofNullable(title);
    }

    public Optional<byte[]> fileSha256() {
        return Optional.ofNullable(mediaSha256);
    }

    @Override
    public Optional<byte[]> mediaSha256() {
        return Optional.ofNullable(mediaSha256);
    }

    public OptionalLong fileLength() {
        return mediaSize == null ? OptionalLong.empty() : OptionalLong.of(mediaSize);
    }

    @Override
    public OptionalLong mediaSize() {
        return mediaSize == null ? OptionalLong.empty() : OptionalLong.of(mediaSize);
    }

    public OptionalInt pageCount() {
        return pageCount == null ? OptionalInt.empty() : OptionalInt.of(pageCount);
    }

    public Optional<byte[]> mediaKey() {
        return Optional.ofNullable(mediaKey);
    }

    public Optional<String> fileName() {
        return Optional.ofNullable(fileName);
    }

    public Optional<byte[]> fileEncSha256() {
        return Optional.ofNullable(mediaEncryptedSha256);
    }

    @Override
    public Optional<byte[]> mediaEncryptedSha256() {
        return Optional.ofNullable(mediaEncryptedSha256);
    }

    public Optional<String> directPath() {
        return Optional.ofNullable(mediaDirectPath);
    }

    @Override
    public Optional<String> mediaDirectPath() {
        return Optional.ofNullable(mediaDirectPath);
    }

    public Optional<Instant> mediaKeyTimestamp() {
        return Optional.ofNullable(mediaKeyTimestamp);
    }

    public boolean contactVcard() {
        return contactVcard != null && contactVcard;
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

    public Optional<byte[]> jpegThumbnail() {
        return Optional.ofNullable(jpegThumbnail);
    }

    public Optional<ContextInfo> contextInfo() {
        return Optional.ofNullable(contextInfo);
    }

    public OptionalInt thumbnailHeight() {
        return thumbnailHeight == null ? OptionalInt.empty() : OptionalInt.of(thumbnailHeight);
    }

    public OptionalInt thumbnailWidth() {
        return thumbnailWidth == null ? OptionalInt.empty() : OptionalInt.of(thumbnailWidth);
    }

    public Optional<String> caption() {
        return Optional.ofNullable(caption);
    }

    public Optional<String> accessibilityLabel() {
        return Optional.ofNullable(accessibilityLabel);
    }

    public Optional<MediaMessageKeyDomain> mediaKeyDomain() {
        return Optional.ofNullable(mediaKeyDomain);
    }

    @Override
    public MediaPath mediaPath() {
        return MediaPath.DOCUMENT;
    }

    @Override
    public void setMediaUrl(String mediaUrl) {
        this.mediaUrl = mediaUrl;
    }

    public void setMimetype(String mimetype) {
        this.mimetype = mimetype;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    @Override
    public void setMediaSha256(byte[] mediaSha256) {
        this.mediaSha256 = mediaSha256;
    }

    @Override
    public void setMediaSize(long mediaSize) {
        this.mediaSize = mediaSize;
    }

    public void setPageCount(Integer pageCount) {
        this.pageCount = pageCount;
    }

    @Override
    public void setMediaKey(byte[] mediaKey) {
        this.mediaKey = mediaKey;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    @Override
    public void setMediaEncryptedSha256(byte[] mediaEncryptedSha256) {
        this.mediaEncryptedSha256 = mediaEncryptedSha256;
    }

    @Override
    public void setMediaDirectPath(String mediaDirectPath) {
        this.mediaDirectPath = mediaDirectPath;
    }

    @Override
    public void setMediaKeyTimestamp(Instant mediaKeyTimestamp) {
        this.mediaKeyTimestamp = mediaKeyTimestamp;
    }

    public void setContactVcard(Boolean contactVcard) {
        this.contactVcard = contactVcard;
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

    public void setJpegThumbnail(byte[] jpegThumbnail) {
        this.jpegThumbnail = jpegThumbnail;
    }

    public void setContextInfo(ContextInfo contextInfo) {
        this.contextInfo = contextInfo;
    }

    public void setThumbnailHeight(Integer thumbnailHeight) {
        this.thumbnailHeight = thumbnailHeight;
    }

    public void setThumbnailWidth(Integer thumbnailWidth) {
        this.thumbnailWidth = thumbnailWidth;
    }

    public void setCaption(String caption) {
        this.caption = caption;
    }

    public void setAccessibilityLabel(String accessibilityLabel) {
        this.accessibilityLabel = accessibilityLabel;
    }

    public void setMediaKeyDomain(MediaMessageKeyDomain mediaKeyDomain) {
        this.mediaKeyDomain = mediaKeyDomain;
    }
}
