package com.github.auties00.cobalt.model.message.system.peer;

import com.github.auties00.cobalt.model.media.MediaRetryNotification;
import com.github.auties00.cobalt.model.message.Message;
import com.github.auties00.cobalt.model.message.media.StickerMessage;
import com.github.auties00.cobalt.model.message.system.history.FullHistorySyncOnDemandRequestMetadata;
import com.github.auties00.cobalt.model.message.system.history.HistorySyncType;
import com.github.auties00.cobalt.model.mixin.InstantMillisMixin;
import it.auties.protobuf.annotation.ProtobufEnum;
import it.auties.protobuf.annotation.ProtobufEnumIndex;
import it.auties.protobuf.annotation.ProtobufMessage;
import it.auties.protobuf.annotation.ProtobufProperty;
import it.auties.protobuf.model.ProtobufType;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

@ProtobufMessage(name = "Message.PeerDataOperationRequestResponseMessage")
public final class PeerDataOperationRequestResponseMessage implements Message {
    @ProtobufProperty(index = 1, type = ProtobufType.ENUM)
    PeerDataOperationRequestType peerDataOperationRequestType;

    @ProtobufProperty(index = 2, type = ProtobufType.STRING)
    String stanzaId;

    @ProtobufProperty(index = 3, type = ProtobufType.MESSAGE)
    List<PeerDataOperationResult> peerDataOperationResult;


    PeerDataOperationRequestResponseMessage(PeerDataOperationRequestType peerDataOperationRequestType, String stanzaId, List<PeerDataOperationResult> peerDataOperationResult) {
        this.peerDataOperationRequestType = peerDataOperationRequestType;
        this.stanzaId = stanzaId;
        this.peerDataOperationResult = peerDataOperationResult;
    }

    public Optional<PeerDataOperationRequestType> peerDataOperationRequestType() {
        return Optional.ofNullable(peerDataOperationRequestType);
    }

    public Optional<String> stanzaId() {
        return Optional.ofNullable(stanzaId);
    }

    public List<PeerDataOperationResult> peerDataOperationResult() {
        return peerDataOperationResult == null ? List.of() : Collections.unmodifiableList(peerDataOperationResult);
    }

    public PeerDataOperationRequestResponseMessage setPeerDataOperationRequestType(PeerDataOperationRequestType peerDataOperationRequestType) {
        this.peerDataOperationRequestType = peerDataOperationRequestType;
        return this;
    }

    public PeerDataOperationRequestResponseMessage setStanzaId(String stanzaId) {
        this.stanzaId = stanzaId;
        return this;
    }

    public PeerDataOperationRequestResponseMessage setPeerDataOperationResult(List<PeerDataOperationResult> peerDataOperationResult) {
        this.peerDataOperationResult = peerDataOperationResult;
        return this;
    }

    @ProtobufMessage(name = "Message.PeerDataOperationRequestResponseMessage.PeerDataOperationResult")
    public static final class PeerDataOperationResult {
        @ProtobufProperty(index = 1, type = ProtobufType.ENUM)
        MediaRetryNotification.ResultType mediaUploadResult;

        @ProtobufProperty(index = 2, type = ProtobufType.MESSAGE)
        StickerMessage stickerMessage;

        @ProtobufProperty(index = 3, type = ProtobufType.MESSAGE)
        PeerDataOperationResult.LinkPreviewResponse linkPreviewResponse;

        @ProtobufProperty(index = 4, type = ProtobufType.MESSAGE)
        PeerDataOperationResult.PlaceholderMessageResendResponse placeholderMessageResendResponse;

        @ProtobufProperty(index = 5, type = ProtobufType.MESSAGE)
        PeerDataOperationResult.WaffleNonceFetchResponse waffleNonceFetchRequestResponse;

        @ProtobufProperty(index = 6, type = ProtobufType.MESSAGE)
        PeerDataOperationResult.FullHistorySyncOnDemandRequestResponse fullHistorySyncOnDemandRequestResponse;

        @ProtobufProperty(index = 7, type = ProtobufType.MESSAGE)
        PeerDataOperationResult.CompanionMetaNonceFetchResponse companionMetaNonceFetchRequestResponse;

        @ProtobufProperty(index = 8, type = ProtobufType.MESSAGE)
        PeerDataOperationResult.SyncDSnapshotFatalRecoveryResponse syncdSnapshotFatalRecoveryResponse;

        @ProtobufProperty(index = 9, type = ProtobufType.MESSAGE)
        PeerDataOperationResult.CompanionCanonicalUserNonceFetchResponse companionCanonicalUserNonceFetchRequestResponse;

        @ProtobufProperty(index = 10, type = ProtobufType.MESSAGE)
        PeerDataOperationResult.HistorySyncChunkRetryResponse historySyncChunkRetryResponse;


        PeerDataOperationResult(MediaRetryNotification.ResultType mediaUploadResult, StickerMessage stickerMessage, LinkPreviewResponse linkPreviewResponse, PlaceholderMessageResendResponse placeholderMessageResendResponse, WaffleNonceFetchResponse waffleNonceFetchRequestResponse, FullHistorySyncOnDemandRequestResponse fullHistorySyncOnDemandRequestResponse, CompanionMetaNonceFetchResponse companionMetaNonceFetchRequestResponse, SyncDSnapshotFatalRecoveryResponse syncdSnapshotFatalRecoveryResponse, CompanionCanonicalUserNonceFetchResponse companionCanonicalUserNonceFetchRequestResponse, HistorySyncChunkRetryResponse historySyncChunkRetryResponse) {
            this.mediaUploadResult = mediaUploadResult;
            this.stickerMessage = stickerMessage;
            this.linkPreviewResponse = linkPreviewResponse;
            this.placeholderMessageResendResponse = placeholderMessageResendResponse;
            this.waffleNonceFetchRequestResponse = waffleNonceFetchRequestResponse;
            this.fullHistorySyncOnDemandRequestResponse = fullHistorySyncOnDemandRequestResponse;
            this.companionMetaNonceFetchRequestResponse = companionMetaNonceFetchRequestResponse;
            this.syncdSnapshotFatalRecoveryResponse = syncdSnapshotFatalRecoveryResponse;
            this.companionCanonicalUserNonceFetchRequestResponse = companionCanonicalUserNonceFetchRequestResponse;
            this.historySyncChunkRetryResponse = historySyncChunkRetryResponse;
        }

        public Optional<MediaRetryNotification.ResultType> mediaUploadResult() {
            return Optional.ofNullable(mediaUploadResult);
        }

        public Optional<StickerMessage> stickerMessage() {
            return Optional.ofNullable(stickerMessage);
        }

        public Optional<LinkPreviewResponse> linkPreviewResponse() {
            return Optional.ofNullable(linkPreviewResponse);
        }

        public Optional<PlaceholderMessageResendResponse> placeholderMessageResendResponse() {
            return Optional.ofNullable(placeholderMessageResendResponse);
        }

        public Optional<WaffleNonceFetchResponse> waffleNonceFetchRequestResponse() {
            return Optional.ofNullable(waffleNonceFetchRequestResponse);
        }

        public Optional<FullHistorySyncOnDemandRequestResponse> fullHistorySyncOnDemandRequestResponse() {
            return Optional.ofNullable(fullHistorySyncOnDemandRequestResponse);
        }

        public Optional<CompanionMetaNonceFetchResponse> companionMetaNonceFetchRequestResponse() {
            return Optional.ofNullable(companionMetaNonceFetchRequestResponse);
        }

        public Optional<SyncDSnapshotFatalRecoveryResponse> syncdSnapshotFatalRecoveryResponse() {
            return Optional.ofNullable(syncdSnapshotFatalRecoveryResponse);
        }

        public Optional<CompanionCanonicalUserNonceFetchResponse> companionCanonicalUserNonceFetchRequestResponse() {
            return Optional.ofNullable(companionCanonicalUserNonceFetchRequestResponse);
        }

        public Optional<HistorySyncChunkRetryResponse> historySyncChunkRetryResponse() {
            return Optional.ofNullable(historySyncChunkRetryResponse);
        }

        public PeerDataOperationResult setMediaUploadResult(MediaRetryNotification.ResultType mediaUploadResult) {
            this.mediaUploadResult = mediaUploadResult;
            return this;
        }

        public PeerDataOperationResult setStickerMessage(StickerMessage stickerMessage) {
            this.stickerMessage = stickerMessage;
            return this;
        }

        public PeerDataOperationResult setLinkPreviewResponse(LinkPreviewResponse linkPreviewResponse) {
            this.linkPreviewResponse = linkPreviewResponse;
            return this;
        }

        public PeerDataOperationResult setPlaceholderMessageResendResponse(PlaceholderMessageResendResponse placeholderMessageResendResponse) {
            this.placeholderMessageResendResponse = placeholderMessageResendResponse;
            return this;
        }

        public PeerDataOperationResult setWaffleNonceFetchRequestResponse(WaffleNonceFetchResponse waffleNonceFetchRequestResponse) {
            this.waffleNonceFetchRequestResponse = waffleNonceFetchRequestResponse;
            return this;
        }

        public PeerDataOperationResult setFullHistorySyncOnDemandRequestResponse(FullHistorySyncOnDemandRequestResponse fullHistorySyncOnDemandRequestResponse) {
            this.fullHistorySyncOnDemandRequestResponse = fullHistorySyncOnDemandRequestResponse;
            return this;
        }

        public PeerDataOperationResult setCompanionMetaNonceFetchRequestResponse(CompanionMetaNonceFetchResponse companionMetaNonceFetchRequestResponse) {
            this.companionMetaNonceFetchRequestResponse = companionMetaNonceFetchRequestResponse;
            return this;
        }

        public PeerDataOperationResult setSyncdSnapshotFatalRecoveryResponse(SyncDSnapshotFatalRecoveryResponse syncdSnapshotFatalRecoveryResponse) {
            this.syncdSnapshotFatalRecoveryResponse = syncdSnapshotFatalRecoveryResponse;
            return this;
        }

        public PeerDataOperationResult setCompanionCanonicalUserNonceFetchRequestResponse(CompanionCanonicalUserNonceFetchResponse companionCanonicalUserNonceFetchRequestResponse) {
            this.companionCanonicalUserNonceFetchRequestResponse = companionCanonicalUserNonceFetchRequestResponse;
            return this;
        }

        public PeerDataOperationResult setHistorySyncChunkRetryResponse(HistorySyncChunkRetryResponse historySyncChunkRetryResponse) {
            this.historySyncChunkRetryResponse = historySyncChunkRetryResponse;
            return this;
        }

        @ProtobufEnum(name = "Message.PeerDataOperationRequestResponseMessage.PeerDataOperationResult.FullHistorySyncOnDemandResponseCode")
        public static enum FullHistorySyncOnDemandResponseCode {
            REQUEST_SUCCESS(0),
            REQUEST_TIME_EXPIRED(1),
            DECLINED_SHARING_HISTORY(2),
            GENERIC_ERROR(3),
            ERROR_REQUEST_ON_NON_SMB_PRIMARY(4),
            ERROR_HOSTED_DEVICE_NOT_CONNECTED(5),
            ERROR_HOSTED_DEVICE_LOGIN_TIME_NOT_SET(6);

            FullHistorySyncOnDemandResponseCode(@ProtobufEnumIndex int index) {
                this.index = index;
            }

            final int index;

            public int index() {
                return this.index;
            }
        }

        @ProtobufEnum(name = "Message.PeerDataOperationRequestResponseMessage.PeerDataOperationResult.HistorySyncChunkRetryResponseCode")
        public static enum HistorySyncChunkRetryResponseCode {
            GENERATION_ERROR(1),
            CHUNK_CONSUMED(2),
            TIMEOUT(3),
            SESSION_EXHAUSTED(4),
            CHUNK_EXHAUSTED(5),
            DUPLICATED_REQUEST(6);

            HistorySyncChunkRetryResponseCode(@ProtobufEnumIndex int index) {
                this.index = index;
            }

            final int index;

            public int index() {
                return this.index;
            }
        }

        @ProtobufMessage(name = "Message.PeerDataOperationRequestResponseMessage.PeerDataOperationResult.CompanionCanonicalUserNonceFetchResponse")
        public static final class CompanionCanonicalUserNonceFetchResponse {
            @ProtobufProperty(index = 1, type = ProtobufType.STRING)
            String nonce;

            @ProtobufProperty(index = 2, type = ProtobufType.STRING)
            String waFbid;

            @ProtobufProperty(index = 3, type = ProtobufType.BOOL)
            Boolean forceRefresh;


            CompanionCanonicalUserNonceFetchResponse(String nonce, String waFbid, Boolean forceRefresh) {
                this.nonce = nonce;
                this.waFbid = waFbid;
                this.forceRefresh = forceRefresh;
            }

            public Optional<String> nonce() {
                return Optional.ofNullable(nonce);
            }

            public Optional<String> waFbid() {
                return Optional.ofNullable(waFbid);
            }

            public boolean forceRefresh() {
                return forceRefresh != null && forceRefresh;
            }

            public CompanionCanonicalUserNonceFetchResponse setNonce(String nonce) {
                this.nonce = nonce;
                return this;
            }

            public CompanionCanonicalUserNonceFetchResponse setWaFbid(String waFbid) {
                this.waFbid = waFbid;
                return this;
            }

            public CompanionCanonicalUserNonceFetchResponse setForceRefresh(Boolean forceRefresh) {
                this.forceRefresh = forceRefresh;
                return this;
            }
        }

        @ProtobufMessage(name = "Message.PeerDataOperationRequestResponseMessage.PeerDataOperationResult.CompanionMetaNonceFetchResponse")
        public static final class CompanionMetaNonceFetchResponse {
            @ProtobufProperty(index = 1, type = ProtobufType.STRING)
            String nonce;


            CompanionMetaNonceFetchResponse(String nonce) {
                this.nonce = nonce;
            }

            public Optional<String> nonce() {
                return Optional.ofNullable(nonce);
            }

            public CompanionMetaNonceFetchResponse setNonce(String nonce) {
                this.nonce = nonce;
                return this;
            }
        }

        @ProtobufMessage(name = "Message.PeerDataOperationRequestResponseMessage.PeerDataOperationResult.FullHistorySyncOnDemandRequestResponse")
        public static final class FullHistorySyncOnDemandRequestResponse {
            @ProtobufProperty(index = 1, type = ProtobufType.MESSAGE)
            FullHistorySyncOnDemandRequestMetadata requestMetadata;

            @ProtobufProperty(index = 2, type = ProtobufType.ENUM)
            PeerDataOperationResult.FullHistorySyncOnDemandResponseCode responseCode;


            FullHistorySyncOnDemandRequestResponse(FullHistorySyncOnDemandRequestMetadata requestMetadata, FullHistorySyncOnDemandResponseCode responseCode) {
                this.requestMetadata = requestMetadata;
                this.responseCode = responseCode;
            }

            public Optional<FullHistorySyncOnDemandRequestMetadata> requestMetadata() {
                return Optional.ofNullable(requestMetadata);
            }

            public Optional<FullHistorySyncOnDemandResponseCode> responseCode() {
                return Optional.ofNullable(responseCode);
            }

            public FullHistorySyncOnDemandRequestResponse setRequestMetadata(FullHistorySyncOnDemandRequestMetadata requestMetadata) {
                this.requestMetadata = requestMetadata;
                return this;
            }

            public FullHistorySyncOnDemandRequestResponse setResponseCode(FullHistorySyncOnDemandResponseCode responseCode) {
                this.responseCode = responseCode;
                return this;
            }
        }

        @ProtobufMessage(name = "Message.PeerDataOperationRequestResponseMessage.PeerDataOperationResult.HistorySyncChunkRetryResponse")
        public static final class HistorySyncChunkRetryResponse {
            @ProtobufProperty(index = 1, type = ProtobufType.ENUM)
            HistorySyncType syncType;

            @ProtobufProperty(index = 2, type = ProtobufType.UINT32)
            Integer chunkOrder;

            @ProtobufProperty(index = 3, type = ProtobufType.STRING)
            String requestId;

            @ProtobufProperty(index = 4, type = ProtobufType.ENUM)
            PeerDataOperationResult.HistorySyncChunkRetryResponseCode responseCode;

            @ProtobufProperty(index = 5, type = ProtobufType.BOOL)
            Boolean canRecover;


            HistorySyncChunkRetryResponse(HistorySyncType syncType, Integer chunkOrder, String requestId, HistorySyncChunkRetryResponseCode responseCode, Boolean canRecover) {
                this.syncType = syncType;
                this.chunkOrder = chunkOrder;
                this.requestId = requestId;
                this.responseCode = responseCode;
                this.canRecover = canRecover;
            }

            public Optional<HistorySyncType> syncType() {
                return Optional.ofNullable(syncType);
            }

            public OptionalInt chunkOrder() {
                return chunkOrder == null ? OptionalInt.empty() : OptionalInt.of(chunkOrder);
            }

            public Optional<String> requestId() {
                return Optional.ofNullable(requestId);
            }

            public Optional<HistorySyncChunkRetryResponseCode> responseCode() {
                return Optional.ofNullable(responseCode);
            }

            public boolean canRecover() {
                return canRecover != null && canRecover;
            }

            public HistorySyncChunkRetryResponse setSyncType(HistorySyncType syncType) {
                this.syncType = syncType;
                return this;
            }

            public HistorySyncChunkRetryResponse setChunkOrder(Integer chunkOrder) {
                this.chunkOrder = chunkOrder;
                return this;
            }

            public HistorySyncChunkRetryResponse setRequestId(String requestId) {
                this.requestId = requestId;
                return this;
            }

            public HistorySyncChunkRetryResponse setResponseCode(HistorySyncChunkRetryResponseCode responseCode) {
                this.responseCode = responseCode;
                return this;
            }

            public HistorySyncChunkRetryResponse setCanRecover(Boolean canRecover) {
                this.canRecover = canRecover;
                return this;
            }
        }

        @ProtobufMessage(name = "Message.PeerDataOperationRequestResponseMessage.PeerDataOperationResult.LinkPreviewResponse")
        public static final class LinkPreviewResponse {
            @ProtobufProperty(index = 1, type = ProtobufType.STRING)
            String url;

            @ProtobufProperty(index = 2, type = ProtobufType.STRING)
            String title;

            @ProtobufProperty(index = 3, type = ProtobufType.STRING)
            String description;

            @ProtobufProperty(index = 4, type = ProtobufType.BYTES)
            byte[] thumbData;

            @ProtobufProperty(index = 6, type = ProtobufType.STRING)
            String matchText;

            @ProtobufProperty(index = 7, type = ProtobufType.STRING)
            String previewType;

            @ProtobufProperty(index = 8, type = ProtobufType.MESSAGE)
            PeerDataOperationResult.LinkPreviewResponse.LinkPreviewHighQualityThumbnail hqThumbnail;

            @ProtobufProperty(index = 9, type = ProtobufType.MESSAGE)
            PeerDataOperationResult.LinkPreviewResponse.PaymentLinkPreviewMetadata previewMetadata;


            LinkPreviewResponse(String url, String title, String description, byte[] thumbData, String matchText, String previewType, LinkPreviewHighQualityThumbnail hqThumbnail, PaymentLinkPreviewMetadata previewMetadata) {
                this.url = url;
                this.title = title;
                this.description = description;
                this.thumbData = thumbData;
                this.matchText = matchText;
                this.previewType = previewType;
                this.hqThumbnail = hqThumbnail;
                this.previewMetadata = previewMetadata;
            }

            public Optional<String> url() {
                return Optional.ofNullable(url);
            }

            public Optional<String> title() {
                return Optional.ofNullable(title);
            }

            public Optional<String> description() {
                return Optional.ofNullable(description);
            }

            public Optional<byte[]> thumbData() {
                return Optional.ofNullable(thumbData);
            }

            public Optional<String> matchText() {
                return Optional.ofNullable(matchText);
            }

            public Optional<String> previewType() {
                return Optional.ofNullable(previewType);
            }

            public Optional<LinkPreviewHighQualityThumbnail> hqThumbnail() {
                return Optional.ofNullable(hqThumbnail);
            }

            public Optional<PaymentLinkPreviewMetadata> previewMetadata() {
                return Optional.ofNullable(previewMetadata);
            }

            public LinkPreviewResponse setUrl(String url) {
                this.url = url;
                return this;
            }

            public LinkPreviewResponse setTitle(String title) {
                this.title = title;
                return this;
            }

            public LinkPreviewResponse setDescription(String description) {
                this.description = description;
                return this;
            }

            public LinkPreviewResponse setThumbData(byte[] thumbData) {
                this.thumbData = thumbData;
                return this;
            }

            public LinkPreviewResponse setMatchText(String matchText) {
                this.matchText = matchText;
                return this;
            }

            public LinkPreviewResponse setPreviewType(String previewType) {
                this.previewType = previewType;
                return this;
            }

            public LinkPreviewResponse setHqThumbnail(LinkPreviewHighQualityThumbnail hqThumbnail) {
                this.hqThumbnail = hqThumbnail;
                return this;
            }

            public LinkPreviewResponse setPreviewMetadata(PaymentLinkPreviewMetadata previewMetadata) {
                this.previewMetadata = previewMetadata;
                return this;
            }

            @ProtobufMessage(name = "Message.PeerDataOperationRequestResponseMessage.PeerDataOperationResult.LinkPreviewResponse.LinkPreviewHighQualityThumbnail")
            public static final class LinkPreviewHighQualityThumbnail {
                @ProtobufProperty(index = 1, type = ProtobufType.STRING)
                String directPath;

                @ProtobufProperty(index = 2, type = ProtobufType.STRING)
                String thumbHash;

                @ProtobufProperty(index = 3, type = ProtobufType.STRING)
                String encThumbHash;

                @ProtobufProperty(index = 4, type = ProtobufType.BYTES)
                byte[] mediaKey;

                @ProtobufProperty(index = 5, type = ProtobufType.INT64, mixins = InstantMillisMixin.class)
                Instant mediaKeyTimestampMs;

                @ProtobufProperty(index = 6, type = ProtobufType.INT32)
                Integer thumbWidth;

                @ProtobufProperty(index = 7, type = ProtobufType.INT32)
                Integer thumbHeight;


                LinkPreviewHighQualityThumbnail(String directPath, String thumbHash, String encThumbHash, byte[] mediaKey, Instant mediaKeyTimestampMs, Integer thumbWidth, Integer thumbHeight) {
                    this.directPath = directPath;
                    this.thumbHash = thumbHash;
                    this.encThumbHash = encThumbHash;
                    this.mediaKey = mediaKey;
                    this.mediaKeyTimestampMs = mediaKeyTimestampMs;
                    this.thumbWidth = thumbWidth;
                    this.thumbHeight = thumbHeight;
                }

                public Optional<String> directPath() {
                    return Optional.ofNullable(directPath);
                }

                public Optional<String> thumbHash() {
                    return Optional.ofNullable(thumbHash);
                }

                public Optional<String> encThumbHash() {
                    return Optional.ofNullable(encThumbHash);
                }

                public Optional<byte[]> mediaKey() {
                    return Optional.ofNullable(mediaKey);
                }

                public Optional<Instant> mediaKeyTimestampMs() {
                    return Optional.ofNullable(mediaKeyTimestampMs);
                }

                public OptionalInt thumbWidth() {
                    return thumbWidth == null ? OptionalInt.empty() : OptionalInt.of(thumbWidth);
                }

                public OptionalInt thumbHeight() {
                    return thumbHeight == null ? OptionalInt.empty() : OptionalInt.of(thumbHeight);
                }

                public LinkPreviewHighQualityThumbnail setDirectPath(String directPath) {
                    this.directPath = directPath;
                    return this;
                }

                public LinkPreviewHighQualityThumbnail setThumbHash(String thumbHash) {
                    this.thumbHash = thumbHash;
                    return this;
                }

                public LinkPreviewHighQualityThumbnail setEncThumbHash(String encThumbHash) {
                    this.encThumbHash = encThumbHash;
                    return this;
                }

                public LinkPreviewHighQualityThumbnail setMediaKey(byte[] mediaKey) {
                    this.mediaKey = mediaKey;
                    return this;
                }

                public LinkPreviewHighQualityThumbnail setMediaKeyTimestampMs(Instant mediaKeyTimestampMs) {
                    this.mediaKeyTimestampMs = mediaKeyTimestampMs;
                    return this;
                }

                public LinkPreviewHighQualityThumbnail setThumbWidth(Integer thumbWidth) {
                    this.thumbWidth = thumbWidth;
                    return this;
                }

                public LinkPreviewHighQualityThumbnail setThumbHeight(Integer thumbHeight) {
                    this.thumbHeight = thumbHeight;
                    return this;
                }
            }

            @ProtobufMessage(name = "Message.PeerDataOperationRequestResponseMessage.PeerDataOperationResult.LinkPreviewResponse.PaymentLinkPreviewMetadata")
            public static final class PaymentLinkPreviewMetadata {
                @ProtobufProperty(index = 1, type = ProtobufType.BOOL)
                Boolean isBusinessVerified;

                @ProtobufProperty(index = 2, type = ProtobufType.STRING)
                String providerName;


                PaymentLinkPreviewMetadata(Boolean isBusinessVerified, String providerName) {
                    this.isBusinessVerified = isBusinessVerified;
                    this.providerName = providerName;
                }

                public boolean isBusinessVerified() {
                    return isBusinessVerified != null && isBusinessVerified;
                }

                public Optional<String> providerName() {
                    return Optional.ofNullable(providerName);
                }

                public PaymentLinkPreviewMetadata setBusinessVerified(Boolean isBusinessVerified) {
                    this.isBusinessVerified = isBusinessVerified;
                    return this;
                }

                public PaymentLinkPreviewMetadata setProviderName(String providerName) {
                    this.providerName = providerName;
                    return this;
                }
            }
        }

        @ProtobufMessage(name = "Message.PeerDataOperationRequestResponseMessage.PeerDataOperationResult.PlaceholderMessageResendResponse")
        public static final class PlaceholderMessageResendResponse {
            @ProtobufProperty(index = 1, type = ProtobufType.BYTES)
            byte[] webMessageInfoBytes;


            PlaceholderMessageResendResponse(byte[] webMessageInfoBytes) {
                this.webMessageInfoBytes = webMessageInfoBytes;
            }

            public Optional<byte[]> webMessageInfoBytes() {
                return Optional.ofNullable(webMessageInfoBytes);
            }

            public PlaceholderMessageResendResponse setWebMessageInfoBytes(byte[] webMessageInfoBytes) {
                this.webMessageInfoBytes = webMessageInfoBytes;
                return this;
            }
        }

        @ProtobufMessage(name = "Message.PeerDataOperationRequestResponseMessage.PeerDataOperationResult.SyncDSnapshotFatalRecoveryResponse")
        public static final class SyncDSnapshotFatalRecoveryResponse {
            @ProtobufProperty(index = 1, type = ProtobufType.BYTES)
            byte[] collectionSnapshot;

            @ProtobufProperty(index = 2, type = ProtobufType.BOOL)
            Boolean isCompressed;


            SyncDSnapshotFatalRecoveryResponse(byte[] collectionSnapshot, Boolean isCompressed) {
                this.collectionSnapshot = collectionSnapshot;
                this.isCompressed = isCompressed;
            }

            public Optional<byte[]> collectionSnapshot() {
                return Optional.ofNullable(collectionSnapshot);
            }

            public boolean isCompressed() {
                return isCompressed != null && isCompressed;
            }

            public SyncDSnapshotFatalRecoveryResponse setCollectionSnapshot(byte[] collectionSnapshot) {
                this.collectionSnapshot = collectionSnapshot;
                return this;
            }

            public SyncDSnapshotFatalRecoveryResponse setCompressed(Boolean isCompressed) {
                this.isCompressed = isCompressed;
                return this;
            }
        }

        @ProtobufMessage(name = "Message.PeerDataOperationRequestResponseMessage.PeerDataOperationResult.WaffleNonceFetchResponse")
        public static final class WaffleNonceFetchResponse {
            @ProtobufProperty(index = 1, type = ProtobufType.STRING)
            String nonce;

            @ProtobufProperty(index = 2, type = ProtobufType.STRING)
            String waEntFbid;


            WaffleNonceFetchResponse(String nonce, String waEntFbid) {
                this.nonce = nonce;
                this.waEntFbid = waEntFbid;
            }

            public Optional<String> nonce() {
                return Optional.ofNullable(nonce);
            }

            public Optional<String> waEntFbid() {
                return Optional.ofNullable(waEntFbid);
            }

            public WaffleNonceFetchResponse setNonce(String nonce) {
                this.nonce = nonce;
                return this;
            }

            public WaffleNonceFetchResponse setWaEntFbid(String waEntFbid) {
                this.waEntFbid = waEntFbid;
                return this;
            }
        }
    }
}
