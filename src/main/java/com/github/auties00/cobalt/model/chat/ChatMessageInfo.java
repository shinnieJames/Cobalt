package com.github.auties00.cobalt.model.chat;

import com.github.auties00.cobalt.model.message.*;
import com.github.auties00.cobalt.model.message.addon.*;
import com.github.auties00.cobalt.model.chat.group.GroupHistoryBundleInfo;
import com.github.auties00.cobalt.model.chat.group.GroupHistoryIndividualMessageInfo;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.media.MediaData;
import com.github.auties00.cobalt.model.message.event.EventAdditionalMetadata;
import com.github.auties00.cobalt.model.message.event.EventResponse;
import com.github.auties00.cobalt.model.message.interactive.InteractiveMessageAdditionalMetadata;
import com.github.auties00.cobalt.model.message.location.LiveLocationMessage;
import com.github.auties00.cobalt.model.message.status.StatusMentionMessage;
import com.github.auties00.cobalt.model.message.status.StatusPSA;
import com.github.auties00.cobalt.model.message.MessageCitation;
import com.github.auties00.cobalt.model.message.MessageReportingTokenInfo;
import com.github.auties00.cobalt.model.mixin.InstantMillisMixin;
import com.github.auties00.cobalt.model.mixin.InstantSecondsMixin;
import com.github.auties00.cobalt.model.payment.PaymentInfo;
import it.auties.protobuf.annotation.ProtobufEnum;
import it.auties.protobuf.annotation.ProtobufEnumIndex;
import it.auties.protobuf.annotation.ProtobufMessage;
import it.auties.protobuf.annotation.ProtobufProperty;
import it.auties.protobuf.model.ProtobufType;

import java.time.Instant;
import java.util.*;

@ProtobufMessage(name = "WebMessageInfo")
public final class ChatMessageInfo implements MessageInfo {
    @ProtobufProperty(index = 1, type = ProtobufType.MESSAGE)
    MessageKey key;

    @ProtobufProperty(index = 2, type = ProtobufType.MESSAGE)
    MessageContainer message;

    @ProtobufProperty(index = 3, type = ProtobufType.UINT64, mixins = InstantSecondsMixin.class)
    Instant timestamp;

    @ProtobufProperty(index = 4, type = ProtobufType.ENUM)
    MessageStatus status;

    @ProtobufProperty(index = 5, type = ProtobufType.STRING)
    Jid senderJid;

    @ProtobufProperty(index = 6, type = ProtobufType.UINT64, mixins = InstantSecondsMixin.class)
    Instant c2STimestamp;

    @ProtobufProperty(index = 16, type = ProtobufType.BOOL)
    Boolean ignore;

    @ProtobufProperty(index = 17, type = ProtobufType.BOOL)
    Boolean starred;

    @ProtobufProperty(index = 18, type = ProtobufType.BOOL)
    Boolean broadcast;

    @ProtobufProperty(index = 19, type = ProtobufType.STRING)
    String pushName;

    @ProtobufProperty(index = 20, type = ProtobufType.BYTES)
    byte[] mediaCiphertextSha256;

    @ProtobufProperty(index = 21, type = ProtobufType.BOOL)
    Boolean multicast;

    @ProtobufProperty(index = 22, type = ProtobufType.BOOL)
    Boolean urlText;

    @ProtobufProperty(index = 23, type = ProtobufType.BOOL)
    Boolean urlNumber;

    @ProtobufProperty(index = 24, type = ProtobufType.ENUM)
    StubType stubType;

    @ProtobufProperty(index = 25, type = ProtobufType.BOOL)
    Boolean clearMedia;

    @ProtobufProperty(index = 26, type = ProtobufType.STRING)
    List<String> stubParameters;

    @ProtobufProperty(index = 27, type = ProtobufType.UINT32)
    Integer duration;

    @ProtobufProperty(index = 28, type = ProtobufType.STRING)
    List<String> labels;

    @ProtobufProperty(index = 29, type = ProtobufType.MESSAGE)
    PaymentInfo paymentInfo;

    @ProtobufProperty(index = 30, type = ProtobufType.MESSAGE)
    LiveLocationMessage finalLiveLocation;

    @ProtobufProperty(index = 31, type = ProtobufType.MESSAGE)
    PaymentInfo quotedPaymentInfo;

    @ProtobufProperty(index = 32, type = ProtobufType.UINT64, mixins = InstantSecondsMixin.class)
    Instant ephemeralStartTimestamp;

    @ProtobufProperty(index = 33, type = ProtobufType.UINT32)
    Integer ephemeralDuration;

    @ProtobufProperty(index = 34, type = ProtobufType.BOOL)
    Boolean ephemeralOffToOn;

    @ProtobufProperty(index = 35, type = ProtobufType.BOOL)
    Boolean ephemeralOutOfSync;

    @ProtobufProperty(index = 36, type = ProtobufType.ENUM)
    BizPrivacyStatus bizPrivacyStatus;

    @ProtobufProperty(index = 37, type = ProtobufType.STRING)
    String verifiedBizName;

    @ProtobufProperty(index = 38, type = ProtobufType.MESSAGE)
    MediaData mediaData;

    @ProtobufProperty(index = 39, type = ProtobufType.MESSAGE)
    PhotoChange photoChange;

    @ProtobufProperty(index = 40, type = ProtobufType.MESSAGE)
    List<MessageReceipt> receipts;

    @ProtobufProperty(index = 41, type = ProtobufType.MESSAGE)
    List<Reaction> reactions;

    @ProtobufProperty(index = 42, type = ProtobufType.MESSAGE)
    MediaData quotedStickerData;

    @ProtobufProperty(index = 43, type = ProtobufType.BYTES)
    byte[] futureproofData;

    @ProtobufProperty(index = 44, type = ProtobufType.MESSAGE)
    StatusPSA statusPsa;

    @ProtobufProperty(index = 45, type = ProtobufType.MESSAGE)
    List<PollVoteRecord> pollUpdates;

    @ProtobufProperty(index = 46, type = ProtobufType.MESSAGE)
    PollAdditionalMetadata pollAdditionalMetadata;

    @ProtobufProperty(index = 47, type = ProtobufType.STRING)
    String agentId;

    @ProtobufProperty(index = 48, type = ProtobufType.BOOL)
    Boolean statusAlreadyViewed;

    @ProtobufProperty(index = 49, type = ProtobufType.BYTES)
    byte[] messageSecret;

    @ProtobufProperty(index = 50, type = ProtobufType.MESSAGE)
    KeepInChat keepInChat;

    @ProtobufProperty(index = 51, type = ProtobufType.STRING)
    Jid originalSelfAuthorUserJidString;

    @ProtobufProperty(index = 52, type = ProtobufType.UINT64, mixins = InstantSecondsMixin.class)
    Instant revokeMessageTimestamp;

    @ProtobufProperty(index = 54, type = ProtobufType.MESSAGE)
    PinInChat pinInChat;

    @ProtobufProperty(index = 55, type = ProtobufType.MESSAGE)
    PremiumMessageInfo premiumMessageInfo;

    @ProtobufProperty(index = 56, type = ProtobufType.BOOL)
    Boolean is1PBizBotMessage;

    @ProtobufProperty(index = 57, type = ProtobufType.BOOL)
    Boolean isGroupHistoryMessage;

    @ProtobufProperty(index = 58, type = ProtobufType.STRING)
    Jid botMessageInvokerJid;

    @ProtobufProperty(index = 59, type = ProtobufType.MESSAGE)
    ChatCommentMetadata commentMetadata;

    @ProtobufProperty(index = 61, type = ProtobufType.MESSAGE)
    List<EventResponse> eventResponses;

    @ProtobufProperty(index = 62, type = ProtobufType.MESSAGE)
    MessageReportingTokenInfo reportingTokenInfo;

    @ProtobufProperty(index = 63, type = ProtobufType.UINT64)
    Long newsletterServerId;

    @ProtobufProperty(index = 64, type = ProtobufType.MESSAGE)
    EventAdditionalMetadata eventAdditionalMetadata;

    @ProtobufProperty(index = 65, type = ProtobufType.BOOL)
    Boolean isMentionedInStatus;

    @ProtobufProperty(index = 66, type = ProtobufType.STRING)
    List<String> statusMentions;

    @ProtobufProperty(index = 67, type = ProtobufType.MESSAGE)
    MessageKey targetMessageId;

    @ProtobufProperty(index = 68, type = ProtobufType.MESSAGE)
    List<MessageAddOn> messageAddOns;

    @ProtobufProperty(index = 69, type = ProtobufType.MESSAGE)
    StatusMentionMessage statusMentionMessageInfo;

    @ProtobufProperty(index = 70, type = ProtobufType.BOOL)
    Boolean isSupportAiMessage;

    @ProtobufProperty(index = 71, type = ProtobufType.STRING)
    List<String> statusMentionSources;

    @ProtobufProperty(index = 72, type = ProtobufType.MESSAGE)
    List<MessageCitation> supportAiCitations;

    @ProtobufProperty(index = 73, type = ProtobufType.STRING)
    String botTargetId;

    @ProtobufProperty(index = 74, type = ProtobufType.MESSAGE)
    GroupHistoryIndividualMessageInfo groupHistoryIndividualMessageInfo;

    @ProtobufProperty(index = 75, type = ProtobufType.MESSAGE)
    GroupHistoryBundleInfo groupHistoryBundleInfo;

    @ProtobufProperty(index = 76, type = ProtobufType.MESSAGE)
    InteractiveMessageAdditionalMetadata interactiveMessageAdditionalMetadata;

    @ProtobufProperty(index = 77, type = ProtobufType.MESSAGE)
    QuarantinedMessage quarantinedMessage;

    @ProtobufProperty(index = 78, type = ProtobufType.UINT32)
    Integer nonJidMentions;


    ChatMessageInfo(MessageKey key, MessageContainer messageContainer, Instant messageTimestamp, MessageStatus status, Jid senderJid, Instant c2STimestamp, Boolean ignore, Boolean starred, Boolean broadcast, String pushName, byte[] mediaCiphertextSha256, Boolean multicast, Boolean urlText, Boolean urlNumber, StubType stubType, Boolean clearMedia, List<String> stubParameters, Integer duration, List<String> labels, PaymentInfo paymentInfo, LiveLocationMessage finalLiveLocation, PaymentInfo quotedPaymentInfo, Instant ephemeralStartTimestamp, Integer ephemeralDuration, Boolean ephemeralOffToOn, Boolean ephemeralOutOfSync, BizPrivacyStatus bizPrivacyStatus, String verifiedBizName, MediaData mediaData, PhotoChange photoChange, List<MessageReceipt> receipts, List<Reaction> reactions, MediaData quotedStickerData, byte[] futureproofData, StatusPSA statusPsa, List<PollVoteRecord> pollUpdates, PollAdditionalMetadata pollAdditionalMetadata, String agentId, Boolean statusAlreadyViewed, byte[] messageSecret, KeepInChat keepInChat, Jid originalSelfAuthorUserJidString, Instant revokeMessageTimestamp, PinInChat pinInChat, PremiumMessageInfo premiumMessageInfo, Boolean is1PBizBotMessage, Boolean isGroupHistoryMessage, Jid botMessageInvokerJid, ChatCommentMetadata commentMetadata, List<EventResponse> eventResponses, MessageReportingTokenInfo reportingTokenInfo, Long newsletterServerId, EventAdditionalMetadata eventAdditionalMetadata, Boolean isMentionedInStatus, List<String> statusMentions, MessageKey targetMessageId, List<MessageAddOn> messageAddOns, StatusMentionMessage statusMentionMessageInfo, Boolean isSupportAiMessage, List<String> statusMentionSources, List<MessageCitation> supportAiCitations, String botTargetId, GroupHistoryIndividualMessageInfo groupHistoryIndividualMessageInfo, GroupHistoryBundleInfo groupHistoryBundleInfo, InteractiveMessageAdditionalMetadata interactiveMessageAdditionalMetadata, QuarantinedMessage quarantinedMessage, Integer nonJidMentions) {
        this.key = Objects.requireNonNull(key);
        this.message = messageContainer;
        this.timestamp = messageTimestamp;
        this.status = status;
        this.senderJid = senderJid;
        if(key.senderJid().isEmpty()) {

        }
        this.c2STimestamp = c2STimestamp;
        this.ignore = ignore;
        this.starred = starred;
        this.broadcast = broadcast;
        this.pushName = pushName;
        this.mediaCiphertextSha256 = mediaCiphertextSha256;
        this.multicast = multicast;
        this.urlText = urlText;
        this.urlNumber = urlNumber;
        this.stubType = stubType;
        this.clearMedia = clearMedia;
        this.stubParameters = stubParameters;
        this.duration = duration;
        this.labels = labels;
        this.paymentInfo = paymentInfo;
        this.finalLiveLocation = finalLiveLocation;
        this.quotedPaymentInfo = quotedPaymentInfo;
        this.ephemeralStartTimestamp = ephemeralStartTimestamp;
        this.ephemeralDuration = ephemeralDuration;
        this.ephemeralOffToOn = ephemeralOffToOn;
        this.ephemeralOutOfSync = ephemeralOutOfSync;
        this.bizPrivacyStatus = bizPrivacyStatus;
        this.verifiedBizName = verifiedBizName;
        this.mediaData = mediaData;
        this.photoChange = photoChange;
        this.receipts = receipts;
        this.reactions = reactions;
        this.quotedStickerData = quotedStickerData;
        this.futureproofData = futureproofData;
        this.statusPsa = statusPsa;
        this.pollUpdates = pollUpdates;
        this.pollAdditionalMetadata = pollAdditionalMetadata;
        this.agentId = agentId;
        this.statusAlreadyViewed = statusAlreadyViewed;
        this.messageSecret = messageSecret;
        this.keepInChat = keepInChat;
        this.originalSelfAuthorUserJidString = originalSelfAuthorUserJidString;
        this.revokeMessageTimestamp = revokeMessageTimestamp;
        this.pinInChat = pinInChat;
        this.premiumMessageInfo = premiumMessageInfo;
        this.is1PBizBotMessage = is1PBizBotMessage;
        this.isGroupHistoryMessage = isGroupHistoryMessage;
        this.botMessageInvokerJid = botMessageInvokerJid;
        this.commentMetadata = commentMetadata;
        this.eventResponses = eventResponses;
        this.reportingTokenInfo = reportingTokenInfo;
        this.newsletterServerId = newsletterServerId;
        this.eventAdditionalMetadata = eventAdditionalMetadata;
        this.isMentionedInStatus = isMentionedInStatus;
        this.statusMentions = statusMentions;
        this.targetMessageId = targetMessageId;
        this.messageAddOns = messageAddOns;
        this.statusMentionMessageInfo = statusMentionMessageInfo;
        this.isSupportAiMessage = isSupportAiMessage;
        this.statusMentionSources = statusMentionSources;
        this.supportAiCitations = supportAiCitations;
        this.botTargetId = botTargetId;
        this.groupHistoryIndividualMessageInfo = groupHistoryIndividualMessageInfo;
        this.groupHistoryBundleInfo = groupHistoryBundleInfo;
        this.interactiveMessageAdditionalMetadata = interactiveMessageAdditionalMetadata;
        this.quarantinedMessage = quarantinedMessage;
        this.nonJidMentions = nonJidMentions;
    }

    @Override
    public MessageKey key() {
        return key;
    }

    @Override
    public MessageContainer message() {
        return message != null ? message : MessageContainer.empty();
    }

    @Override
    public Optional<Instant> timestamp() {
        return Optional.ofNullable(timestamp);
    }

    @Override
    public Optional<MessageStatus> status() {
        return Optional.ofNullable(status);
    }

    public Optional<Jid> senderJid() {
        return Optional.ofNullable(senderJid);
    }

    public Optional<Instant> messageC2STimestamp() {
        return Optional.ofNullable(c2STimestamp);
    }

    public boolean ignore() {
        return ignore != null && ignore;
    }

    @Override
    public boolean starred() {
        return starred != null && starred;
    }

    public boolean broadcast() {
        return broadcast != null && broadcast;
    }

    public Optional<String> pushName() {
        return Optional.ofNullable(pushName);
    }

    public Optional<byte[]> mediaCiphertextSha256() {
        return Optional.ofNullable(mediaCiphertextSha256);
    }

    public boolean multicast() {
        return multicast != null && multicast;
    }

    public boolean urlText() {
        return urlText != null && urlText;
    }

    public boolean urlNumber() {
        return urlNumber != null && urlNumber;
    }

    public Optional<StubType> messageStubType() {
        return Optional.ofNullable(stubType);
    }

    public boolean clearMedia() {
        return clearMedia != null && clearMedia;
    }

    public List<String> messageStubParameters() {
        return stubParameters == null ? List.of() : Collections.unmodifiableList(stubParameters);
    }

    public OptionalInt duration() {
        return duration == null ? OptionalInt.empty() : OptionalInt.of(duration);
    }

    public List<String> labels() {
        return labels == null ? List.of() : Collections.unmodifiableList(labels);
    }

    public Optional<PaymentInfo> paymentInfo() {
        return Optional.ofNullable(paymentInfo);
    }

    public Optional<LiveLocationMessage> finalLiveLocation() {
        return Optional.ofNullable(finalLiveLocation);
    }

    public Optional<PaymentInfo> quotedPaymentInfo() {
        return Optional.ofNullable(quotedPaymentInfo);
    }

    public Optional<Instant> ephemeralStartTimestamp() {
        return Optional.ofNullable(ephemeralStartTimestamp);
    }

    public OptionalInt ephemeralDuration() {
        return ephemeralDuration == null ? OptionalInt.empty() : OptionalInt.of(ephemeralDuration);
    }

    public boolean ephemeralOffToOn() {
        return ephemeralOffToOn != null && ephemeralOffToOn;
    }

    public boolean ephemeralOutOfSync() {
        return ephemeralOutOfSync != null && ephemeralOutOfSync;
    }

    public Optional<BizPrivacyStatus> bizPrivacyStatus() {
        return Optional.ofNullable(bizPrivacyStatus);
    }

    public Optional<String> verifiedBizName() {
        return Optional.ofNullable(verifiedBizName);
    }

    public Optional<MediaData> mediaData() {
        return Optional.ofNullable(mediaData);
    }

    public Optional<PhotoChange> photoChange() {
        return Optional.ofNullable(photoChange);
    }

    @Override
    public List<MessageReceipt> receipts() {
        return receipts == null ? List.of() : Collections.unmodifiableList(receipts);
    }

    public List<Reaction> reactions() {
        return reactions == null ? List.of() : Collections.unmodifiableList(reactions);
    }

    public Optional<MediaData> quotedStickerData() {
        return Optional.ofNullable(quotedStickerData);
    }

    public Optional<byte[]> futureproofData() {
        return Optional.ofNullable(futureproofData);
    }

    public Optional<StatusPSA> statusPsa() {
        return Optional.ofNullable(statusPsa);
    }

    public List<PollVoteRecord> pollUpdates() {
        return pollUpdates == null ? List.of() : Collections.unmodifiableList(pollUpdates);
    }

    public Optional<PollAdditionalMetadata> pollAdditionalMetadata() {
        return Optional.ofNullable(pollAdditionalMetadata);
    }

    public Optional<String> agentId() {
        return Optional.ofNullable(agentId);
    }

    public boolean statusAlreadyViewed() {
        return statusAlreadyViewed != null && statusAlreadyViewed;
    }

    public Optional<byte[]> messageSecret() {
        return Optional.ofNullable(messageSecret);
    }

    public Optional<KeepInChat> keepInChat() {
        return Optional.ofNullable(keepInChat);
    }

    public Optional<Jid> originalSelfAuthorUserJidString() {
        return Optional.ofNullable(originalSelfAuthorUserJidString);
    }

    public Optional<Instant> revokeMessageTimestamp() {
        return Optional.ofNullable(revokeMessageTimestamp);
    }

    public Optional<PinInChat> pinInChat() {
        return Optional.ofNullable(pinInChat);
    }

    public Optional<PremiumMessageInfo> premiumMessageInfo() {
        return Optional.ofNullable(premiumMessageInfo);
    }

    public boolean is1PBizBotMessage() {
        return is1PBizBotMessage != null && is1PBizBotMessage;
    }

    public boolean isGroupHistoryMessage() {
        return isGroupHistoryMessage != null && isGroupHistoryMessage;
    }

    public Optional<Jid> botMessageInvokerJid() {
        return Optional.ofNullable(botMessageInvokerJid);
    }

    public Optional<ChatCommentMetadata> commentMetadata() {
        return Optional.ofNullable(commentMetadata);
    }

    public List<EventResponse> eventResponses() {
        return eventResponses == null ? List.of() : Collections.unmodifiableList(eventResponses);
    }

    public Optional<MessageReportingTokenInfo> reportingTokenInfo() {
        return Optional.ofNullable(reportingTokenInfo);
    }

    public OptionalLong newsletterServerId() {
        return newsletterServerId == null ? OptionalLong.empty() : OptionalLong.of(newsletterServerId);
    }

    public Optional<EventAdditionalMetadata> eventAdditionalMetadata() {
        return Optional.ofNullable(eventAdditionalMetadata);
    }

    public boolean isMentionedInStatus() {
        return isMentionedInStatus != null && isMentionedInStatus;
    }

    public List<String> statusMentions() {
        return statusMentions == null ? List.of() : Collections.unmodifiableList(statusMentions);
    }

    public Optional<MessageKey> targetMessageId() {
        return Optional.ofNullable(targetMessageId);
    }

    public List<MessageAddOn> messageAddOns() {
        return messageAddOns == null ? List.of() : Collections.unmodifiableList(messageAddOns);
    }

    public Optional<StatusMentionMessage> statusMentionMessageInfo() {
        return Optional.ofNullable(statusMentionMessageInfo);
    }

    public boolean isSupportAiMessage() {
        return isSupportAiMessage != null && isSupportAiMessage;
    }

    public List<String> statusMentionSources() {
        return statusMentionSources == null ? List.of() : Collections.unmodifiableList(statusMentionSources);
    }

    public List<MessageCitation> supportAiCitations() {
        return supportAiCitations == null ? List.of() : Collections.unmodifiableList(supportAiCitations);
    }

    public Optional<String> botTargetId() {
        return Optional.ofNullable(botTargetId);
    }

    public Optional<GroupHistoryIndividualMessageInfo> groupHistoryIndividualMessageInfo() {
        return Optional.ofNullable(groupHistoryIndividualMessageInfo);
    }

    public Optional<GroupHistoryBundleInfo> groupHistoryBundleInfo() {
        return Optional.ofNullable(groupHistoryBundleInfo);
    }

    public Optional<InteractiveMessageAdditionalMetadata> interactiveMessageAdditionalMetadata() {
        return Optional.ofNullable(interactiveMessageAdditionalMetadata);
    }

    public Optional<QuarantinedMessage> quarantinedMessage() {
        return Optional.ofNullable(quarantinedMessage);
    }

    public OptionalInt nonJidMentions() {
        return nonJidMentions == null ? OptionalInt.empty() : OptionalInt.of(nonJidMentions);
    }

    public void setKey(MessageKey key) {
        this.key = key;
    }

    public void setMessage(MessageContainer messageContainer) {
        this.message = messageContainer;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }

    public void setStatus(MessageStatus status) {
        this.status = status;
    }

    public void setSenderJid(Jid senderJid) {
        this.senderJid = senderJid;
    }

    public void setC2STimestamp(Instant c2STimestamp) {
        this.c2STimestamp = c2STimestamp;
    }

    public void setIgnore(Boolean ignore) {
        this.ignore = ignore;
    }

    public void setStarred(Boolean starred) {
        this.starred = starred;
    }

    public void setBroadcast(Boolean broadcast) {
        this.broadcast = broadcast;
    }

    public void setPushName(String pushName) {
        this.pushName = pushName;
    }

    public void setMediaCiphertextSha256(byte[] mediaCiphertextSha256) {
        this.mediaCiphertextSha256 = mediaCiphertextSha256;
    }

    public void setMulticast(Boolean multicast) {
        this.multicast = multicast;
    }

    public void setUrlText(Boolean urlText) {
        this.urlText = urlText;
    }

    public void setUrlNumber(Boolean urlNumber) {
        this.urlNumber = urlNumber;
    }

    public void setStubType(StubType stubType) {
        this.stubType = stubType;
    }

    public void setClearMedia(Boolean clearMedia) {
        this.clearMedia = clearMedia;
    }

    public void setStubParameters(List<String> stubParameters) {
        this.stubParameters = stubParameters;
    }

    public void setDuration(Integer duration) {
        this.duration = duration;
    }

    public void setLabels(List<String> labels) {
        this.labels = labels;
    }

    public void setPaymentInfo(PaymentInfo paymentInfo) {
        this.paymentInfo = paymentInfo;
    }

    public void setFinalLiveLocation(LiveLocationMessage finalLiveLocation) {
        this.finalLiveLocation = finalLiveLocation;
    }

    public void setQuotedPaymentInfo(PaymentInfo quotedPaymentInfo) {
        this.quotedPaymentInfo = quotedPaymentInfo;
    }

    public void setEphemeralStartTimestamp(Instant ephemeralStartTimestamp) {
        this.ephemeralStartTimestamp = ephemeralStartTimestamp;
    }

    public void setEphemeralDuration(Integer ephemeralDuration) {
        this.ephemeralDuration = ephemeralDuration;
    }

    public void setEphemeralOffToOn(Boolean ephemeralOffToOn) {
        this.ephemeralOffToOn = ephemeralOffToOn;
    }

    public void setEphemeralOutOfSync(Boolean ephemeralOutOfSync) {
        this.ephemeralOutOfSync = ephemeralOutOfSync;
    }

    public void setBizPrivacyStatus(BizPrivacyStatus bizPrivacyStatus) {
        this.bizPrivacyStatus = bizPrivacyStatus;
    }

    public void setVerifiedBizName(String verifiedBizName) {
        this.verifiedBizName = verifiedBizName;
    }

    public void setMediaData(MediaData mediaData) {
        this.mediaData = mediaData;
    }

    public void setPhotoChange(PhotoChange photoChange) {
        this.photoChange = photoChange;
    }

    public void setReceipts(List<MessageReceipt> messageReceipt) {
        this.receipts = messageReceipt;
    }

    public void setReactions(List<Reaction> reactions) {
        this.reactions = reactions;
    }

    public void setQuotedStickerData(MediaData quotedStickerData) {
        this.quotedStickerData = quotedStickerData;
    }

    public void setFutureproofData(byte[] futureproofData) {
        this.futureproofData = futureproofData;
    }

    public void setStatusPsa(StatusPSA statusPsa) {
        this.statusPsa = statusPsa;
    }

    public void setPollUpdates(List<PollVoteRecord> pollUpdates) {
        this.pollUpdates = pollUpdates;
    }

    public void setPollAdditionalMetadata(PollAdditionalMetadata pollAdditionalMetadata) {
        this.pollAdditionalMetadata = pollAdditionalMetadata;
    }

    public void setAgentId(String agentId) {
        this.agentId = agentId;
    }

    public void setStatusAlreadyViewed(Boolean statusAlreadyViewed) {
        this.statusAlreadyViewed = statusAlreadyViewed;
    }

    public void setMessageSecret(byte[] messageSecret) {
        this.messageSecret = messageSecret;
    }

    public void setKeepInChat(KeepInChat keepInChat) {
        this.keepInChat = keepInChat;
    }

    public void setOriginalSelfAuthorUserJidString(Jid originalSelfAuthorUserJidString) {
        this.originalSelfAuthorUserJidString = originalSelfAuthorUserJidString;
    }

    public void setRevokeMessageTimestamp(Instant revokeMessageTimestamp) {
        this.revokeMessageTimestamp = revokeMessageTimestamp;
    }

    public void setPinInChat(PinInChat pinInChat) {
        this.pinInChat = pinInChat;
    }

    public void setPremiumMessageInfo(PremiumMessageInfo premiumMessageInfo) {
        this.premiumMessageInfo = premiumMessageInfo;
    }

    public void set1PBizBotMessage(Boolean is1PBizBotMessage) {
        this.is1PBizBotMessage = is1PBizBotMessage;
    }

    public void setGroupHistoryMessage(Boolean isGroupHistoryMessage) {
        this.isGroupHistoryMessage = isGroupHistoryMessage;
    }

    public void setBotMessageInvokerJid(Jid botMessageInvokerJid) {
        this.botMessageInvokerJid = botMessageInvokerJid;
    }

    public void setCommentMetadata(ChatCommentMetadata commentMetadata) {
        this.commentMetadata = commentMetadata;
    }

    public void setEventResponses(List<EventResponse> eventResponses) {
        this.eventResponses = eventResponses;
    }

    public void setReportingTokenInfo(MessageReportingTokenInfo reportingTokenInfo) {
        this.reportingTokenInfo = reportingTokenInfo;
    }

    public void setNewsletterServerId(Long newsletterServerId) {
        this.newsletterServerId = newsletterServerId;
    }

    public void setEventAdditionalMetadata(EventAdditionalMetadata eventAdditionalMetadata) {
        this.eventAdditionalMetadata = eventAdditionalMetadata;
    }

    public void setMentionedInStatus(Boolean isMentionedInStatus) {
        this.isMentionedInStatus = isMentionedInStatus;
    }

    public void setStatusMentions(List<String> statusMentions) {
        this.statusMentions = statusMentions;
    }

    public void setTargetMessageId(MessageKey targetMessageId) {
        this.targetMessageId = targetMessageId;
    }

    public void setMessageAddOns(List<MessageAddOn> messageAddOns) {
        this.messageAddOns = messageAddOns;
    }

    public void setStatusMentionMessageInfo(StatusMentionMessage statusMentionMessageInfo) {
        this.statusMentionMessageInfo = statusMentionMessageInfo;
    }

    public void setSupportAiMessage(Boolean isSupportAiMessage) {
        this.isSupportAiMessage = isSupportAiMessage;
    }

    public void setStatusMentionSources(List<String> statusMentionSources) {
        this.statusMentionSources = statusMentionSources;
    }

    public void setSupportAiCitations(List<MessageCitation> supportAiCitations) {
        this.supportAiCitations = supportAiCitations;
    }

    public void setBotTargetId(String botTargetId) {
        this.botTargetId = botTargetId;
    }

    public void setGroupHistoryIndividualMessageInfo(GroupHistoryIndividualMessageInfo groupHistoryIndividualMessageInfo) {
        this.groupHistoryIndividualMessageInfo = groupHistoryIndividualMessageInfo;
    }

    public void setGroupHistoryBundleInfo(GroupHistoryBundleInfo groupHistoryBundleInfo) {
        this.groupHistoryBundleInfo = groupHistoryBundleInfo;
    }

    public void setInteractiveMessageAdditionalMetadata(InteractiveMessageAdditionalMetadata interactiveMessageAdditionalMetadata) {
        this.interactiveMessageAdditionalMetadata = interactiveMessageAdditionalMetadata;
    }

    public void setQuarantinedMessage(QuarantinedMessage quarantinedMessage) {
        this.quarantinedMessage = quarantinedMessage;
    }

    public void setNonJidMentions(Integer nonJidMentions) {
        this.nonJidMentions = nonJidMentions;
    }

    @ProtobufEnum(name = "WebMessageInfo.BizPrivacyStatus")
    public static enum BizPrivacyStatus {
        E2EE(0),
        FB(2),
        BSP(1),
        BSP_AND_FB(3);

        BizPrivacyStatus(@ProtobufEnumIndex int index) {
            this.index = index;
        }

        final int index;

        public int index() {
            return this.index;
        }
    }

    @ProtobufEnum(name = "WebMessageInfo.StubType")
    public static enum StubType {
        UNKNOWN(0),
        REVOKE(1),
        CIPHERTEXT(2),
        FUTUREPROOF(3),
        NON_VERIFIED_TRANSITION(4),
        UNVERIFIED_TRANSITION(5),
        VERIFIED_TRANSITION(6),
        VERIFIED_LOW_UNKNOWN(7),
        VERIFIED_HIGH(8),
        VERIFIED_INITIAL_UNKNOWN(9),
        VERIFIED_INITIAL_LOW(10),
        VERIFIED_INITIAL_HIGH(11),
        VERIFIED_TRANSITION_ANY_TO_NONE(12),
        VERIFIED_TRANSITION_ANY_TO_HIGH(13),
        VERIFIED_TRANSITION_HIGH_TO_LOW(14),
        VERIFIED_TRANSITION_HIGH_TO_UNKNOWN(15),
        VERIFIED_TRANSITION_UNKNOWN_TO_LOW(16),
        VERIFIED_TRANSITION_LOW_TO_UNKNOWN(17),
        VERIFIED_TRANSITION_NONE_TO_LOW(18),
        VERIFIED_TRANSITION_NONE_TO_UNKNOWN(19),
        GROUP_CREATE(20),
        GROUP_CHANGE_SUBJECT(21),
        GROUP_CHANGE_ICON(22),
        GROUP_CHANGE_INVITE_LINK(23),
        GROUP_CHANGE_DESCRIPTION(24),
        GROUP_CHANGE_RESTRICT(25),
        GROUP_CHANGE_ANNOUNCE(26),
        GROUP_PARTICIPANT_ADD(27),
        GROUP_PARTICIPANT_REMOVE(28),
        GROUP_PARTICIPANT_PROMOTE(29),
        GROUP_PARTICIPANT_DEMOTE(30),
        GROUP_PARTICIPANT_INVITE(31),
        GROUP_PARTICIPANT_LEAVE(32),
        GROUP_PARTICIPANT_CHANGE_NUMBER(33),
        BROADCAST_CREATE(34),
        BROADCAST_ADD(35),
        BROADCAST_REMOVE(36),
        GENERIC_NOTIFICATION(37),
        E2E_IDENTITY_CHANGED(38),
        E2E_ENCRYPTED(39),
        CALL_MISSED_VOICE(40),
        CALL_MISSED_VIDEO(41),
        INDIVIDUAL_CHANGE_NUMBER(42),
        GROUP_DELETE(43),
        GROUP_ANNOUNCE_MODE_MESSAGE_BOUNCE(44),
        CALL_MISSED_GROUP_VOICE(45),
        CALL_MISSED_GROUP_VIDEO(46),
        PAYMENT_CIPHERTEXT(47),
        PAYMENT_FUTUREPROOF(48),
        PAYMENT_TRANSACTION_STATUS_UPDATE_FAILED(49),
        PAYMENT_TRANSACTION_STATUS_UPDATE_REFUNDED(50),
        PAYMENT_TRANSACTION_STATUS_UPDATE_REFUND_FAILED(51),
        PAYMENT_TRANSACTION_STATUS_RECEIVER_PENDING_SETUP(52),
        PAYMENT_TRANSACTION_STATUS_RECEIVER_SUCCESS_AFTER_HICCUP(53),
        PAYMENT_ACTION_ACCOUNT_SETUP_REMINDER(54),
        PAYMENT_ACTION_SEND_PAYMENT_REMINDER(55),
        PAYMENT_ACTION_SEND_PAYMENT_INVITATION(56),
        PAYMENT_ACTION_REQUEST_DECLINED(57),
        PAYMENT_ACTION_REQUEST_EXPIRED(58),
        PAYMENT_ACTION_REQUEST_CANCELLED(59),
        BIZ_VERIFIED_TRANSITION_TOP_TO_BOTTOM(60),
        BIZ_VERIFIED_TRANSITION_BOTTOM_TO_TOP(61),
        BIZ_INTRO_TOP(62),
        BIZ_INTRO_BOTTOM(63),
        BIZ_NAME_CHANGE(64),
        BIZ_MOVE_TO_CONSUMER_APP(65),
        BIZ_TWO_TIER_MIGRATION_TOP(66),
        BIZ_TWO_TIER_MIGRATION_BOTTOM(67),
        OVERSIZED(68),
        GROUP_CHANGE_NO_FREQUENTLY_FORWARDED(69),
        GROUP_V4_ADD_INVITE_SENT(70),
        GROUP_PARTICIPANT_ADD_REQUEST_JOIN(71),
        CHANGE_EPHEMERAL_SETTING(72),
        E2E_DEVICE_CHANGED(73),
        VIEWED_ONCE(74),
        E2E_ENCRYPTED_NOW(75),
        BLUE_MSG_BSP_FB_TO_BSP_PREMISE(76),
        BLUE_MSG_BSP_FB_TO_SELF_FB(77),
        BLUE_MSG_BSP_FB_TO_SELF_PREMISE(78),
        BLUE_MSG_BSP_FB_UNVERIFIED(79),
        BLUE_MSG_BSP_FB_UNVERIFIED_TO_SELF_PREMISE_VERIFIED(80),
        BLUE_MSG_BSP_FB_VERIFIED(81),
        BLUE_MSG_BSP_FB_VERIFIED_TO_SELF_PREMISE_UNVERIFIED(82),
        BLUE_MSG_BSP_PREMISE_TO_SELF_PREMISE(83),
        BLUE_MSG_BSP_PREMISE_UNVERIFIED(84),
        BLUE_MSG_BSP_PREMISE_UNVERIFIED_TO_SELF_PREMISE_VERIFIED(85),
        BLUE_MSG_BSP_PREMISE_VERIFIED(86),
        BLUE_MSG_BSP_PREMISE_VERIFIED_TO_SELF_PREMISE_UNVERIFIED(87),
        BLUE_MSG_CONSUMER_TO_BSP_FB_UNVERIFIED(88),
        BLUE_MSG_CONSUMER_TO_BSP_PREMISE_UNVERIFIED(89),
        BLUE_MSG_CONSUMER_TO_SELF_FB_UNVERIFIED(90),
        BLUE_MSG_CONSUMER_TO_SELF_PREMISE_UNVERIFIED(91),
        BLUE_MSG_SELF_FB_TO_BSP_PREMISE(92),
        BLUE_MSG_SELF_FB_TO_SELF_PREMISE(93),
        BLUE_MSG_SELF_FB_UNVERIFIED(94),
        BLUE_MSG_SELF_FB_UNVERIFIED_TO_SELF_PREMISE_VERIFIED(95),
        BLUE_MSG_SELF_FB_VERIFIED(96),
        BLUE_MSG_SELF_FB_VERIFIED_TO_SELF_PREMISE_UNVERIFIED(97),
        BLUE_MSG_SELF_PREMISE_TO_BSP_PREMISE(98),
        BLUE_MSG_SELF_PREMISE_UNVERIFIED(99),
        BLUE_MSG_SELF_PREMISE_VERIFIED(100),
        BLUE_MSG_TO_BSP_FB(101),
        BLUE_MSG_TO_CONSUMER(102),
        BLUE_MSG_TO_SELF_FB(103),
        BLUE_MSG_UNVERIFIED_TO_BSP_FB_VERIFIED(104),
        BLUE_MSG_UNVERIFIED_TO_BSP_PREMISE_VERIFIED(105),
        BLUE_MSG_UNVERIFIED_TO_SELF_FB_VERIFIED(106),
        BLUE_MSG_UNVERIFIED_TO_VERIFIED(107),
        BLUE_MSG_VERIFIED_TO_BSP_FB_UNVERIFIED(108),
        BLUE_MSG_VERIFIED_TO_BSP_PREMISE_UNVERIFIED(109),
        BLUE_MSG_VERIFIED_TO_SELF_FB_UNVERIFIED(110),
        BLUE_MSG_VERIFIED_TO_UNVERIFIED(111),
        BLUE_MSG_BSP_FB_UNVERIFIED_TO_BSP_PREMISE_VERIFIED(112),
        BLUE_MSG_BSP_FB_UNVERIFIED_TO_SELF_FB_VERIFIED(113),
        BLUE_MSG_BSP_FB_VERIFIED_TO_BSP_PREMISE_UNVERIFIED(114),
        BLUE_MSG_BSP_FB_VERIFIED_TO_SELF_FB_UNVERIFIED(115),
        BLUE_MSG_SELF_FB_UNVERIFIED_TO_BSP_PREMISE_VERIFIED(116),
        BLUE_MSG_SELF_FB_VERIFIED_TO_BSP_PREMISE_UNVERIFIED(117),
        E2E_IDENTITY_UNAVAILABLE(118),
        GROUP_CREATING(119),
        GROUP_CREATE_FAILED(120),
        GROUP_BOUNCED(121),
        BLOCK_CONTACT(122),
        EPHEMERAL_SETTING_NOT_APPLIED(123),
        SYNC_FAILED(124),
        SYNCING(125),
        BIZ_PRIVACY_MODE_INIT_FB(126),
        BIZ_PRIVACY_MODE_INIT_BSP(127),
        BIZ_PRIVACY_MODE_TO_FB(128),
        BIZ_PRIVACY_MODE_TO_BSP(129),
        DISAPPEARING_MODE(130),
        E2E_DEVICE_FETCH_FAILED(131),
        ADMIN_REVOKE(132),
        GROUP_INVITE_LINK_GROWTH_LOCKED(133),
        COMMUNITY_LINK_PARENT_GROUP(134),
        COMMUNITY_LINK_SIBLING_GROUP(135),
        COMMUNITY_LINK_SUB_GROUP(136),
        COMMUNITY_UNLINK_PARENT_GROUP(137),
        COMMUNITY_UNLINK_SIBLING_GROUP(138),
        COMMUNITY_UNLINK_SUB_GROUP(139),
        GROUP_PARTICIPANT_ACCEPT(140),
        GROUP_PARTICIPANT_LINKED_GROUP_JOIN(141),
        COMMUNITY_CREATE(142),
        EPHEMERAL_KEEP_IN_CHAT(143),
        GROUP_MEMBERSHIP_JOIN_APPROVAL_REQUEST(144),
        GROUP_MEMBERSHIP_JOIN_APPROVAL_MODE(145),
        INTEGRITY_UNLINK_PARENT_GROUP(146),
        COMMUNITY_PARTICIPANT_PROMOTE(147),
        COMMUNITY_PARTICIPANT_DEMOTE(148),
        COMMUNITY_PARENT_GROUP_DELETED(149),
        COMMUNITY_LINK_PARENT_GROUP_MEMBERSHIP_APPROVAL(150),
        GROUP_PARTICIPANT_JOINED_GROUP_AND_PARENT_GROUP(151),
        MASKED_THREAD_CREATED(152),
        MASKED_THREAD_UNMASKED(153),
        BIZ_CHAT_ASSIGNMENT(154),
        CHAT_PSA(155),
        CHAT_POLL_CREATION_MESSAGE(156),
        CAG_MASKED_THREAD_CREATED(157),
        COMMUNITY_PARENT_GROUP_SUBJECT_CHANGED(158),
        CAG_INVITE_AUTO_ADD(159),
        BIZ_CHAT_ASSIGNMENT_UNASSIGN(160),
        CAG_INVITE_AUTO_JOINED(161),
        SCHEDULED_CALL_START_MESSAGE(162),
        COMMUNITY_INVITE_RICH(163),
        COMMUNITY_INVITE_AUTO_ADD_RICH(164),
        SUB_GROUP_INVITE_RICH(165),
        SUB_GROUP_PARTICIPANT_ADD_RICH(166),
        COMMUNITY_LINK_PARENT_GROUP_RICH(167),
        COMMUNITY_PARTICIPANT_ADD_RICH(168),
        SILENCED_UNKNOWN_CALLER_AUDIO(169),
        SILENCED_UNKNOWN_CALLER_VIDEO(170),
        GROUP_MEMBER_ADD_MODE(171),
        GROUP_MEMBERSHIP_JOIN_APPROVAL_REQUEST_NON_ADMIN_ADD(172),
        COMMUNITY_CHANGE_DESCRIPTION(173),
        SENDER_INVITE(174),
        RECEIVER_INVITE(175),
        COMMUNITY_ALLOW_MEMBER_ADDED_GROUPS(176),
        PINNED_MESSAGE_IN_CHAT(177),
        PAYMENT_INVITE_SETUP_INVITER(178),
        PAYMENT_INVITE_SETUP_INVITEE_RECEIVE_ONLY(179),
        PAYMENT_INVITE_SETUP_INVITEE_SEND_AND_RECEIVE(180),
        LINKED_GROUP_CALL_START(181),
        REPORT_TO_ADMIN_ENABLED_STATUS(182),
        EMPTY_SUBGROUP_CREATE(183),
        SCHEDULED_CALL_CANCEL(184),
        SUBGROUP_ADMIN_TRIGGERED_AUTO_ADD_RICH(185),
        GROUP_CHANGE_RECENT_HISTORY_SHARING(186),
        PAID_MESSAGE_SERVER_CAMPAIGN_ID(187),
        GENERAL_CHAT_CREATE(188),
        GENERAL_CHAT_ADD(189),
        GENERAL_CHAT_AUTO_ADD_DISABLED(190),
        SUGGESTED_SUBGROUP_ANNOUNCE(191),
        BIZ_BOT_1P_MESSAGING_ENABLED(192),
        CHANGE_USERNAME(193),
        BIZ_COEX_PRIVACY_INIT_SELF(194),
        BIZ_COEX_PRIVACY_TRANSITION_SELF(195),
        SUPPORT_AI_EDUCATION(196),
        BIZ_BOT_3P_MESSAGING_ENABLED(197),
        REMINDER_SETUP_MESSAGE(198),
        REMINDER_SENT_MESSAGE(199),
        REMINDER_CANCEL_MESSAGE(200),
        BIZ_COEX_PRIVACY_INIT(201),
        BIZ_COEX_PRIVACY_TRANSITION(202),
        GROUP_DEACTIVATED(203),
        COMMUNITY_DEACTIVATE_SIBLING_GROUP(204),
        EVENT_UPDATED(205),
        EVENT_CANCELED(206),
        COMMUNITY_OWNER_UPDATED(207),
        COMMUNITY_SUB_GROUP_VISIBILITY_HIDDEN(208),
        CAPI_GROUP_NE2EE_SYSTEM_MESSAGE(209),
        STATUS_MENTION(210),
        USER_CONTROLS_SYSTEM_MESSAGE(211),
        SUPPORT_SYSTEM_MESSAGE(212),
        CHANGE_LID(213),
        BIZ_CUSTOMER_3PD_DATA_SHARING_OPT_IN_MESSAGE(214),
        BIZ_CUSTOMER_3PD_DATA_SHARING_OPT_OUT_MESSAGE(215),
        CHANGE_LIMIT_SHARING(216),
        GROUP_MEMBER_LINK_MODE(217),
        BIZ_AUTOMATICALLY_LABELED_CHAT_SYSTEM_MESSAGE(218),
        PHONE_NUMBER_HIDING_CHAT_DEPRECATED_MESSAGE(219),
        QUARANTINED_MESSAGE(220),
        GROUP_MEMBER_SHARE_GROUP_HISTORY_MODE(221),
        GROUP_OPEN_BOT_ADDED(222),
        GROUP_TEE_BOT_ADDED(223);

        StubType(@ProtobufEnumIndex int index) {
            this.index = index;
        }

        final int index;

        public int index() {
            return this.index;
        }
    }

    @ProtobufMessage(name = "KeepInChat")
    public static final class KeepInChat {
        @ProtobufProperty(index = 1, type = ProtobufType.ENUM)
        ChatKeepType keepType;

        @ProtobufProperty(index = 2, type = ProtobufType.INT64, mixins = InstantSecondsMixin.class)
        Instant serverTimestamp;

        @ProtobufProperty(index = 3, type = ProtobufType.MESSAGE)
        MessageKey key;

        @ProtobufProperty(index = 4, type = ProtobufType.STRING)
        Jid deviceJid;

        @ProtobufProperty(index = 5, type = ProtobufType.INT64, mixins = InstantMillisMixin.class)
        Instant clientTimestampMs;

        @ProtobufProperty(index = 6, type = ProtobufType.INT64, mixins = InstantMillisMixin.class)
        Instant serverTimestampMs;


        KeepInChat(ChatKeepType keepType, Instant serverTimestamp, MessageKey key, Jid deviceJid, Instant clientTimestampMs, Instant serverTimestampMs) {
            this.keepType = keepType;
            this.serverTimestamp = serverTimestamp;
            this.key = key;
            this.deviceJid = deviceJid;
            this.clientTimestampMs = clientTimestampMs;
            this.serverTimestampMs = serverTimestampMs;
        }

        public Optional<ChatKeepType> keepType() {
            return Optional.ofNullable(keepType);
        }

        public Optional<Instant> serverTimestamp() {
            return Optional.ofNullable(serverTimestamp);
        }

        public Optional<MessageKey> key() {
            return Optional.ofNullable(key);
        }

        public Optional<Jid> deviceJid() {
            return Optional.ofNullable(deviceJid);
        }

        public Optional<Instant> clientTimestampMs() {
            return Optional.ofNullable(clientTimestampMs);
        }

        public Optional<Instant> serverTimestampMs() {
            return Optional.ofNullable(serverTimestampMs);
        }

        public void setKeepType(ChatKeepType keepType) {
            this.keepType = keepType;
    }

        public void setServerTimestamp(Instant serverTimestamp) {
            this.serverTimestamp = serverTimestamp;
    }

        public void setKey(MessageKey key) {
            this.key = key;
    }

        public void setDeviceJid(Jid deviceJid) {
            this.deviceJid = deviceJid;
    }

        public void setClientTimestampMs(Instant clientTimestampMs) {
            this.clientTimestampMs = clientTimestampMs;
    }

        public void setServerTimestampMs(Instant serverTimestampMs) {
            this.serverTimestampMs = serverTimestampMs;
    }
    }

    @ProtobufMessage(name = "PhotoChange")
    public static final class PhotoChange {
        @ProtobufProperty(index = 1, type = ProtobufType.BYTES)
        byte[] oldPhoto;

        @ProtobufProperty(index = 2, type = ProtobufType.BYTES)
        byte[] newPhoto;

        @ProtobufProperty(index = 3, type = ProtobufType.UINT32)
        Integer newPhotoId;


        PhotoChange(byte[] oldPhoto, byte[] newPhoto, Integer newPhotoId) {
            this.oldPhoto = oldPhoto;
            this.newPhoto = newPhoto;
            this.newPhotoId = newPhotoId;
        }

        public Optional<byte[]> oldPhoto() {
            return Optional.ofNullable(oldPhoto);
        }

        public Optional<byte[]> newPhoto() {
            return Optional.ofNullable(newPhoto);
        }

        public OptionalInt newPhotoId() {
            return newPhotoId == null ? OptionalInt.empty() : OptionalInt.of(newPhotoId);
        }

        public void setOldPhoto(byte[] oldPhoto) {
            this.oldPhoto = oldPhoto;
    }

        public void setNewPhoto(byte[] newPhoto) {
            this.newPhoto = newPhoto;
    }

        public void setNewPhotoId(Integer newPhotoId) {
            this.newPhotoId = newPhotoId;
    }
    }

    @ProtobufMessage(name = "PinInChat")
    public static final class PinInChat {
        @ProtobufProperty(index = 1, type = ProtobufType.ENUM)
        Type type;

        @ProtobufProperty(index = 2, type = ProtobufType.MESSAGE)
        MessageKey key;

        @ProtobufProperty(index = 3, type = ProtobufType.INT64, mixins = InstantMillisMixin.class)
        Instant senderTimestampMs;

        @ProtobufProperty(index = 4, type = ProtobufType.INT64, mixins = InstantMillisMixin.class)
        Instant serverTimestampMs;

        @ProtobufProperty(index = 5, type = ProtobufType.MESSAGE)
        MessageAddOnContextInfo messageAddOnContextInfo;


        PinInChat(Type type, MessageKey key, Instant senderTimestampMs, Instant serverTimestampMs, MessageAddOnContextInfo messageAddOnContextInfo) {
            this.type = type;
            this.key = key;
            this.senderTimestampMs = senderTimestampMs;
            this.serverTimestampMs = serverTimestampMs;
            this.messageAddOnContextInfo = messageAddOnContextInfo;
        }

        public Optional<Type> type() {
            return Optional.ofNullable(type);
        }

        public Optional<MessageKey> key() {
            return Optional.ofNullable(key);
        }

        public Optional<Instant> senderTimestampMs() {
            return Optional.ofNullable(senderTimestampMs);
        }

        public Optional<Instant> serverTimestampMs() {
            return Optional.ofNullable(serverTimestampMs);
        }

        public Optional<MessageAddOnContextInfo> messageAddOnContextInfo() {
            return Optional.ofNullable(messageAddOnContextInfo);
        }

        public void setType(Type type) {
            this.type = type;
    }

        public void setKey(MessageKey key) {
            this.key = key;
    }

        public void setSenderTimestampMs(Instant senderTimestampMs) {
            this.senderTimestampMs = senderTimestampMs;
    }

        public void setServerTimestampMs(Instant serverTimestampMs) {
            this.serverTimestampMs = serverTimestampMs;
    }

        public void setMessageAddOnContextInfo(MessageAddOnContextInfo messageAddOnContextInfo) {
            this.messageAddOnContextInfo = messageAddOnContextInfo;
    }

        @ProtobufEnum(name = "PinInChat.Type")
        public static enum Type {
            UNKNOWN_TYPE(0),
            PIN_FOR_ALL(1),
            UNPIN_FOR_ALL(2);

            Type(@ProtobufEnumIndex int index) {
                this.index = index;
            }

            final int index;

            public int index() {
                return this.index;
            }
        }
    }
}
