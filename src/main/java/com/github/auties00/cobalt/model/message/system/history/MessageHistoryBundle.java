package com.github.auties00.cobalt.model.message.system.history;

import com.github.auties00.cobalt.model.message.context.ContextInfo;
import com.github.auties00.cobalt.model.message.context.ContextualMessage;
import com.github.auties00.cobalt.model.mixin.InstantSecondsMixin;
import it.auties.protobuf.annotation.ProtobufMessage;
import it.auties.protobuf.annotation.ProtobufProperty;
import it.auties.protobuf.model.ProtobufType;

import java.time.Instant;
import java.util.Optional;

@ProtobufMessage(name = "Message.MessageHistoryBundle")
public final class MessageHistoryBundle implements ContextualMessage {
    @ProtobufProperty(index = 1, type = ProtobufType.STRING)
    String mimetype;

    @ProtobufProperty(index = 2, type = ProtobufType.BYTES)
    byte[] fileSha256;

    @ProtobufProperty(index = 3, type = ProtobufType.BYTES)
    byte[] mediaKey;

    @ProtobufProperty(index = 4, type = ProtobufType.BYTES)
    byte[] fileEncSha256;

    @ProtobufProperty(index = 5, type = ProtobufType.STRING)
    String directPath;

    @ProtobufProperty(index = 6, type = ProtobufType.INT64, mixins = InstantSecondsMixin.class)
    Instant mediaKeyTimestamp;

    @ProtobufProperty(index = 7, type = ProtobufType.MESSAGE)
    ContextInfo contextInfo;

    @ProtobufProperty(index = 8, type = ProtobufType.MESSAGE)
    MessageHistoryMetadata messageHistoryMetadata;


    MessageHistoryBundle(String mimetype, byte[] fileSha256, byte[] mediaKey, byte[] fileEncSha256, String directPath, Instant mediaKeyTimestamp, ContextInfo contextInfo, MessageHistoryMetadata messageHistoryMetadata) {
        this.mimetype = mimetype;
        this.fileSha256 = fileSha256;
        this.mediaKey = mediaKey;
        this.fileEncSha256 = fileEncSha256;
        this.directPath = directPath;
        this.mediaKeyTimestamp = mediaKeyTimestamp;
        this.contextInfo = contextInfo;
        this.messageHistoryMetadata = messageHistoryMetadata;
    }

    public Optional<String> mimetype() {
        return Optional.ofNullable(mimetype);
    }

    public Optional<byte[]> fileSha256() {
        return Optional.ofNullable(fileSha256);
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

    public Optional<MessageHistoryMetadata> messageHistoryMetadata() {
        return Optional.ofNullable(messageHistoryMetadata);
    }

    public MessageHistoryBundle setMimetype(String mimetype) {
        this.mimetype = mimetype;
        return this;
    }

    public MessageHistoryBundle setFileSha256(byte[] fileSha256) {
        this.fileSha256 = fileSha256;
        return this;
    }

    public MessageHistoryBundle setMediaKey(byte[] mediaKey) {
        this.mediaKey = mediaKey;
        return this;
    }

    public MessageHistoryBundle setFileEncSha256(byte[] fileEncSha256) {
        this.fileEncSha256 = fileEncSha256;
        return this;
    }

    public MessageHistoryBundle setDirectPath(String directPath) {
        this.directPath = directPath;
        return this;
    }

    public MessageHistoryBundle setMediaKeyTimestamp(Instant mediaKeyTimestamp) {
        this.mediaKeyTimestamp = mediaKeyTimestamp;
        return this;
    }

    public MessageHistoryBundle setContextInfo(ContextInfo contextInfo) {
        this.contextInfo = contextInfo;
        return this;
    }

    public MessageHistoryBundle setMessageHistoryMetadata(MessageHistoryMetadata messageHistoryMetadata) {
        this.messageHistoryMetadata = messageHistoryMetadata;
        return this;
    }
}
