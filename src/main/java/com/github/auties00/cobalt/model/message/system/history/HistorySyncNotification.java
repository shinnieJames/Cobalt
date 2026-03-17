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
    byte[] mediaSha256;

    @ProtobufProperty(index = 2, type = ProtobufType.UINT64)
    Long mediaSize;

    @ProtobufProperty(index = 3, type = ProtobufType.BYTES)
    byte[] mediaKey;

    @ProtobufProperty(index = 4, type = ProtobufType.BYTES)
    byte[] mediaEncryptedSha256;

    @ProtobufProperty(index = 5, type = ProtobufType.STRING)
    String mediaDirectPath;

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


    HistorySyncNotification(byte[] mediaSha256, Long mediaSize, byte[] mediaKey, byte[] mediaEncryptedSha256, String mediaDirectPath, HistorySyncType syncType, Integer chunkOrder, String originalMessageId, Integer progress, Instant oldestMsgInChunkTimestampSec, byte[] initialHistBootstrapInlinePayload, String peerDataRequestSessionId, FullHistorySyncOnDemandRequestMetadata fullHistorySyncOnDemandRequestMetadata, String encHandle, HistorySyncMessageAccessStatus messageAccessStatus) {
        this.mediaSha256 = mediaSha256;
        this.mediaSize = mediaSize;
        this.mediaKey = mediaKey;
        this.mediaEncryptedSha256 = mediaEncryptedSha256;
        this.mediaDirectPath = mediaDirectPath;
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

    @Override
    public Optional<String> mediaUrl() {
        return Optional.empty();
    }

    @Override
    public MediaPath mediaPath() {
        return MediaPath.HISTORY_SYNC;
    }

    @Override
    public void setMediaSha256(byte[] mediaSha256) {
        this.mediaSha256 = mediaSha256;
    }

    @Override
    public void setMediaSize(long mediaSize) {
        this.mediaSize = mediaSize;
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

    public void setSyncType(HistorySyncType syncType) {
        this.syncType = syncType;
    }

    public void setChunkOrder(Integer chunkOrder) {
        this.chunkOrder = chunkOrder;
    }

    public void setOriginalMessageId(String originalMessageId) {
        this.originalMessageId = originalMessageId;
    }

    public void setProgress(Integer progress) {
        this.progress = progress;
    }

    public void setOldestMsgInChunkTimestampSec(Instant oldestMsgInChunkTimestampSec) {
        this.oldestMsgInChunkTimestampSec = oldestMsgInChunkTimestampSec;
    }

    public void setInitialHistBootstrapInlinePayload(byte[] initialHistBootstrapInlinePayload) {
        this.initialHistBootstrapInlinePayload = initialHistBootstrapInlinePayload;
    }

    public void setPeerDataRequestSessionId(String peerDataRequestSessionId) {
        this.peerDataRequestSessionId = peerDataRequestSessionId;
    }

    public void setFullHistorySyncOnDemandRequestMetadata(FullHistorySyncOnDemandRequestMetadata fullHistorySyncOnDemandRequestMetadata) {
        this.fullHistorySyncOnDemandRequestMetadata = fullHistorySyncOnDemandRequestMetadata;
    }

    public void setEncHandle(String encHandle) {
        this.encHandle = encHandle;
    }

    public void setMessageAccessStatus(HistorySyncMessageAccessStatus messageAccessStatus) {
        this.messageAccessStatus = messageAccessStatus;
    }

    @Override
    public void setMediaUrl(String mediaUrl) {
    }

    @Override
    public void setMediaKeyTimestamp(Instant timestamp) {
    }
}
