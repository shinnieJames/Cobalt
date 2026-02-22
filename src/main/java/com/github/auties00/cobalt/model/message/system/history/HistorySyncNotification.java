package com.github.auties00.cobalt.model.message.system.history;

import com.github.auties00.cobalt.model.media.MediaPath;
import com.github.auties00.cobalt.model.media.MediaProvider;
import com.github.auties00.cobalt.model.message.Message;
import com.github.auties00.cobalt.model.mixin.InstantSecondsMixin;
import it.auties.protobuf.annotation.ProtobufMessage;
import it.auties.protobuf.annotation.ProtobufProperty;
import it.auties.protobuf.model.ProtobufType;

import java.time.Instant;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;

@ProtobufMessage(name = "Message.HistorySyncNotification")
public final class HistorySyncNotification implements Message, MediaProvider {
    @ProtobufProperty(index = 1, type = ProtobufType.BYTES)
    byte[] fileSha256;

    @ProtobufProperty(index = 2, type = ProtobufType.UINT64)
    Long fileLength;

    @ProtobufProperty(index = 3, type = ProtobufType.BYTES)
    byte[] mediaKey;

    @ProtobufProperty(index = 4, type = ProtobufType.BYTES)
    byte[] fileEncSha256;

    @ProtobufProperty(index = 5, type = ProtobufType.STRING)
    String directPath;

    @ProtobufProperty(index = 6, type = ProtobufType.ENUM)
    HistorySyncType syncType;

    @ProtobufProperty(index = 7, type = ProtobufType.UINT32)
    Integer chunkOrder;

    @ProtobufProperty(index = 8, type = ProtobufType.STRING)
    String originalMessageId;

    @ProtobufProperty(index = 9, type = ProtobufType.UINT32)
    Integer progress;

    @ProtobufProperty(index = 10, type = ProtobufType.INT64, mixins = InstantSecondsMixin.class)
    Instant oldestMsgInChunkTimestampSec;

    @ProtobufProperty(index = 11, type = ProtobufType.BYTES)
    byte[] initialHistBootstrapInlinePayload;

    @ProtobufProperty(index = 12, type = ProtobufType.STRING)
    String peerDataRequestSessionId;

    @ProtobufProperty(index = 13, type = ProtobufType.MESSAGE)
    FullHistorySyncOnDemandRequestMetadata fullHistorySyncOnDemandRequestMetadata;

    @ProtobufProperty(index = 14, type = ProtobufType.STRING)
    String encHandle;

    @ProtobufProperty(index = 15, type = ProtobufType.MESSAGE)
    HistorySyncMessageAccessStatus messageAccessStatus;


    HistorySyncNotification(byte[] fileSha256, Long fileLength, byte[] mediaKey, byte[] fileEncSha256, String directPath, HistorySyncType syncType, Integer chunkOrder, String originalMessageId, Integer progress, Instant oldestMsgInChunkTimestampSec, byte[] initialHistBootstrapInlinePayload, String peerDataRequestSessionId, FullHistorySyncOnDemandRequestMetadata fullHistorySyncOnDemandRequestMetadata, String encHandle, HistorySyncMessageAccessStatus messageAccessStatus) {
        this.fileSha256 = fileSha256;
        this.fileLength = fileLength;
        this.mediaKey = mediaKey;
        this.fileEncSha256 = fileEncSha256;
        this.directPath = directPath;
        this.syncType = syncType;
        this.chunkOrder = chunkOrder;
        this.originalMessageId = originalMessageId;
        this.progress = progress;
        this.oldestMsgInChunkTimestampSec = oldestMsgInChunkTimestampSec;
        this.initialHistBootstrapInlinePayload = initialHistBootstrapInlinePayload;
        this.peerDataRequestSessionId = peerDataRequestSessionId;
        this.fullHistorySyncOnDemandRequestMetadata = fullHistorySyncOnDemandRequestMetadata;
        this.encHandle = encHandle;
        this.messageAccessStatus = messageAccessStatus;
    }

    public Optional<byte[]> fileSha256() {
        return Optional.ofNullable(fileSha256);
    }

    public OptionalLong fileLength() {
        return fileLength == null ? OptionalLong.empty() : OptionalLong.of(fileLength);
    }

    @Override
    public Optional<String> mediaUrl() {
        return Optional.empty();
    }

    @Override
    public void setMediaUrl(String mediaUrl) {

    }

    @Override
    public Optional<String> mediaDirectPath() {
        return Optional.ofNullable(directPath);
    }

    @Override
    public void setMediaDirectPath(String mediaDirectPath) {
        this.directPath = mediaDirectPath;
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

    public Optional<HistorySyncType> syncType() {
        return Optional.ofNullable(syncType);
    }

    public OptionalInt chunkOrder() {
        return chunkOrder == null ? OptionalInt.empty() : OptionalInt.of(chunkOrder);
    }

    public Optional<String> originalMessageId() {
        return Optional.ofNullable(originalMessageId);
    }

    public OptionalInt progress() {
        return progress == null ? OptionalInt.empty() : OptionalInt.of(progress);
    }

    public Optional<Instant> oldestMsgInChunkTimestampSec() {
        return Optional.ofNullable(oldestMsgInChunkTimestampSec);
    }

    public Optional<byte[]> initialHistBootstrapInlinePayload() {
        return Optional.ofNullable(initialHistBootstrapInlinePayload);
    }

    public Optional<String> peerDataRequestSessionId() {
        return Optional.ofNullable(peerDataRequestSessionId);
    }

    public Optional<FullHistorySyncOnDemandRequestMetadata> fullHistorySyncOnDemandRequestMetadata() {
        return Optional.ofNullable(fullHistorySyncOnDemandRequestMetadata);
    }

    public Optional<String> encHandle() {
        return Optional.ofNullable(encHandle);
    }

    public Optional<HistorySyncMessageAccessStatus> messageAccessStatus() {
        return Optional.ofNullable(messageAccessStatus);
    }

    public HistorySyncNotification setFileSha256(byte[] fileSha256) {
        this.fileSha256 = fileSha256;
        return this;
    }

    public HistorySyncNotification setFileLength(Long fileLength) {
        this.fileLength = fileLength;
        return this;
    }

    @Override
    public void setMediaKey(byte[] mediaKey) {
        this.mediaKey = mediaKey;
    }

    @Override
    public void setMediaKeyTimestamp(Long timestamp) {

    }

    @Override
    public Optional<byte[]> mediaSha256() {
        return Optional.ofNullable(fileSha256);
    }

    @Override
    public void setMediaSha256(byte[] bytes) {
        this.fileSha256 = bytes;
    }

    @Override
    public Optional<byte[]> mediaEncryptedSha256() {
        return Optional.ofNullable(fileEncSha256);
    }

    @Override
    public void setMediaEncryptedSha256(byte[] bytes) {
        this.fileEncSha256 = bytes;
    }

    @Override
    public OptionalLong mediaSize() {
        return fileLength == null ? OptionalLong.empty() : OptionalLong.of(fileLength);
    }

    @Override
    public void setMediaSize(long mediaSize) {
        this.fileLength = mediaSize;
    }

    @Override
    public MediaPath mediaPath() {
        return MediaPath.HISTORY_SYNC;
    }

    public HistorySyncNotification setFileEncSha256(byte[] fileEncSha256) {
        this.fileEncSha256 = fileEncSha256;
        return this;
    }

    public HistorySyncNotification setDirectPath(String directPath) {
        this.directPath = directPath;
        return this;
    }

    public HistorySyncNotification setSyncType(HistorySyncType syncType) {
        this.syncType = syncType;
        return this;
    }

    public HistorySyncNotification setChunkOrder(Integer chunkOrder) {
        this.chunkOrder = chunkOrder;
        return this;
    }

    public HistorySyncNotification setOriginalMessageId(String originalMessageId) {
        this.originalMessageId = originalMessageId;
        return this;
    }

    public HistorySyncNotification setProgress(Integer progress) {
        this.progress = progress;
        return this;
    }

    public HistorySyncNotification setOldestMsgInChunkTimestampSec(Instant oldestMsgInChunkTimestampSec) {
        this.oldestMsgInChunkTimestampSec = oldestMsgInChunkTimestampSec;
        return this;
    }

    public HistorySyncNotification setInitialHistBootstrapInlinePayload(byte[] initialHistBootstrapInlinePayload) {
        this.initialHistBootstrapInlinePayload = initialHistBootstrapInlinePayload;
        return this;
    }

    public HistorySyncNotification setPeerDataRequestSessionId(String peerDataRequestSessionId) {
        this.peerDataRequestSessionId = peerDataRequestSessionId;
        return this;
    }

    public HistorySyncNotification setFullHistorySyncOnDemandRequestMetadata(FullHistorySyncOnDemandRequestMetadata fullHistorySyncOnDemandRequestMetadata) {
        this.fullHistorySyncOnDemandRequestMetadata = fullHistorySyncOnDemandRequestMetadata;
        return this;
    }

    public HistorySyncNotification setEncHandle(String encHandle) {
        this.encHandle = encHandle;
        return this;
    }

    public HistorySyncNotification setMessageAccessStatus(HistorySyncMessageAccessStatus messageAccessStatus) {
        this.messageAccessStatus = messageAccessStatus;
        return this;
    }
}
