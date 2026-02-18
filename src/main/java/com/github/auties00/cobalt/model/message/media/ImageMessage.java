package com.github.auties00.cobalt.model.message.media;

import com.github.auties00.cobalt.model.message.ContextInfo;
import com.github.auties00.cobalt.model.message.MediaMessage;
import com.github.auties00.cobalt.model.message.interactive.InteractiveAnnotation;
import com.github.auties00.cobalt.model.message.util.MediaKeyDomain;

import java.time.Instant;
import java.util.*;

@ProtobufMessage(name = "Message.ImageMessage")
public final class ImageMessage implements InteractiveHeader, InteractiveMessage.MediaSpec, TemplateMessage.Title, TemplateMessage.TitleSpec, MediaMessage {
    @ProtobufProperty(index = 1, type = ProtobufType.STRING)
    String url;

    @ProtobufProperty(index = 2, type = ProtobufType.STRING)
    String mimetype;

    @ProtobufProperty(index = 3, type = ProtobufType.STRING)
    String caption;

    @ProtobufProperty(index = 4, type = ProtobufType.BYTES)
    byte[] fileSha256;

    @ProtobufProperty(index = 5, type = ProtobufType.UINT64)
    Long fileLength;

    @ProtobufProperty(index = 6, type = ProtobufType.UINT32)
    Integer height;

    @ProtobufProperty(index = 7, type = ProtobufType.UINT32)
    Integer width;

    @ProtobufProperty(index = 8, type = ProtobufType.BYTES)
    byte[] mediaKey;

    @ProtobufProperty(index = 9, type = ProtobufType.BYTES)
    byte[] fileEncSha256;

    @ProtobufProperty(index = 10, type = ProtobufType.MESSAGE)
    List<InteractiveAnnotation> interactiveAnnotations;

    @ProtobufProperty(index = 11, type = ProtobufType.STRING)
    String directPath;

    @ProtobufProperty(index = 12, type = ProtobufType.INT64, mixins = InstantProtobufMixin.class)
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
    MediaKeyDomain mediaKeyDomain;

    @ProtobufProperty(index = 34, type = ProtobufType.STRING)
    String qrUrl;


    ImageMessage(String url, String mimetype, String caption, byte[] fileSha256, Long fileLength, Integer height, Integer width, byte[] mediaKey, byte[] fileEncSha256, List<InteractiveAnnotation> interactiveAnnotations, String directPath, Instant mediaKeyTimestamp, byte[] jpegThumbnail, ContextInfo contextInfo, byte[] firstScanSidecar, Integer firstScanLength, Integer experimentGroupId, byte[] scansSidecar, List<Integer> scanLengths, byte[] midQualityFileSha256, byte[] midQualityFileEncSha256, Boolean viewOnce, String thumbnailDirectPath, byte[] thumbnailSha256, byte[] thumbnailEncSha256, String staticUrl, List<InteractiveAnnotation> annotations, ImageSourceType imageSourceType, String accessibilityLabel, MediaKeyDomain mediaKeyDomain, String qrUrl) {
        this.url = url;
        this.mimetype = mimetype;
        this.caption = caption;
        this.fileSha256 = fileSha256;
        this.fileLength = fileLength;
        this.height = height;
        this.width = width;
        this.mediaKey = mediaKey;
        this.fileEncSha256 = fileEncSha256;
        this.interactiveAnnotations = interactiveAnnotations;
        this.directPath = directPath;
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
        return Optional.ofNullable(url);
    }

    public Optional<String> mimetype() {
        return Optional.ofNullable(mimetype);
    }

    public Optional<String> caption() {
        return Optional.ofNullable(caption);
    }

    public Optional<byte[]> fileSha256() {
        return Optional.ofNullable(fileSha256);
    }

    public OptionalLong fileLength() {
        return fileLength == null ? OptionalLong.empty() : OptionalLong.of(fileLength);
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
        return Optional.ofNullable(fileEncSha256);
    }

    public List<InteractiveAnnotation> interactiveAnnotations() {
        return interactiveAnnotations == null ? List.of() : Collections.unmodifiableList(interactiveAnnotations);
    }

    public Optional<String> directPath() {
        return Optional.ofNullable(directPath);
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

    public Optional<MediaKeyDomain> mediaKeyDomain() {
        return Optional.ofNullable(mediaKeyDomain);
    }

    public Optional<String> qrUrl() {
        return Optional.ofNullable(qrUrl);
    }

    public ImageMessage setUrl(String url) {
        this.url = url;
        return this;
    }

    public ImageMessage setMimetype(String mimetype) {
        this.mimetype = mimetype;
        return this;
    }

    public ImageMessage setCaption(String caption) {
        this.caption = caption;
        return this;
    }

    public ImageMessage setFileSha256(byte[] fileSha256) {
        this.fileSha256 = fileSha256;
        return this;
    }

    public ImageMessage setFileLength(Long fileLength) {
        this.fileLength = fileLength;
        return this;
    }

    public ImageMessage setHeight(Integer height) {
        this.height = height;
        return this;
    }

    public ImageMessage setWidth(Integer width) {
        this.width = width;
        return this;
    }

    public ImageMessage setMediaKey(byte[] mediaKey) {
        this.mediaKey = mediaKey;
        return this;
    }

    public ImageMessage setFileEncSha256(byte[] fileEncSha256) {
        this.fileEncSha256 = fileEncSha256;
        return this;
    }

    public ImageMessage setInteractiveAnnotations(List<InteractiveAnnotation> interactiveAnnotations) {
        this.interactiveAnnotations = interactiveAnnotations;
        return this;
    }

    public ImageMessage setDirectPath(String directPath) {
        this.directPath = directPath;
        return this;
    }

    public ImageMessage setMediaKeyTimestamp(Instant mediaKeyTimestamp) {
        this.mediaKeyTimestamp = mediaKeyTimestamp;
        return this;
    }

    public ImageMessage setJpegThumbnail(byte[] jpegThumbnail) {
        this.jpegThumbnail = jpegThumbnail;
        return this;
    }

    public ImageMessage setContextInfo(ContextInfo contextInfo) {
        this.contextInfo = contextInfo;
        return this;
    }

    public ImageMessage setFirstScanSidecar(byte[] firstScanSidecar) {
        this.firstScanSidecar = firstScanSidecar;
        return this;
    }

    public ImageMessage setFirstScanLength(Integer firstScanLength) {
        this.firstScanLength = firstScanLength;
        return this;
    }

    public ImageMessage setExperimentGroupId(Integer experimentGroupId) {
        this.experimentGroupId = experimentGroupId;
        return this;
    }

    public ImageMessage setScansSidecar(byte[] scansSidecar) {
        this.scansSidecar = scansSidecar;
        return this;
    }

    public ImageMessage setScanLengths(List<Integer> scanLengths) {
        this.scanLengths = scanLengths;
        return this;
    }

    public ImageMessage setMidQualityFileSha256(byte[] midQualityFileSha256) {
        this.midQualityFileSha256 = midQualityFileSha256;
        return this;
    }

    public ImageMessage setMidQualityFileEncSha256(byte[] midQualityFileEncSha256) {
        this.midQualityFileEncSha256 = midQualityFileEncSha256;
        return this;
    }

    public ImageMessage setViewOnce(Boolean viewOnce) {
        this.viewOnce = viewOnce;
        return this;
    }

    public ImageMessage setThumbnailDirectPath(String thumbnailDirectPath) {
        this.thumbnailDirectPath = thumbnailDirectPath;
        return this;
    }

    public ImageMessage setThumbnailSha256(byte[] thumbnailSha256) {
        this.thumbnailSha256 = thumbnailSha256;
        return this;
    }

    public ImageMessage setThumbnailEncSha256(byte[] thumbnailEncSha256) {
        this.thumbnailEncSha256 = thumbnailEncSha256;
        return this;
    }

    public ImageMessage setStaticUrl(String staticUrl) {
        this.staticUrl = staticUrl;
        return this;
    }

    public ImageMessage setAnnotations(List<InteractiveAnnotation> annotations) {
        this.annotations = annotations;
        return this;
    }

    public ImageMessage setImageSourceType(ImageSourceType imageSourceType) {
        this.imageSourceType = imageSourceType;
        return this;
    }

    public ImageMessage setAccessibilityLabel(String accessibilityLabel) {
        this.accessibilityLabel = accessibilityLabel;
        return this;
    }

    public ImageMessage setMediaKeyDomain(MediaKeyDomain mediaKeyDomain) {
        this.mediaKeyDomain = mediaKeyDomain;
        return this;
    }

    public ImageMessage setQrUrl(String qrUrl) {
        this.qrUrl = qrUrl;
        return this;
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
