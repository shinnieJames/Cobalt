package com.github.auties00.cobalt.model.message.media;

import com.github.auties00.cobalt.model.media.MediaPath;
import com.github.auties00.cobalt.model.message.context.ContextInfo;
import com.github.auties00.cobalt.model.message.interactive.InteractiveMessage;
import com.github.auties00.cobalt.model.mixin.InstantSecondsMixin;
import it.auties.protobuf.annotation.ProtobufMessage;
import it.auties.protobuf.annotation.ProtobufProperty;
import it.auties.protobuf.model.ProtobufType;

import java.time.Instant;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;

@ProtobufMessage(name = "Message.AudioMessage")
public final class AudioMessage implements InteractiveMessage.Media, MediaMessage {
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

    @ProtobufProperty(index = 6, type = ProtobufType.BOOL)
    Boolean ptt;

    @ProtobufProperty(index = 7, type = ProtobufType.BYTES)
    byte[] mediaKey;

    @ProtobufProperty(index = 8, type = ProtobufType.BYTES)
    byte[] mediaEncryptedSha256;

    @ProtobufProperty(index = 9, type = ProtobufType.STRING)
    String mediaDirectPath;

    @ProtobufProperty(index = 10, type = ProtobufType.INT64, mixins = InstantSecondsMixin.class)
    Instant mediaKeyTimestamp;

    @ProtobufProperty(index = 17, type = ProtobufType.MESSAGE)
    ContextInfo contextInfo;

    @ProtobufProperty(index = 18, type = ProtobufType.BYTES)
    byte[] streamingSidecar;

    @ProtobufProperty(index = 19, type = ProtobufType.BYTES)
    byte[] waveform;

    @ProtobufProperty(index = 20, type = ProtobufType.FIXED32)
    Integer backgroundArgb;

    @ProtobufProperty(index = 21, type = ProtobufType.BOOL)
    Boolean viewOnce;

    @ProtobufProperty(index = 22, type = ProtobufType.STRING)
    String accessibilityLabel;

    @ProtobufProperty(index = 23, type = ProtobufType.ENUM)
    MediaMessageKeyDomain mediaKeyDomain;


    AudioMessage(String mediaUrl, String mimetype, byte[] mediaSha256, Long mediaSize, Integer seconds, Boolean ptt, byte[] mediaKey, byte[] mediaEncryptedSha256, String mediaDirectPath, Instant mediaKeyTimestamp, ContextInfo contextInfo, byte[] streamingSidecar, byte[] waveform, Integer backgroundArgb, Boolean viewOnce, String accessibilityLabel, MediaMessageKeyDomain mediaKeyDomain) {
        this.mediaUrl = mediaUrl;
        this.mimetype = mimetype;
        this.mediaSha256 = mediaSha256;
        this.mediaSize = mediaSize;
        this.seconds = seconds;
        this.ptt = ptt;
        this.mediaKey = mediaKey;
        this.mediaEncryptedSha256 = mediaEncryptedSha256;
        this.mediaDirectPath = mediaDirectPath;
        this.mediaKeyTimestamp = mediaKeyTimestamp;
        this.contextInfo = contextInfo;
        this.streamingSidecar = streamingSidecar;
        this.waveform = waveform;
        this.backgroundArgb = backgroundArgb;
        this.viewOnce = viewOnce;
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

    public boolean ptt() {
        return ptt != null && ptt;
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

    public Optional<ContextInfo> contextInfo() {
        return Optional.ofNullable(contextInfo);
    }

    public Optional<byte[]> streamingSidecar() {
        return Optional.ofNullable(streamingSidecar);
    }

    public Optional<byte[]> waveform() {
        return Optional.ofNullable(waveform);
    }

    public OptionalInt backgroundArgb() {
        return backgroundArgb == null ? OptionalInt.empty() : OptionalInt.of(backgroundArgb);
    }

    public boolean viewOnce() {
        return viewOnce != null && viewOnce;
    }

    public Optional<String> accessibilityLabel() {
        return Optional.ofNullable(accessibilityLabel);
    }

    public Optional<MediaMessageKeyDomain> mediaKeyDomain() {
        return Optional.ofNullable(mediaKeyDomain);
    }

    @Override
    public MediaPath mediaPath() {
        return MediaPath.AUDIO;
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

    public void setPtt(Boolean ptt) {
        this.ptt = ptt;
    }

    @Override
    public void setMediaKey(byte[] mediaKey) {
        this.mediaKey = mediaKey;
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

    public void setContextInfo(ContextInfo contextInfo) {
        this.contextInfo = contextInfo;
    }

    public void setStreamingSidecar(byte[] streamingSidecar) {
        this.streamingSidecar = streamingSidecar;
    }

    public void setWaveform(byte[] waveform) {
        this.waveform = waveform;
    }

    public void setBackgroundArgb(Integer backgroundArgb) {
        this.backgroundArgb = backgroundArgb;
    }

    public void setViewOnce(Boolean viewOnce) {
        this.viewOnce = viewOnce;
    }

    public void setAccessibilityLabel(String accessibilityLabel) {
        this.accessibilityLabel = accessibilityLabel;
    }

    public void setMediaKeyDomain(MediaMessageKeyDomain mediaKeyDomain) {
        this.mediaKeyDomain = mediaKeyDomain;
    }
}
