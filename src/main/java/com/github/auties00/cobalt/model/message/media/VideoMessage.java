package com.github.auties00.cobalt.model.message.media;

import com.github.auties00.cobalt.model.media.MediaPath;
import com.github.auties00.cobalt.model.media.ProcessedVideo;
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

@ProtobufMessage(name = "Message.VideoMessage")
public final class VideoMessage implements InteractiveHeader, InteractiveMessage.MediaSpec, TemplateMessage.Title, TemplateMessage.TitleSpec, MediaMessage {
    @ProtobufProperty(index = 1, type = ProtobufType.STRING)
    String mediaUrl;

    @ProtobufProperty(index = 2, type = ProtobufType.STRING)
    String mimetype;

    @ProtobufProperty(index = 3, type = ProtobufType.BYTES)
    byte[] mediaSha256;

    @ProtobufProperty(index = 4, type = ProtobufType.UINT64)
    Long mediaSize;

    @ProtobufProperty(index = 5, type = ProtobufType.UINT32)
    Integer seconds;

    @ProtobufProperty(index = 6, type = ProtobufType.BYTES)
    byte[] mediaKey;

    @ProtobufProperty(index = 7, type = ProtobufType.STRING)
    String caption;

    @ProtobufProperty(index = 8, type = ProtobufType.BOOL)
    Boolean gifPlayback;

    @ProtobufProperty(index = 9, type = ProtobufType.UINT32)
    Integer height;

    @ProtobufProperty(index = 10, type = ProtobufType.UINT32)
    Integer width;

    @ProtobufProperty(index = 11, type = ProtobufType.BYTES)
    byte[] mediaEncryptedSha256;

    @ProtobufProperty(index = 12, type = ProtobufType.MESSAGE)
    List<InteractiveAnnotation> interactiveAnnotations;

    @ProtobufProperty(index = 13, type = ProtobufType.STRING)
    String mediaDirectPath;

    @ProtobufProperty(index = 14, type = ProtobufType.INT64, mixins = InstantSecondsMixin.class)
    Instant mediaKeyTimestamp;

    @ProtobufProperty(index = 16, type = ProtobufType.BYTES)
    byte[] jpegThumbnail;

    @ProtobufProperty(index = 17, type = ProtobufType.MESSAGE)
    ContextInfo contextInfo;

    @ProtobufProperty(index = 18, type = ProtobufType.BYTES)
    byte[] streamingSidecar;

    @ProtobufProperty(index = 19, type = ProtobufType.ENUM)
    Attribution gifAttribution;

    @ProtobufProperty(index = 20, type = ProtobufType.BOOL)
    Boolean viewOnce;

    @ProtobufProperty(index = 21, type = ProtobufType.STRING)
    String thumbnailDirectPath;

    @ProtobufProperty(index = 22, type = ProtobufType.BYTES)
    byte[] thumbnailSha256;

    @ProtobufProperty(index = 23, type = ProtobufType.BYTES)
    byte[] thumbnailEncSha256;

    @ProtobufProperty(index = 24, type = ProtobufType.STRING)
    String staticUrl;

    @ProtobufProperty(index = 25, type = ProtobufType.MESSAGE)
    List<InteractiveAnnotation> annotations;

    @ProtobufProperty(index = 26, type = ProtobufType.STRING)
    String accessibilityLabel;

    @ProtobufProperty(index = 27, type = ProtobufType.MESSAGE)
    List<ProcessedVideo> processedVideos;

    @ProtobufProperty(index = 28, type = ProtobufType.UINT32)
    Integer externalShareFullVideoDurationInSeconds;

    @ProtobufProperty(index = 29, type = ProtobufType.UINT64)
    Long motionPhotoPresentationOffsetMs;

    @ProtobufProperty(index = 30, type = ProtobufType.STRING)
    String metadataUrl;

    @ProtobufProperty(index = 31, type = ProtobufType.ENUM)
    VideoSourceType videoSourceType;

    @ProtobufProperty(index = 32, type = ProtobufType.ENUM)
    MediaMessageKeyDomain mediaKeyDomain;


    VideoMessage(String mediaUrl, String mimetype, byte[] mediaSha256, Long mediaSize, Integer seconds, byte[] mediaKey, String caption, Boolean gifPlayback, Integer height, Integer width, byte[] mediaEncryptedSha256, List<InteractiveAnnotation> interactiveAnnotations, String mediaDirectPath, Instant mediaKeyTimestamp, byte[] jpegThumbnail, ContextInfo contextInfo, byte[] streamingSidecar, Attribution gifAttribution, Boolean viewOnce, String thumbnailDirectPath, byte[] thumbnailSha256, byte[] thumbnailEncSha256, String staticUrl, List<InteractiveAnnotation> annotations, String accessibilityLabel, List<ProcessedVideo> processedVideos, Integer externalShareFullVideoDurationInSeconds, Long motionPhotoPresentationOffsetMs, String metadataUrl, VideoSourceType videoSourceType, MediaMessageKeyDomain mediaKeyDomain) {
        this.mediaUrl = mediaUrl;
        this.mimetype = mimetype;
        this.mediaSha256 = mediaSha256;
        this.mediaSize = mediaSize;
        this.seconds = seconds;
        this.mediaKey = mediaKey;
        this.caption = caption;
        this.gifPlayback = gifPlayback;
        this.height = height;
        this.width = width;
        this.mediaEncryptedSha256 = mediaEncryptedSha256;
        this.interactiveAnnotations = interactiveAnnotations;
        this.mediaDirectPath = mediaDirectPath;
        this.mediaKeyTimestamp = mediaKeyTimestamp;
        this.jpegThumbnail = jpegThumbnail;
        this.contextInfo = contextInfo;
        this.streamingSidecar = streamingSidecar;
        this.gifAttribution = gifAttribution;
        this.viewOnce = viewOnce;
        this.thumbnailDirectPath = thumbnailDirectPath;
        this.thumbnailSha256 = thumbnailSha256;
        this.thumbnailEncSha256 = thumbnailEncSha256;
        this.staticUrl = staticUrl;
        this.annotations = annotations;
        this.accessibilityLabel = accessibilityLabel;
        this.processedVideos = processedVideos;
        this.externalShareFullVideoDurationInSeconds = externalShareFullVideoDurationInSeconds;
        this.motionPhotoPresentationOffsetMs = motionPhotoPresentationOffsetMs;
        this.metadataUrl = metadataUrl;
        this.videoSourceType = videoSourceType;
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

    public OptionalInt seconds() {
        return seconds == null ? OptionalInt.empty() : OptionalInt.of(seconds);
    }

    public Optional<byte[]> mediaKey() {
        return Optional.ofNullable(mediaKey);
    }

    public Optional<String> caption() {
        return Optional.ofNullable(caption);
    }

    public boolean gifPlayback() {
        return gifPlayback != null && gifPlayback;
    }

    public OptionalInt height() {
        return height == null ? OptionalInt.empty() : OptionalInt.of(height);
    }

    public OptionalInt width() {
        return width == null ? OptionalInt.empty() : OptionalInt.of(width);
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

    public Optional<byte[]> streamingSidecar() {
        return Optional.ofNullable(streamingSidecar);
    }

    public Optional<Attribution> gifAttribution() {
        return Optional.ofNullable(gifAttribution);
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

    public Optional<String> accessibilityLabel() {
        return Optional.ofNullable(accessibilityLabel);
    }

    public List<ProcessedVideo> processedVideos() {
        return processedVideos == null ? List.of() : Collections.unmodifiableList(processedVideos);
    }

    public OptionalInt externalShareFullVideoDurationInSeconds() {
        return externalShareFullVideoDurationInSeconds == null ? OptionalInt.empty() : OptionalInt.of(externalShareFullVideoDurationInSeconds);
    }

    public OptionalLong motionPhotoPresentationOffsetMs() {
        return motionPhotoPresentationOffsetMs == null ? OptionalLong.empty() : OptionalLong.of(motionPhotoPresentationOffsetMs);
    }

    public Optional<String> metadataUrl() {
        return Optional.ofNullable(metadataUrl);
    }

    public Optional<VideoSourceType> videoSourceType() {
        return Optional.ofNullable(videoSourceType);
    }

    public Optional<MediaMessageKeyDomain> mediaKeyDomain() {
        return Optional.ofNullable(mediaKeyDomain);
    }

    @Override
    public MediaPath mediaPath() {
        return MediaPath.VIDEO;
    }

    @Override
    public void setMediaUrl(String mediaUrl) {
        this.mediaUrl = mediaUrl;
    }

    public void setMimetype(String mimetype) {
        this.mimetype = mimetype;
    }

    @Override
    public void setMediaSha256(byte[] mediaSha256) {
        this.mediaSha256 = mediaSha256;
    }

    @Override
    public void setMediaSize(long mediaSize) {
        this.mediaSize = mediaSize;
    }

    public void setSeconds(Integer seconds) {
        this.seconds = seconds;
    }

    @Override
    public void setMediaKey(byte[] mediaKey) {
        this.mediaKey = mediaKey;
    }

    public void setCaption(String caption) {
        this.caption = caption;
    }

    public void setGifPlayback(Boolean gifPlayback) {
        this.gifPlayback = gifPlayback;
    }

    public void setHeight(Integer height) {
        this.height = height;
    }

    public void setWidth(Integer width) {
        this.width = width;
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

    public void setStreamingSidecar(byte[] streamingSidecar) {
        this.streamingSidecar = streamingSidecar;
    }

    public void setGifAttribution(Attribution gifAttribution) {
        this.gifAttribution = gifAttribution;
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

    public void setAccessibilityLabel(String accessibilityLabel) {
        this.accessibilityLabel = accessibilityLabel;
    }

    public void setProcessedVideos(List<ProcessedVideo> processedVideos) {
        this.processedVideos = processedVideos;
    }

    public void setExternalShareFullVideoDurationInSeconds(Integer externalShareFullVideoDurationInSeconds) {
        this.externalShareFullVideoDurationInSeconds = externalShareFullVideoDurationInSeconds;
    }

    public void setMotionPhotoPresentationOffsetMs(Long motionPhotoPresentationOffsetMs) {
        this.motionPhotoPresentationOffsetMs = motionPhotoPresentationOffsetMs;
    }

    public void setMetadataUrl(String metadataUrl) {
        this.metadataUrl = metadataUrl;
    }

    public void setVideoSourceType(VideoSourceType videoSourceType) {
        this.videoSourceType = videoSourceType;
    }

    public void setMediaKeyDomain(MediaMessageKeyDomain mediaKeyDomain) {
        this.mediaKeyDomain = mediaKeyDomain;
    }

    @ProtobufEnum(name = "Message.VideoMessage.Attribution")
    public static enum Attribution {
        NONE(0),
        GIPHY(1),
        TENOR(2),
        KLIPY(3);

        Attribution(@ProtobufEnumIndex int index) {
            this.index = index;
        }

        final int index;

        public int index() {
            return this.index;
        }
    }

    @ProtobufEnum(name = "Message.VideoMessage.VideoSourceType")
    public static enum VideoSourceType {
        USER_VIDEO(0),
        AI_GENERATED(1);

        VideoSourceType(@ProtobufEnumIndex int index) {
            this.index = index;
        }

        final int index;

        public int index() {
            return this.index;
        }
    }
}
