package com.github.auties00.cobalt.model.message.media;

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
    String url;

    @ProtobufProperty(index = 2, type = ProtobufType.STRING)
    String mimetype;

    @ProtobufProperty(index = 3, type = ProtobufType.STRING)
    String title;

    @ProtobufProperty(index = 4, type = ProtobufType.BYTES)
    byte[] fileSha256;

    @ProtobufProperty(index = 5, type = ProtobufType.UINT64)
    Long fileLength;

    @ProtobufProperty(index = 6, type = ProtobufType.UINT32)
    Integer pageCount;

    @ProtobufProperty(index = 7, type = ProtobufType.BYTES)
    byte[] mediaKey;

    @ProtobufProperty(index = 8, type = ProtobufType.STRING)
    String fileName;

    @ProtobufProperty(index = 9, type = ProtobufType.BYTES)
    byte[] fileEncSha256;

    @ProtobufProperty(index = 10, type = ProtobufType.STRING)
    String directPath;

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


    DocumentMessage(String url, String mimetype, String title, byte[] fileSha256, Long fileLength, Integer pageCount, byte[] mediaKey, String fileName, byte[] fileEncSha256, String directPath, Instant mediaKeyTimestamp, Boolean contactVcard, String thumbnailDirectPath, byte[] thumbnailSha256, byte[] thumbnailEncSha256, byte[] jpegThumbnail, ContextInfo contextInfo, Integer thumbnailHeight, Integer thumbnailWidth, String caption, String accessibilityLabel, MediaMessageKeyDomain mediaKeyDomain) {
        this.url = url;
        this.mimetype = mimetype;
        this.title = title;
        this.fileSha256 = fileSha256;
        this.fileLength = fileLength;
        this.pageCount = pageCount;
        this.mediaKey = mediaKey;
        this.fileName = fileName;
        this.fileEncSha256 = fileEncSha256;
        this.directPath = directPath;
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
        return Optional.ofNullable(url);
    }

    public Optional<String> mimetype() {
        return Optional.ofNullable(mimetype);
    }

    public Optional<String> title() {
        return Optional.ofNullable(title);
    }

    public Optional<byte[]> fileSha256() {
        return Optional.ofNullable(fileSha256);
    }

    public OptionalLong fileLength() {
        return fileLength == null ? OptionalLong.empty() : OptionalLong.of(fileLength);
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
        return Optional.ofNullable(fileEncSha256);
    }

    public Optional<String> directPath() {
        return Optional.ofNullable(directPath);
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

    public DocumentMessage setUrl(String url) {
        this.url = url;
        return this;
    }

    public DocumentMessage setMimetype(String mimetype) {
        this.mimetype = mimetype;
        return this;
    }

    public DocumentMessage setTitle(String title) {
        this.title = title;
        return this;
    }

    public DocumentMessage setFileSha256(byte[] fileSha256) {
        this.fileSha256 = fileSha256;
        return this;
    }

    public DocumentMessage setFileLength(Long fileLength) {
        this.fileLength = fileLength;
        return this;
    }

    public DocumentMessage setPageCount(Integer pageCount) {
        this.pageCount = pageCount;
        return this;
    }

    public DocumentMessage setMediaKey(byte[] mediaKey) {
        this.mediaKey = mediaKey;
        return this;
    }

    public DocumentMessage setFileName(String fileName) {
        this.fileName = fileName;
        return this;
    }

    public DocumentMessage setFileEncSha256(byte[] fileEncSha256) {
        this.fileEncSha256 = fileEncSha256;
        return this;
    }

    public DocumentMessage setDirectPath(String directPath) {
        this.directPath = directPath;
        return this;
    }

    public DocumentMessage setMediaKeyTimestamp(Instant mediaKeyTimestamp) {
        this.mediaKeyTimestamp = mediaKeyTimestamp;
        return this;
    }

    public DocumentMessage setContactVcard(Boolean contactVcard) {
        this.contactVcard = contactVcard;
        return this;
    }

    public DocumentMessage setThumbnailDirectPath(String thumbnailDirectPath) {
        this.thumbnailDirectPath = thumbnailDirectPath;
        return this;
    }

    public DocumentMessage setThumbnailSha256(byte[] thumbnailSha256) {
        this.thumbnailSha256 = thumbnailSha256;
        return this;
    }

    public DocumentMessage setThumbnailEncSha256(byte[] thumbnailEncSha256) {
        this.thumbnailEncSha256 = thumbnailEncSha256;
        return this;
    }

    public DocumentMessage setJpegThumbnail(byte[] jpegThumbnail) {
        this.jpegThumbnail = jpegThumbnail;
        return this;
    }

    public DocumentMessage setContextInfo(ContextInfo contextInfo) {
        this.contextInfo = contextInfo;
        return this;
    }

    public DocumentMessage setThumbnailHeight(Integer thumbnailHeight) {
        this.thumbnailHeight = thumbnailHeight;
        return this;
    }

    public DocumentMessage setThumbnailWidth(Integer thumbnailWidth) {
        this.thumbnailWidth = thumbnailWidth;
        return this;
    }

    public DocumentMessage setCaption(String caption) {
        this.caption = caption;
        return this;
    }

    public DocumentMessage setAccessibilityLabel(String accessibilityLabel) {
        this.accessibilityLabel = accessibilityLabel;
        return this;
    }

    public DocumentMessage setMediaKeyDomain(MediaMessageKeyDomain mediaKeyDomain) {
        this.mediaKeyDomain = mediaKeyDomain;
        return this;
    }
}
