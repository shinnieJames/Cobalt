package com.github.auties00.cobalt.model.message.media;

import com.github.auties00.cobalt.model.message.ContextInfo;
import com.github.auties00.cobalt.model.message.MediaMessage;
import com.github.auties00.cobalt.model.message.util.MediaKeyDomain;

import java.time.Instant;
import it.auties.protobuf.annotation.*;
import it.auties.protobuf.model.*;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;

@ProtobufMessage(name = "Message.AudioMessage")
public final class AudioMessage implements InteractiveMessage.Media, MediaMessage {
    @ProtobufProperty(index = 1, type = ProtobufType.STRING)
    String url;

    @ProtobufProperty(index = 2, type = ProtobufType.STRING)
    String mimetype;

    @ProtobufProperty(index = 3, type = ProtobufType.BYTES)
    byte[] fileSha256;

    @ProtobufProperty(index = 4, type = ProtobufType.UINT64)
    Long fileLength;

    @ProtobufProperty(index = 5, type = ProtobufType.UINT32)
    Integer seconds;

    @ProtobufProperty(index = 6, type = ProtobufType.BOOL)
    Boolean ptt;

    @ProtobufProperty(index = 7, type = ProtobufType.BYTES)
    byte[] mediaKey;

    @ProtobufProperty(index = 8, type = ProtobufType.BYTES)
    byte[] fileEncSha256;

    @ProtobufProperty(index = 9, type = ProtobufType.STRING)
    String directPath;

    @ProtobufProperty(index = 10, type = ProtobufType.INT64, mixins = InstantProtobufMixin.class)
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
    MediaKeyDomain mediaKeyDomain;


    AudioMessage(String url, String mimetype, byte[] fileSha256, Long fileLength, Integer seconds, Boolean ptt, byte[] mediaKey, byte[] fileEncSha256, String directPath, Instant mediaKeyTimestamp, ContextInfo contextInfo, byte[] streamingSidecar, byte[] waveform, Integer backgroundArgb, Boolean viewOnce, String accessibilityLabel, MediaKeyDomain mediaKeyDomain) {
        this.url = url;
        this.mimetype = mimetype;
        this.fileSha256 = fileSha256;
        this.fileLength = fileLength;
        this.seconds = seconds;
        this.ptt = ptt;
        this.mediaKey = mediaKey;
        this.fileEncSha256 = fileEncSha256;
        this.directPath = directPath;
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
        return Optional.ofNullable(url);
    }

    public Optional<String> mimetype() {
        return Optional.ofNullable(mimetype);
    }

    public Optional<byte[]> fileSha256() {
        return Optional.ofNullable(fileSha256);
    }

    public OptionalLong fileLength() {
        return fileLength == null ? OptionalLong.empty() : OptionalLong.of(fileLength);
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
        return Optional.ofNullable(fileEncSha256);
    }

    public Optional<String> directPath() {
        return Optional.ofNullable(directPath);
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

    public Optional<MediaKeyDomain> mediaKeyDomain() {
        return Optional.ofNullable(mediaKeyDomain);
    }

    public AudioMessage setUrl(String url) {
        this.url = url;
        return this;
    }

    public AudioMessage setMimetype(String mimetype) {
        this.mimetype = mimetype;
        return this;
    }

    public AudioMessage setFileSha256(byte[] fileSha256) {
        this.fileSha256 = fileSha256;
        return this;
    }

    public AudioMessage setFileLength(Long fileLength) {
        this.fileLength = fileLength;
        return this;
    }

    public AudioMessage setSeconds(Integer seconds) {
        this.seconds = seconds;
        return this;
    }

    public AudioMessage setPtt(Boolean ptt) {
        this.ptt = ptt;
        return this;
    }

    public AudioMessage setMediaKey(byte[] mediaKey) {
        this.mediaKey = mediaKey;
        return this;
    }

    public AudioMessage setFileEncSha256(byte[] fileEncSha256) {
        this.fileEncSha256 = fileEncSha256;
        return this;
    }

    public AudioMessage setDirectPath(String directPath) {
        this.directPath = directPath;
        return this;
    }

    public AudioMessage setMediaKeyTimestamp(Instant mediaKeyTimestamp) {
        this.mediaKeyTimestamp = mediaKeyTimestamp;
        return this;
    }

    public AudioMessage setContextInfo(ContextInfo contextInfo) {
        this.contextInfo = contextInfo;
        return this;
    }

    public AudioMessage setStreamingSidecar(byte[] streamingSidecar) {
        this.streamingSidecar = streamingSidecar;
        return this;
    }

    public AudioMessage setWaveform(byte[] waveform) {
        this.waveform = waveform;
        return this;
    }

    public AudioMessage setBackgroundArgb(Integer backgroundArgb) {
        this.backgroundArgb = backgroundArgb;
        return this;
    }

    public AudioMessage setViewOnce(Boolean viewOnce) {
        this.viewOnce = viewOnce;
        return this;
    }

    public AudioMessage setAccessibilityLabel(String accessibilityLabel) {
        this.accessibilityLabel = accessibilityLabel;
        return this;
    }

    public AudioMessage setMediaKeyDomain(MediaKeyDomain mediaKeyDomain) {
        this.mediaKeyDomain = mediaKeyDomain;
        return this;
    }
}
