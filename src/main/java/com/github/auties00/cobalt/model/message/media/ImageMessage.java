package com.github.auties00.cobalt.model.message.media;

import com.github.auties00.cobalt.model.media.MediaPath;
import com.github.auties00.cobalt.model.message.context.ContextInfo;
import com.github.auties00.cobalt.model.message.interactive.InteractiveAnnotation;
import com.github.auties00.cobalt.model.message.interactive.InteractiveHeader;
import com.github.auties00.cobalt.model.message.interactive.InteractiveMessage;
import com.github.auties00.cobalt.model.message.interactive.TemplateMessage;
import com.github.auties00.cobalt.model.mixin.InstantSecondsMixin;
import it.auties.protobuf.annotation.ProtobufEnum;
import it.auties.protobuf.annotation.ProtobufEnumIndex;
import it.auties.protobuf.annotation.ProtobufMessage;
import it.auties.protobuf.annotation.ProtobufProperty;
import it.auties.protobuf.model.ProtobufType;

import java.time.Instant;
import java.util.*;

@ProtobufMessage(name = "Message.ImageMessage")
public final class ImageMessage implements InteractiveHeader, InteractiveMessage.MediaSpec, TemplateMessage.Title, TemplateMessage.TitleSpec, MediaMessage {
    @ProtobufProperty(index = 1, type = ProtobufType.STRING)
    String mediaUrl;

    @ProtobufProperty(index = 2, type = ProtobufType.STRING)
    String mimetype;

    @ProtobufProperty(index = 3, type = ProtobufType.STRING)
    String caption;

    @ProtobufProperty(index = 4, type = ProtobufType.BYTES)
    byte[] mediaSha256;

    @ProtobufProperty(index = 5, type = ProtobufType.UINT64)
    Long mediaSize;

    @ProtobufProperty(index = 6, type = ProtobufType.UINT32)
    Integer height;

    @ProtobufProperty(index = 7, type = ProtobufType.UINT32)
    Integer width;

    @ProtobufProperty(index = 8, type = ProtobufType.BYTES)
    byte[] mediaKey;

    @ProtobufProperty(index = 9, type = ProtobufType.BYTES)
    byte[] mediaEncryptedSha256;

    @ProtobufProperty(index = 10, type = ProtobufType.MESSAGE)
    List<InteractiveAnnotation> interactiveAnnotations;

    @ProtobufProperty(index = 11, type = ProtobufType.STRING)
    String mediaDirectPath;

    @ProtobufProperty(index = 12, type = ProtobufType.INT64, mixins = InstantSecondsMixin.class)
    Instant mediaKeyTimestamp;

    @ProtobufProperty(index = 16, type = ProtobufType.BYTES)
    byte[] jpegThumbnail;

    @ProtobufProperty(index = 17, type = ProtobufType.MESSAGE)
    ContextInfo contextInfo;

    @ProtobufProperty(index = 18, type = ProtobufType.BYTES)
    byte[] firstScanSidecar;

    @ProtobufProperty(index = 19, type = ProtobufType.UINT32)
    Integer firstScanLength;

    @ProtobufProperty(index = 20, type = ProtobufType.UINT32)
    Integer experimentGroupId;

    @ProtobufProperty(index = 21, type = ProtobufType.BYTES)
    byte[] scansSidecar;

    @ProtobufProperty(index = 22, type = ProtobufType.UINT32)
    List<Integer> scanLengths;

    @ProtobufProperty(index = 23, type = ProtobufType.BYTES)
    byte[] midQualityFileSha256;

    @ProtobufProperty(index = 24, type = ProtobufType.BYTES)
    byte[] midQualityFileEncSha256;

    @ProtobufProperty(index = 25, type = ProtobufType.BOOL)
    Boolean viewOnce;

    @ProtobufProperty(index = 26, type = ProtobufType.STRING)
    String thumbnailDirectPath;

    @ProtobufProperty(index = 27, type = ProtobufType.BYTES)
    byte[] thumbnailSha256;

    @ProtobufProperty(index = 28, type = ProtobufType.BYTES)
    byte[] thumbnailEncSha256;

    @ProtobufProperty(index = 29, type = ProtobufType.STRING)
    String staticUrl;

    @ProtobufProperty(index = 30, type = ProtobufType.MESSAGE)
    List<InteractiveAnnotation> annotations;

    @ProtobufProperty(index = 31, type = ProtobufType.ENUM)
    ImageSourceType imageSourceType;

    @ProtobufProperty(index = 32, type = ProtobufType.STRING)
    String accessibilityLabel;

    @ProtobufProperty(index = 33, type = ProtobufType.ENUM)
    MediaMessageKeyDomain mediaKeyDomain;

    @ProtobufProperty(index = 34, type = ProtobufType.STRING)
    String qrUrl;


    ImageMessage(String mediaUrl, String mimetype, String caption, byte[] mediaSha256, Long mediaSize, Integer height, Integer width, byte[] mediaKey, byte[] mediaEncryptedSha256, List<InteractiveAnnotation> interactiveAnnotations, String mediaDirectPath, Instant mediaKeyTimestamp, byte[] jpegThumbnail, ContextInfo contextInfo, byte[] firstScanSidecar, Integer firstScanLength, Integer experimentGroupId, byte[] scansSidecar, List<Integer> scanLengths, byte[] midQualityFileSha256, byte[] midQualityFileEncSha256, Boolean viewOnce, String thumbnailDirectPath, byte[] thumbnailSha256, byte[] thumbnailEncSha256, String staticUrl, List<InteractiveAnnotation> annotations, ImageSourceType imageSourceType, String accessibilityLabel, MediaMessageKeyDomain mediaKeyDomain, String qrUrl) {
        this.mediaUrl = mediaUrl;
        this.mimetype = mimetype;
        this.caption = caption;
        this.mediaSha256 = mediaSha256;
        this.mediaSize = mediaSize;
        this.height = height;
        this.width = width;
        this.mediaKey = mediaKey;
        this.mediaEncryptedSha256 = mediaEncryptedSha256;
        this.interactiveAnnotations = interactiveAnnotations;
        this.mediaDirectPath = mediaDirectPath;
        this.mediaKeyTimestamp = mediaKeyTimestamp;
        this.jpegThumbnail = jpegThumbnail;
        this.contextInfo = contextInfo;
        this.firstScanSidecar = firstScanSidecar;
        this.firstScanLength = firstScanLength;
        this.experimentGroupId = experimentGroupId;
        this.scansSidecar = scansSidecar;
        this.scanLengths = scanLengths;
        this.midQualityFileSha256 = midQualityFileSha256;
        this.midQualityFileEncSha256 = midQualityFileEncSha256;
        this.viewOnce = viewOnce;
        this.thumbnailDirectPath = thumbnailDirectPath;
        this.thumbnailSha256 = thumbnailSha256;
        this.thumbnailEncSha256 = thumbnailEncSha256;
        this.staticUrl = staticUrl;
        this.annotations = annotations;
        this.imageSourceType = imageSourceType;
        this.accessibilityLabel = accessibilityLabel;
        this.mediaKeyDomain = mediaKeyDomain;
        this.qrUrl = qrUrl;
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

    public Optional<String> caption() {
        return Optional.ofNullable(caption);
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

    public OptionalInt height() {
        return height == null ? OptionalInt.empty() : OptionalInt.of(height);
    }

    public OptionalInt width() {
        return width == null ? OptionalInt.empty() : OptionalInt.of(width);
    }

    public Optional<byte[]> mediaKey() {
        return Optional.ofNullable(mediaKey);
    }

    public Optional<byte[]> fileEncSha256() {
        return Optional.ofNullable(mediaEncryptedSha256);
    }

    @Override
    public Optional<byte[]> mediaEncryptedSha256() {
        return Optional.ofNullable(mediaEncryptedSha256);
    }

    public List<InteractiveAnnotation> interactiveAnnotations() {
        return interactiveAnnotations == null ? List.of() : Collections.unmodifiableList(interactiveAnnotations);
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

    public Optional<byte[]> jpegThumbnail() {
        return Optional.ofNullable(jpegThumbnail);
    }

    public Optional<ContextInfo> contextInfo() {
        return Optional.ofNullable(contextInfo);
    }

    public Optional<byte[]> firstScanSidecar() {
        return Optional.ofNullable(firstScanSidecar);
    }

    public OptionalInt firstScanLength() {
        return firstScanLength == null ? OptionalInt.empty() : OptionalInt.of(firstScanLength);
    }

    public OptionalInt experimentGroupId() {
        return experimentGroupId == null ? OptionalInt.empty() : OptionalInt.of(experimentGroupId);
    }

    public Optional<byte[]> scansSidecar() {
        return Optional.ofNullable(scansSidecar);
    }

    public List<Integer> scanLengths() {
        return scanLengths == null ? List.of() : Collections.unmodifiableList(scanLengths);
    }

    public Optional<byte[]> midQualityFileSha256() {
        return Optional.ofNullable(midQualityFileSha256);
    }

    public Optional<byte[]> midQualityFileEncSha256() {
        return Optional.ofNullable(midQualityFileEncSha256);
    }

    public boolean viewOnce() {
        return viewOnce != null && viewOnce;
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

    public Optional<String> staticUrl() {
        return Optional.ofNullable(staticUrl);
    }

    public List<InteractiveAnnotation> annotations() {
        return annotations == null ? List.of() : Collections.unmodifiableList(annotations);
    }

    public Optional<ImageSourceType> imageSourceType() {
        return Optional.ofNullable(imageSourceType);
    }

    public Optional<String> accessibilityLabel() {
        return Optional.ofNullable(accessibilityLabel);
    }

    public Optional<MediaMessageKeyDomain> mediaKeyDomain() {
        return Optional.ofNullable(mediaKeyDomain);
    }

    public Optional<String> qrUrl() {
        return Optional.ofNullable(qrUrl);
    }

    @Override
    public MediaPath mediaPath() {
        return MediaPath.IMAGE;
    }

    @Override
    public void setMediaUrl(String mediaUrl) {
        this.mediaUrl = mediaUrl;
    }

    public void setMimetype(String mimetype) {
        this.mimetype = mimetype;
    }

    public void setCaption(String caption) {
        this.caption = caption;
    }

    @Override
    public void setMediaSha256(byte[] mediaSha256) {
        this.mediaSha256 = mediaSha256;
    }

    @Override
    public void setMediaSize(long mediaSize) {
        this.mediaSize = mediaSize;
    }

    public void setHeight(Integer height) {
        this.height = height;
    }

    public void setWidth(Integer width) {
        this.width = width;
    }

    @Override
    public void setMediaKey(byte[] mediaKey) {
        this.mediaKey = mediaKey;
    }

    @Override
    public void setMediaEncryptedSha256(byte[] mediaEncryptedSha256) {
        this.mediaEncryptedSha256 = mediaEncryptedSha256;
    }

    public void setInteractiveAnnotations(List<InteractiveAnnotation> interactiveAnnotations) {
        this.interactiveAnnotations = interactiveAnnotations;
    }

    @Override
    public void setMediaDirectPath(String mediaDirectPath) {
        this.mediaDirectPath = mediaDirectPath;
    }

    @Override
    public void setMediaKeyTimestamp(Instant mediaKeyTimestamp) {
        this.mediaKeyTimestamp = mediaKeyTimestamp;
    }

    public void setJpegThumbnail(byte[] jpegThumbnail) {
        this.jpegThumbnail = jpegThumbnail;
    }

    public void setContextInfo(ContextInfo contextInfo) {
        this.contextInfo = contextInfo;
    }

    public void setFirstScanSidecar(byte[] firstScanSidecar) {
        this.firstScanSidecar = firstScanSidecar;
    }

    public void setFirstScanLength(Integer firstScanLength) {
        this.firstScanLength = firstScanLength;
    }

    public void setExperimentGroupId(Integer experimentGroupId) {
        this.experimentGroupId = experimentGroupId;
    }

    public void setScansSidecar(byte[] scansSidecar) {
        this.scansSidecar = scansSidecar;
    }

    public void setScanLengths(List<Integer> scanLengths) {
        this.scanLengths = scanLengths;
    }

    public void setMidQualityFileSha256(byte[] midQualityFileSha256) {
        this.midQualityFileSha256 = midQualityFileSha256;
    }

    public void setMidQualityFileEncSha256(byte[] midQualityFileEncSha256) {
        this.midQualityFileEncSha256 = midQualityFileEncSha256;
    }

    public void setViewOnce(Boolean viewOnce) {
        this.viewOnce = viewOnce;
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

    public void setStaticUrl(String staticUrl) {
        this.staticUrl = staticUrl;
    }

    public void setAnnotations(List<InteractiveAnnotation> annotations) {
        this.annotations = annotations;
    }

    public void setImageSourceType(ImageSourceType imageSourceType) {
        this.imageSourceType = imageSourceType;
    }

    public void setAccessibilityLabel(String accessibilityLabel) {
        this.accessibilityLabel = accessibilityLabel;
    }

    public void setMediaKeyDomain(MediaMessageKeyDomain mediaKeyDomain) {
        this.mediaKeyDomain = mediaKeyDomain;
    }

    public void setQrUrl(String qrUrl) {
        this.qrUrl = qrUrl;
    }

    @ProtobufEnum(name = "Message.ImageMessage.ImageSourceType")
    public static enum ImageSourceType {
        USER_IMAGE(0),
        AI_GENERATED(1),
        AI_MODIFIED(2),
        RASTERIZED_TEXT_STATUS(3);

        ImageSourceType(@ProtobufEnumIndex int index) {
            this.index = index;
        }

        final int index;

        public int index() {
            return this.index;
        }
    }
}
