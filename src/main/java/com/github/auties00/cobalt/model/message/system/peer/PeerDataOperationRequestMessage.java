package com.github.auties00.cobalt.model.message.system.peer;

import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.message.MessageKey;
import com.github.auties00.cobalt.model.device.DeviceProps;
import com.github.auties00.cobalt.model.message.Message;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import it.auties.protobuf.annotation.*;
import it.auties.protobuf.model.*;
import java.util.Optional;
import java.util.OptionalInt;

@ProtobufMessage(name = "Message.PeerDataOperationRequestMessage")
public final class PeerDataOperationRequestMessage implements Message {
    @ProtobufProperty(index = 1, type = ProtobufType.ENUM)
    PeerDataOperationRequestType peerDataOperationRequestType;

    @ProtobufProperty(index = 2, type = ProtobufType.MESSAGE)
    List<RequestStickerReupload> requestStickerReupload;

    @ProtobufProperty(index = 3, type = ProtobufType.MESSAGE)
    List<RequestUrlPreview> requestUrlPreview;

    @ProtobufProperty(index = 4, type = ProtobufType.MESSAGE)
    HistorySyncOnDemandRequest historySyncOnDemandRequest;

    @ProtobufProperty(index = 5, type = ProtobufType.MESSAGE)
    List<PlaceholderMessageResendRequest> placeholderMessageResendRequest;

    @ProtobufProperty(index = 6, type = ProtobufType.MESSAGE)
    FullHistorySyncOnDemandRequest fullHistorySyncOnDemandRequest;

    @ProtobufProperty(index = 7, type = ProtobufType.MESSAGE)
    SyncDCollectionFatalRecoveryRequest syncdCollectionFatalRecoveryRequest;

    @ProtobufProperty(index = 8, type = ProtobufType.MESSAGE)
    HistorySyncChunkRetryRequest historySyncChunkRetryRequest;

    @ProtobufProperty(index = 9, type = ProtobufType.MESSAGE)
    GalaxyFlowAction galaxyFlowAction;


    PeerDataOperationRequestMessage(PeerDataOperationRequestType peerDataOperationRequestType, List<RequestStickerReupload> requestStickerReupload, List<RequestUrlPreview> requestUrlPreview, HistorySyncOnDemandRequest historySyncOnDemandRequest, List<PlaceholderMessageResendRequest> placeholderMessageResendRequest, FullHistorySyncOnDemandRequest fullHistorySyncOnDemandRequest, SyncDCollectionFatalRecoveryRequest syncdCollectionFatalRecoveryRequest, HistorySyncChunkRetryRequest historySyncChunkRetryRequest, GalaxyFlowAction galaxyFlowAction) {
        this.peerDataOperationRequestType = peerDataOperationRequestType;
        this.requestStickerReupload = requestStickerReupload;
        this.requestUrlPreview = requestUrlPreview;
        this.historySyncOnDemandRequest = historySyncOnDemandRequest;
        this.placeholderMessageResendRequest = placeholderMessageResendRequest;
        this.fullHistorySyncOnDemandRequest = fullHistorySyncOnDemandRequest;
        this.syncdCollectionFatalRecoveryRequest = syncdCollectionFatalRecoveryRequest;
        this.historySyncChunkRetryRequest = historySyncChunkRetryRequest;
        this.galaxyFlowAction = galaxyFlowAction;
    }

    public Optional<PeerDataOperationRequestType> peerDataOperationRequestType() {
        return Optional.ofNullable(peerDataOperationRequestType);
    }

    public List<RequestStickerReupload> requestStickerReupload() {
        return requestStickerReupload == null ? List.of() : Collections.unmodifiableList(requestStickerReupload);
    }

    public List<RequestUrlPreview> requestUrlPreview() {
        return requestUrlPreview == null ? List.of() : Collections.unmodifiableList(requestUrlPreview);
    }

    public Optional<HistorySyncOnDemandRequest> historySyncOnDemandRequest() {
        return Optional.ofNullable(historySyncOnDemandRequest);
    }

    public List<PlaceholderMessageResendRequest> placeholderMessageResendRequest() {
        return placeholderMessageResendRequest == null ? List.of() : Collections.unmodifiableList(placeholderMessageResendRequest);
    }

    public Optional<FullHistorySyncOnDemandRequest> fullHistorySyncOnDemandRequest() {
        return Optional.ofNullable(fullHistorySyncOnDemandRequest);
    }

    public Optional<SyncDCollectionFatalRecoveryRequest> syncdCollectionFatalRecoveryRequest() {
        return Optional.ofNullable(syncdCollectionFatalRecoveryRequest);
    }

    public Optional<HistorySyncChunkRetryRequest> historySyncChunkRetryRequest() {
        return Optional.ofNullable(historySyncChunkRetryRequest);
    }

    public Optional<GalaxyFlowAction> galaxyFlowAction() {
        return Optional.ofNullable(galaxyFlowAction);
    }

    public PeerDataOperationRequestMessage setPeerDataOperationRequestType(PeerDataOperationRequestType peerDataOperationRequestType) {
        this.peerDataOperationRequestType = peerDataOperationRequestType;
        return this;
    }

    public PeerDataOperationRequestMessage setRequestStickerReupload(List<RequestStickerReupload> requestStickerReupload) {
        this.requestStickerReupload = requestStickerReupload;
        return this;
    }

    public PeerDataOperationRequestMessage setRequestUrlPreview(List<RequestUrlPreview> requestUrlPreview) {
        this.requestUrlPreview = requestUrlPreview;
        return this;
    }

    public PeerDataOperationRequestMessage setHistorySyncOnDemandRequest(HistorySyncOnDemandRequest historySyncOnDemandRequest) {
        this.historySyncOnDemandRequest = historySyncOnDemandRequest;
        return this;
    }

    public PeerDataOperationRequestMessage setPlaceholderMessageResendRequest(List<PlaceholderMessageResendRequest> placeholderMessageResendRequest) {
        this.placeholderMessageResendRequest = placeholderMessageResendRequest;
        return this;
    }

    public PeerDataOperationRequestMessage setFullHistorySyncOnDemandRequest(FullHistorySyncOnDemandRequest fullHistorySyncOnDemandRequest) {
        this.fullHistorySyncOnDemandRequest = fullHistorySyncOnDemandRequest;
        return this;
    }

    public PeerDataOperationRequestMessage setSyncdCollectionFatalRecoveryRequest(SyncDCollectionFatalRecoveryRequest syncdCollectionFatalRecoveryRequest) {
        this.syncdCollectionFatalRecoveryRequest = syncdCollectionFatalRecoveryRequest;
        return this;
    }

    public PeerDataOperationRequestMessage setHistorySyncChunkRetryRequest(HistorySyncChunkRetryRequest historySyncChunkRetryRequest) {
        this.historySyncChunkRetryRequest = historySyncChunkRetryRequest;
        return this;
    }

    public PeerDataOperationRequestMessage setGalaxyFlowAction(GalaxyFlowAction galaxyFlowAction) {
        this.galaxyFlowAction = galaxyFlowAction;
        return this;
    }

    @ProtobufMessage(name = "Message.PeerDataOperationRequestMessage.FullHistorySyncOnDemandRequest")
    public static final class FullHistorySyncOnDemandRequest {
        @ProtobufProperty(index = 1, type = ProtobufType.MESSAGE)
        FullHistorySyncOnDemandRequestMetadata requestMetadata;

        @ProtobufProperty(index = 2, type = ProtobufType.MESSAGE)
        DeviceProps.HistorySyncConfig historySyncConfig;


        FullHistorySyncOnDemandRequest(FullHistorySyncOnDemandRequestMetadata requestMetadata, DeviceProps.HistorySyncConfig historySyncConfig) {
            this.requestMetadata = requestMetadata;
            this.historySyncConfig = historySyncConfig;
        }

        public Optional<FullHistorySyncOnDemandRequestMetadata> requestMetadata() {
            return Optional.ofNullable(requestMetadata);
        }

        public Optional<DeviceProps.HistorySyncConfig> historySyncConfig() {
            return Optional.ofNullable(historySyncConfig);
        }

        public FullHistorySyncOnDemandRequest setRequestMetadata(FullHistorySyncOnDemandRequestMetadata requestMetadata) {
            this.requestMetadata = requestMetadata;
            return this;
        }

        public FullHistorySyncOnDemandRequest setHistorySyncConfig(DeviceProps.HistorySyncConfig historySyncConfig) {
            this.historySyncConfig = historySyncConfig;
            return this;
        }
    }

    @ProtobufMessage(name = "Message.PeerDataOperationRequestMessage.GalaxyFlowAction")
    public static final class GalaxyFlowAction {
        @ProtobufProperty(index = 1, type = ProtobufType.ENUM)
        GalaxyFlowAction.GalaxyFlowActionType type;

        @ProtobufProperty(index = 2, type = ProtobufType.STRING)
        String flowId;

        @ProtobufProperty(index = 3, type = ProtobufType.STRING)
        String stanzaId;


        GalaxyFlowAction(GalaxyFlowActionType type, String flowId, String stanzaId) {
            this.type = type;
            this.flowId = flowId;
            this.stanzaId = stanzaId;
        }

        public Optional<GalaxyFlowActionType> type() {
            return Optional.ofNullable(type);
        }

        public Optional<String> flowId() {
            return Optional.ofNullable(flowId);
        }

        public Optional<String> stanzaId() {
            return Optional.ofNullable(stanzaId);
        }

        public GalaxyFlowAction setType(GalaxyFlowActionType type) {
            this.type = type;
            return this;
        }

        public GalaxyFlowAction setFlowId(String flowId) {
            this.flowId = flowId;
            return this;
        }

        public GalaxyFlowAction setStanzaId(String stanzaId) {
            this.stanzaId = stanzaId;
            return this;
        }

        @ProtobufEnum(name = "Message.PeerDataOperationRequestMessage.GalaxyFlowAction.GalaxyFlowActionType")
        public static enum GalaxyFlowActionType {
            NOTIFY_LAUNCH(1);

            GalaxyFlowActionType(@ProtobufEnumIndex int index) {
                this.index = index;
            }

            final int index;

            public int index() {
                return this.index;
            }
        }
    }

    @ProtobufMessage(name = "Message.PeerDataOperationRequestMessage.HistorySyncChunkRetryRequest")
    public static final class HistorySyncChunkRetryRequest {
        @ProtobufProperty(index = 1, type = ProtobufType.ENUM)
        HistorySyncType syncType;

        @ProtobufProperty(index = 2, type = ProtobufType.UINT32)
        Integer chunkOrder;

        @ProtobufProperty(index = 3, type = ProtobufType.STRING)
        String chunkNotificationId;

        @ProtobufProperty(index = 4, type = ProtobufType.BOOL)
        Boolean regenerateChunk;


        HistorySyncChunkRetryRequest(HistorySyncType syncType, Integer chunkOrder, String chunkNotificationId, Boolean regenerateChunk) {
            this.syncType = syncType;
            this.chunkOrder = chunkOrder;
            this.chunkNotificationId = chunkNotificationId;
            this.regenerateChunk = regenerateChunk;
        }

        public Optional<HistorySyncType> syncType() {
            return Optional.ofNullable(syncType);
        }

        public OptionalInt chunkOrder() {
            return chunkOrder == null ? OptionalInt.empty() : OptionalInt.of(chunkOrder);
        }

        public Optional<String> chunkNotificationId() {
            return Optional.ofNullable(chunkNotificationId);
        }

        public boolean regenerateChunk() {
            return regenerateChunk != null && regenerateChunk;
        }

        public HistorySyncChunkRetryRequest setSyncType(HistorySyncType syncType) {
            this.syncType = syncType;
            return this;
        }

        public HistorySyncChunkRetryRequest setChunkOrder(Integer chunkOrder) {
            this.chunkOrder = chunkOrder;
            return this;
        }

        public HistorySyncChunkRetryRequest setChunkNotificationId(String chunkNotificationId) {
            this.chunkNotificationId = chunkNotificationId;
            return this;
        }

        public HistorySyncChunkRetryRequest setRegenerateChunk(Boolean regenerateChunk) {
            this.regenerateChunk = regenerateChunk;
            return this;
        }
    }

    @ProtobufMessage(name = "Message.PeerDataOperationRequestMessage.HistorySyncOnDemandRequest")
    public static final class HistorySyncOnDemandRequest {
        @ProtobufProperty(index = 1, type = ProtobufType.STRING)
        Jid chatJid;

        @ProtobufProperty(index = 2, type = ProtobufType.STRING)
        String oldestMsgId;

        @ProtobufProperty(index = 3, type = ProtobufType.BOOL)
        Boolean oldestMsgFromMe;

        @ProtobufProperty(index = 4, type = ProtobufType.INT32)
        Integer onDemandMsgCount;

        @ProtobufProperty(index = 5, type = ProtobufType.INT64, mixins = InstantProtobufMixin.class)
        Instant oldestMsgTimestampMs;

        @ProtobufProperty(index = 6, type = ProtobufType.STRING)
        String accountLid;


        HistorySyncOnDemandRequest(Jid chatJid, String oldestMsgId, Boolean oldestMsgFromMe, Integer onDemandMsgCount, Instant oldestMsgTimestampMs, String accountLid) {
            this.chatJid = chatJid;
            this.oldestMsgId = oldestMsgId;
            this.oldestMsgFromMe = oldestMsgFromMe;
            this.onDemandMsgCount = onDemandMsgCount;
            this.oldestMsgTimestampMs = oldestMsgTimestampMs;
            this.accountLid = accountLid;
        }

        public Optional<Jid> chatJid() {
            return Optional.ofNullable(chatJid);
        }

        public Optional<String> oldestMsgId() {
            return Optional.ofNullable(oldestMsgId);
        }

        public boolean oldestMsgFromMe() {
            return oldestMsgFromMe != null && oldestMsgFromMe;
        }

        public OptionalInt onDemandMsgCount() {
            return onDemandMsgCount == null ? OptionalInt.empty() : OptionalInt.of(onDemandMsgCount);
        }

        public Optional<Instant> oldestMsgTimestampMs() {
            return Optional.ofNullable(oldestMsgTimestampMs);
        }

        public Optional<String> accountLid() {
            return Optional.ofNullable(accountLid);
        }

        public HistorySyncOnDemandRequest setChatJid(Jid chatJid) {
            this.chatJid = chatJid;
            return this;
        }

        public HistorySyncOnDemandRequest setOldestMsgId(String oldestMsgId) {
            this.oldestMsgId = oldestMsgId;
            return this;
        }

        public HistorySyncOnDemandRequest setOldestMsgFromMe(Boolean oldestMsgFromMe) {
            this.oldestMsgFromMe = oldestMsgFromMe;
            return this;
        }

        public HistorySyncOnDemandRequest setOnDemandMsgCount(Integer onDemandMsgCount) {
            this.onDemandMsgCount = onDemandMsgCount;
            return this;
        }

        public HistorySyncOnDemandRequest setOldestMsgTimestampMs(Instant oldestMsgTimestampMs) {
            this.oldestMsgTimestampMs = oldestMsgTimestampMs;
            return this;
        }

        public HistorySyncOnDemandRequest setAccountLid(String accountLid) {
            this.accountLid = accountLid;
            return this;
        }
    }

    @ProtobufMessage(name = "Message.PeerDataOperationRequestMessage.PlaceholderMessageResendRequest")
    public static final class PlaceholderMessageResendRequest {
        @ProtobufProperty(index = 1, type = ProtobufType.MESSAGE)
        MessageKey messageKey;


        PlaceholderMessageResendRequest(MessageKey messageKey) {
            this.messageKey = messageKey;
        }

        public Optional<MessageKey> messageKey() {
            return Optional.ofNullable(messageKey);
        }

        public PlaceholderMessageResendRequest setMessageKey(MessageKey messageKey) {
            this.messageKey = messageKey;
            return this;
        }
    }

    @ProtobufMessage(name = "Message.PeerDataOperationRequestMessage.RequestStickerReupload")
    public static final class RequestStickerReupload {
        @ProtobufProperty(index = 1, type = ProtobufType.STRING)
        String fileSha256;


        RequestStickerReupload(String fileSha256) {
            this.fileSha256 = fileSha256;
        }

        public Optional<String> fileSha256() {
            return Optional.ofNullable(fileSha256);
        }

        public RequestStickerReupload setFileSha256(String fileSha256) {
            this.fileSha256 = fileSha256;
            return this;
        }
    }

    @ProtobufMessage(name = "Message.PeerDataOperationRequestMessage.RequestUrlPreview")
    public static final class RequestUrlPreview {
        @ProtobufProperty(index = 1, type = ProtobufType.STRING)
        String url;

        @ProtobufProperty(index = 2, type = ProtobufType.BOOL)
        Boolean includeHqThumbnail;


        RequestUrlPreview(String url, Boolean includeHqThumbnail) {
            this.url = url;
            this.includeHqThumbnail = includeHqThumbnail;
        }

        public Optional<String> url() {
            return Optional.ofNullable(url);
        }

        public boolean includeHqThumbnail() {
            return includeHqThumbnail != null && includeHqThumbnail;
        }

        public RequestUrlPreview setUrl(String url) {
            this.url = url;
            return this;
        }

        public RequestUrlPreview setIncludeHqThumbnail(Boolean includeHqThumbnail) {
            this.includeHqThumbnail = includeHqThumbnail;
            return this;
        }
    }

    @ProtobufMessage(name = "Message.PeerDataOperationRequestMessage.SyncDCollectionFatalRecoveryRequest")
    public static final class SyncDCollectionFatalRecoveryRequest {
        @ProtobufProperty(index = 1, type = ProtobufType.STRING)
        String collectionName;

        @ProtobufProperty(index = 2, type = ProtobufType.INT64, mixins = InstantProtobufMixin.class)
        Instant timestamp;


        SyncDCollectionFatalRecoveryRequest(String collectionName, Instant timestamp) {
            this.collectionName = collectionName;
            this.timestamp = timestamp;
        }

        public Optional<String> collectionName() {
            return Optional.ofNullable(collectionName);
        }

        public Optional<Instant> timestamp() {
            return Optional.ofNullable(timestamp);
        }

        public SyncDCollectionFatalRecoveryRequest setCollectionName(String collectionName) {
            this.collectionName = collectionName;
            return this;
        }

        public SyncDCollectionFatalRecoveryRequest setTimestamp(Instant timestamp) {
            this.timestamp = timestamp;
            return this;
        }
    }
}
