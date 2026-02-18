package com.github.auties00.cobalt.model.message;

import com.github.auties00.cobalt.model.bot.session.BotMessageSharingInfo;
import com.github.auties00.cobalt.model.bot.session.ForwardedAIBotMessageInfo;
import com.github.auties00.cobalt.model.chat.ChatDisappearingMode;
import com.github.auties00.cobalt.model.chat.group.GroupParticipantLabel;
import com.github.auties00.cobalt.model.chat.group.GroupMention;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.message.interactive.InteractiveAction;
import com.github.auties00.cobalt.model.message.interactive.InteractiveActionLink;
import com.github.auties00.cobalt.model.message.interactive.UrlTrackingMap;
import com.github.auties00.cobalt.model.message.status.StatusAttribution;
import com.github.auties00.cobalt.model.mixin.InstantSecondsMixin;
import it.auties.protobuf.annotation.ProtobufEnum;
import it.auties.protobuf.annotation.ProtobufEnumIndex;
import it.auties.protobuf.annotation.ProtobufMessage;
import it.auties.protobuf.annotation.ProtobufProperty;
import it.auties.protobuf.model.ProtobufType;

import java.time.Instant;
import java.util.*;

@ProtobufMessage(name = "ContextInfo")
public final class ContextInfo {
    @ProtobufProperty(index = 1, type = ProtobufType.STRING)
    String stanzaId;

    @ProtobufProperty(index = 2, type = ProtobufType.STRING)
    String participant;

    @ProtobufProperty(index = 3, type = ProtobufType.MESSAGE)
    MessageContainer quotedMessageContainer;

    @ProtobufProperty(index = 4, type = ProtobufType.STRING)
    Jid remoteJid;

    @ProtobufProperty(index = 15, type = ProtobufType.STRING)
    List<Jid> mentionedJid;

    @ProtobufProperty(index = 18, type = ProtobufType.STRING)
    String conversionSource;

    @ProtobufProperty(index = 19, type = ProtobufType.BYTES)
    byte[] conversionData;

    @ProtobufProperty(index = 20, type = ProtobufType.UINT32)
    Integer conversionDelaySeconds;

    @ProtobufProperty(index = 21, type = ProtobufType.UINT32)
    Integer forwardingScore;

    @ProtobufProperty(index = 22, type = ProtobufType.BOOL)
    Boolean isForwarded;

    @ProtobufProperty(index = 23, type = ProtobufType.MESSAGE)
    AdReplyInfo quotedAd;

    @ProtobufProperty(index = 24, type = ProtobufType.MESSAGE)
    MessageKey placeholderKey;

    @ProtobufProperty(index = 25, type = ProtobufType.UINT32)
    Integer expiration;

    @ProtobufProperty(index = 26, type = ProtobufType.INT64, mixins = InstantSecondsMixin.class)
    Instant ephemeralSettingTimestamp;

    @ProtobufProperty(index = 27, type = ProtobufType.BYTES)
    byte[] ephemeralSharedSecret;

    @ProtobufProperty(index = 28, type = ProtobufType.MESSAGE)
    ExternalAdReplyInfo externalAdReply;

    @ProtobufProperty(index = 29, type = ProtobufType.STRING)
    String entryPointConversionSource;

    @ProtobufProperty(index = 30, type = ProtobufType.STRING)
    String entryPointConversionApp;

    @ProtobufProperty(index = 31, type = ProtobufType.UINT32)
    Integer entryPointConversionDelaySeconds;

    @ProtobufProperty(index = 32, type = ProtobufType.MESSAGE)
    ChatDisappearingMode disappearingMode;

    @ProtobufProperty(index = 33, type = ProtobufType.MESSAGE)
    InteractiveActionLink actionLink;

    @ProtobufProperty(index = 34, type = ProtobufType.STRING)
    String groupSubject;

    @ProtobufProperty(index = 35, type = ProtobufType.STRING)
    Jid parentGroupJid;

    @ProtobufProperty(index = 37, type = ProtobufType.STRING)
    String trustBannerType;

    @ProtobufProperty(index = 38, type = ProtobufType.UINT32)
    Integer trustBannerAction;

    @ProtobufProperty(index = 39, type = ProtobufType.BOOL)
    Boolean isSampled;

    @ProtobufProperty(index = 40, type = ProtobufType.MESSAGE)
    List<GroupMention> groupMentions;

    @ProtobufProperty(index = 41, type = ProtobufType.MESSAGE)
    UTMInfo utm;

    @ProtobufProperty(index = 43, type = ProtobufType.MESSAGE)
    ForwardedNewsletterMessageInfo forwardedNewsletterMessageInfo;

    @ProtobufProperty(index = 44, type = ProtobufType.MESSAGE)
    BusinessMessageForwardInfo businessMessageForwardInfo;

    @ProtobufProperty(index = 45, type = ProtobufType.STRING)
    String smbClientCampaignId;

    @ProtobufProperty(index = 46, type = ProtobufType.STRING)
    String smbServerCampaignId;

    @ProtobufProperty(index = 47, type = ProtobufType.MESSAGE)
    DataSharingContext dataSharingContext;

    @ProtobufProperty(index = 48, type = ProtobufType.BOOL)
    Boolean alwaysShowAdAttribution;

    @ProtobufProperty(index = 49, type = ProtobufType.MESSAGE)
    FeatureEligibilities featureEligibilities;

    @ProtobufProperty(index = 50, type = ProtobufType.STRING)
    String entryPointConversionExternalSource;

    @ProtobufProperty(index = 51, type = ProtobufType.STRING)
    String entryPointConversionExternalMedium;

    @ProtobufProperty(index = 54, type = ProtobufType.STRING)
    String ctwaSignals;

    @ProtobufProperty(index = 55, type = ProtobufType.BYTES)
    byte[] ctwaPayload;

    @ProtobufProperty(index = 56, type = ProtobufType.MESSAGE)
    ForwardedAIBotMessageInfo forwardedAiBotMessageInfo;

    @ProtobufProperty(index = 57, type = ProtobufType.ENUM)
    StatusAttributionType statusAttributionType;

    @ProtobufProperty(index = 58, type = ProtobufType.MESSAGE)
    UrlTrackingMap urlTrackingMap;

    @ProtobufProperty(index = 59, type = ProtobufType.ENUM)
    PairedMediaType pairedMediaType;

    @ProtobufProperty(index = 60, type = ProtobufType.UINT32)
    Integer rankingVersion;

    @ProtobufProperty(index = 62, type = ProtobufType.MESSAGE)
    GroupParticipantLabel participantLabel;

    @ProtobufProperty(index = 63, type = ProtobufType.BOOL)
    Boolean isQuestion;

    @ProtobufProperty(index = 64, type = ProtobufType.ENUM)
    StatusSourceType statusSourceType;

    @ProtobufProperty(index = 65, type = ProtobufType.MESSAGE)
    List<StatusAttribution> statusAttributions;

    @ProtobufProperty(index = 66, type = ProtobufType.BOOL)
    Boolean isGroupStatus;

    @ProtobufProperty(index = 67, type = ProtobufType.ENUM)
    ForwardOrigin forwardOrigin;

    @ProtobufProperty(index = 68, type = ProtobufType.MESSAGE)
    QuestionReplyQuotedMessage questionReplyQuotedMessage;

    @ProtobufProperty(index = 69, type = ProtobufType.MESSAGE)
    StatusAudienceMetadata statusAudienceMetadata;

    @ProtobufProperty(index = 70, type = ProtobufType.UINT32)
    Integer nonJidMentions;

    @ProtobufProperty(index = 71, type = ProtobufType.ENUM)
    QuotedType quotedType;

    @ProtobufProperty(index = 72, type = ProtobufType.MESSAGE)
    BotMessageSharingInfo botMessageSharingInfo;


    ContextInfo(String stanzaId, String participant, MessageContainer quotedMessageContainer, Jid remoteJid, List<Jid> mentionedJid, String conversionSource, byte[] conversionData, Integer conversionDelaySeconds, Integer forwardingScore, Boolean isForwarded, AdReplyInfo quotedAd, MessageKey placeholderKey, Integer expiration, Instant ephemeralSettingTimestamp, byte[] ephemeralSharedSecret, ExternalAdReplyInfo externalAdReply, String entryPointConversionSource, String entryPointConversionApp, Integer entryPointConversionDelaySeconds, ChatDisappearingMode disappearingMode, InteractiveActionLink actionLink, String groupSubject, Jid parentGroupJid, String trustBannerType, Integer trustBannerAction, Boolean isSampled, List<GroupMention> groupMentions, UTMInfo utm, ForwardedNewsletterMessageInfo forwardedNewsletterMessageInfo, BusinessMessageForwardInfo businessMessageForwardInfo, String smbClientCampaignId, String smbServerCampaignId, DataSharingContext dataSharingContext, Boolean alwaysShowAdAttribution, FeatureEligibilities featureEligibilities, String entryPointConversionExternalSource, String entryPointConversionExternalMedium, String ctwaSignals, byte[] ctwaPayload, ForwardedAIBotMessageInfo forwardedAiBotMessageInfo, StatusAttributionType statusAttributionType, UrlTrackingMap urlTrackingMap, PairedMediaType pairedMediaType, Integer rankingVersion, GroupParticipantLabel participantLabel, Boolean isQuestion, StatusSourceType statusSourceType, List<StatusAttribution> statusAttributions, Boolean isGroupStatus, ForwardOrigin forwardOrigin, QuestionReplyQuotedMessage questionReplyQuotedMessage, StatusAudienceMetadata statusAudienceMetadata, Integer nonJidMentions, QuotedType quotedType, BotMessageSharingInfo botMessageSharingInfo) {
        this.stanzaId = stanzaId;
        this.participant = participant;
        this.quotedMessageContainer = quotedMessageContainer;
        this.remoteJid = remoteJid;
        this.mentionedJid = mentionedJid;
        this.conversionSource = conversionSource;
        this.conversionData = conversionData;
        this.conversionDelaySeconds = conversionDelaySeconds;
        this.forwardingScore = forwardingScore;
        this.isForwarded = isForwarded;
        this.quotedAd = quotedAd;
        this.placeholderKey = placeholderKey;
        this.expiration = expiration;
        this.ephemeralSettingTimestamp = ephemeralSettingTimestamp;
        this.ephemeralSharedSecret = ephemeralSharedSecret;
        this.externalAdReply = externalAdReply;
        this.entryPointConversionSource = entryPointConversionSource;
        this.entryPointConversionApp = entryPointConversionApp;
        this.entryPointConversionDelaySeconds = entryPointConversionDelaySeconds;
        this.disappearingMode = disappearingMode;
        this.actionLink = actionLink;
        this.groupSubject = groupSubject;
        this.parentGroupJid = parentGroupJid;
        this.trustBannerType = trustBannerType;
        this.trustBannerAction = trustBannerAction;
        this.isSampled = isSampled;
        this.groupMentions = groupMentions;
        this.utm = utm;
        this.forwardedNewsletterMessageInfo = forwardedNewsletterMessageInfo;
        this.businessMessageForwardInfo = businessMessageForwardInfo;
        this.smbClientCampaignId = smbClientCampaignId;
        this.smbServerCampaignId = smbServerCampaignId;
        this.dataSharingContext = dataSharingContext;
        this.alwaysShowAdAttribution = alwaysShowAdAttribution;
        this.featureEligibilities = featureEligibilities;
        this.entryPointConversionExternalSource = entryPointConversionExternalSource;
        this.entryPointConversionExternalMedium = entryPointConversionExternalMedium;
        this.ctwaSignals = ctwaSignals;
        this.ctwaPayload = ctwaPayload;
        this.forwardedAiBotMessageInfo = forwardedAiBotMessageInfo;
        this.statusAttributionType = statusAttributionType;
        this.urlTrackingMap = urlTrackingMap;
        this.pairedMediaType = pairedMediaType;
        this.rankingVersion = rankingVersion;
        this.participantLabel = participantLabel;
        this.isQuestion = isQuestion;
        this.statusSourceType = statusSourceType;
        this.statusAttributions = statusAttributions;
        this.isGroupStatus = isGroupStatus;
        this.forwardOrigin = forwardOrigin;
        this.questionReplyQuotedMessage = questionReplyQuotedMessage;
        this.statusAudienceMetadata = statusAudienceMetadata;
        this.nonJidMentions = nonJidMentions;
        this.quotedType = quotedType;
        this.botMessageSharingInfo = botMessageSharingInfo;
    }

    public Optional<String> stanzaId() {
        return Optional.ofNullable(stanzaId);
    }

    public Optional<String> participant() {
        return Optional.ofNullable(participant);
    }

    public Optional<MessageContainer> quotedMessage() {
        return Optional.ofNullable(quotedMessageContainer);
    }

    public Optional<Jid> remoteJid() {
        return Optional.ofNullable(remoteJid);
    }

    public List<Jid> mentionedJid() {
        return mentionedJid == null ? List.of() : Collections.unmodifiableList(mentionedJid);
    }

    public Optional<String> conversionSource() {
        return Optional.ofNullable(conversionSource);
    }

    public Optional<byte[]> conversionData() {
        return Optional.ofNullable(conversionData);
    }

    public OptionalInt conversionDelaySeconds() {
        return conversionDelaySeconds == null ? OptionalInt.empty() : OptionalInt.of(conversionDelaySeconds);
    }

    public OptionalInt forwardingScore() {
        return forwardingScore == null ? OptionalInt.empty() : OptionalInt.of(forwardingScore);
    }

    public boolean isForwarded() {
        return isForwarded != null && isForwarded;
    }

    public Optional<AdReplyInfo> quotedAd() {
        return Optional.ofNullable(quotedAd);
    }

    public Optional<MessageKey> placeholderKey() {
        return Optional.ofNullable(placeholderKey);
    }

    public OptionalInt expiration() {
        return expiration == null ? OptionalInt.empty() : OptionalInt.of(expiration);
    }

    public Optional<Instant> ephemeralSettingTimestamp() {
        return Optional.ofNullable(ephemeralSettingTimestamp);
    }

    public Optional<byte[]> ephemeralSharedSecret() {
        return Optional.ofNullable(ephemeralSharedSecret);
    }

    public Optional<ExternalAdReplyInfo> externalAdReply() {
        return Optional.ofNullable(externalAdReply);
    }

    public Optional<String> entryPointConversionSource() {
        return Optional.ofNullable(entryPointConversionSource);
    }

    public Optional<String> entryPointConversionApp() {
        return Optional.ofNullable(entryPointConversionApp);
    }

    public OptionalInt entryPointConversionDelaySeconds() {
        return entryPointConversionDelaySeconds == null ? OptionalInt.empty() : OptionalInt.of(entryPointConversionDelaySeconds);
    }

    public Optional<ChatDisappearingMode> disappearingMode() {
        return Optional.ofNullable(disappearingMode);
    }

    public Optional<InteractiveActionLink> actionLink() {
        return Optional.ofNullable(actionLink);
    }

    public Optional<String> groupSubject() {
        return Optional.ofNullable(groupSubject);
    }

    public Optional<Jid> parentGroupJid() {
        return Optional.ofNullable(parentGroupJid);
    }

    public Optional<String> trustBannerType() {
        return Optional.ofNullable(trustBannerType);
    }

    public OptionalInt trustBannerAction() {
        return trustBannerAction == null ? OptionalInt.empty() : OptionalInt.of(trustBannerAction);
    }

    public boolean isSampled() {
        return isSampled != null && isSampled;
    }

    public List<GroupMention> groupMentions() {
        return groupMentions == null ? List.of() : Collections.unmodifiableList(groupMentions);
    }

    public Optional<UTMInfo> utm() {
        return Optional.ofNullable(utm);
    }

    public Optional<ForwardedNewsletterMessageInfo> forwardedNewsletterMessageInfo() {
        return Optional.ofNullable(forwardedNewsletterMessageInfo);
    }

    public Optional<BusinessMessageForwardInfo> businessMessageForwardInfo() {
        return Optional.ofNullable(businessMessageForwardInfo);
    }

    public Optional<String> smbClientCampaignId() {
        return Optional.ofNullable(smbClientCampaignId);
    }

    public Optional<String> smbServerCampaignId() {
        return Optional.ofNullable(smbServerCampaignId);
    }

    public Optional<DataSharingContext> dataSharingContext() {
        return Optional.ofNullable(dataSharingContext);
    }

    public boolean alwaysShowAdAttribution() {
        return alwaysShowAdAttribution != null && alwaysShowAdAttribution;
    }

    public Optional<FeatureEligibilities> featureEligibilities() {
        return Optional.ofNullable(featureEligibilities);
    }

    public Optional<String> entryPointConversionExternalSource() {
        return Optional.ofNullable(entryPointConversionExternalSource);
    }

    public Optional<String> entryPointConversionExternalMedium() {
        return Optional.ofNullable(entryPointConversionExternalMedium);
    }

    public Optional<String> ctwaSignals() {
        return Optional.ofNullable(ctwaSignals);
    }

    public Optional<byte[]> ctwaPayload() {
        return Optional.ofNullable(ctwaPayload);
    }

    public Optional<ForwardedAIBotMessageInfo> forwardedAiBotMessageInfo() {
        return Optional.ofNullable(forwardedAiBotMessageInfo);
    }

    public Optional<StatusAttributionType> statusAttributionType() {
        return Optional.ofNullable(statusAttributionType);
    }

    public Optional<UrlTrackingMap> urlTrackingMap() {
        return Optional.ofNullable(urlTrackingMap);
    }

    public Optional<PairedMediaType> pairedMediaType() {
        return Optional.ofNullable(pairedMediaType);
    }

    public OptionalInt rankingVersion() {
        return rankingVersion == null ? OptionalInt.empty() : OptionalInt.of(rankingVersion);
    }

    public Optional<GroupParticipantLabel> memberLabel() {
        return Optional.ofNullable(participantLabel);
    }

    public boolean isQuestion() {
        return isQuestion != null && isQuestion;
    }

    public Optional<StatusSourceType> statusSourceType() {
        return Optional.ofNullable(statusSourceType);
    }

    public List<StatusAttribution> statusAttributions() {
        return statusAttributions == null ? List.of() : Collections.unmodifiableList(statusAttributions);
    }

    public boolean isGroupStatus() {
        return isGroupStatus != null && isGroupStatus;
    }

    public Optional<ForwardOrigin> forwardOrigin() {
        return Optional.ofNullable(forwardOrigin);
    }

    public Optional<QuestionReplyQuotedMessage> questionReplyQuotedMessage() {
        return Optional.ofNullable(questionReplyQuotedMessage);
    }

    public Optional<StatusAudienceMetadata> statusAudienceMetadata() {
        return Optional.ofNullable(statusAudienceMetadata);
    }

    public OptionalInt nonJidMentions() {
        return nonJidMentions == null ? OptionalInt.empty() : OptionalInt.of(nonJidMentions);
    }

    public Optional<QuotedType> quotedType() {
        return Optional.ofNullable(quotedType);
    }

    public Optional<BotMessageSharingInfo> botMessageSharingInfo() {
        return Optional.ofNullable(botMessageSharingInfo);
    }

    public ContextInfo setStanzaId(String stanzaId) {
        this.stanzaId = stanzaId;
        return this;
    }

    public ContextInfo setParticipant(String participant) {
        this.participant = participant;
        return this;
    }

    public ContextInfo setQuotedMessage(MessageContainer quotedMessageContainer) {
        this.quotedMessageContainer = quotedMessageContainer;
        return this;
    }

    public ContextInfo setRemoteJid(Jid remoteJid) {
        this.remoteJid = remoteJid;
        return this;
    }

    public ContextInfo setMentionedJid(List<Jid> mentionedJid) {
        this.mentionedJid = mentionedJid;
        return this;
    }

    public ContextInfo setConversionSource(String conversionSource) {
        this.conversionSource = conversionSource;
        return this;
    }

    public ContextInfo setConversionData(byte[] conversionData) {
        this.conversionData = conversionData;
        return this;
    }

    public ContextInfo setConversionDelaySeconds(Integer conversionDelaySeconds) {
        this.conversionDelaySeconds = conversionDelaySeconds;
        return this;
    }

    public ContextInfo setForwardingScore(Integer forwardingScore) {
        this.forwardingScore = forwardingScore;
        return this;
    }

    public ContextInfo setForwarded(Boolean isForwarded) {
        this.isForwarded = isForwarded;
        return this;
    }

    public ContextInfo setQuotedAd(AdReplyInfo quotedAd) {
        this.quotedAd = quotedAd;
        return this;
    }

    public ContextInfo setPlaceholderKey(MessageKey placeholderKey) {
        this.placeholderKey = placeholderKey;
        return this;
    }

    public ContextInfo setExpiration(Integer expiration) {
        this.expiration = expiration;
        return this;
    }

    public ContextInfo setEphemeralSettingTimestamp(Instant ephemeralSettingTimestamp) {
        this.ephemeralSettingTimestamp = ephemeralSettingTimestamp;
        return this;
    }

    public ContextInfo setEphemeralSharedSecret(byte[] ephemeralSharedSecret) {
        this.ephemeralSharedSecret = ephemeralSharedSecret;
        return this;
    }

    public ContextInfo setExternalAdReply(ExternalAdReplyInfo externalAdReply) {
        this.externalAdReply = externalAdReply;
        return this;
    }

    public ContextInfo setEntryPointConversionSource(String entryPointConversionSource) {
        this.entryPointConversionSource = entryPointConversionSource;
        return this;
    }

    public ContextInfo setEntryPointConversionApp(String entryPointConversionApp) {
        this.entryPointConversionApp = entryPointConversionApp;
        return this;
    }

    public ContextInfo setEntryPointConversionDelaySeconds(Integer entryPointConversionDelaySeconds) {
        this.entryPointConversionDelaySeconds = entryPointConversionDelaySeconds;
        return this;
    }

    public ContextInfo setDisappearingMode(ChatDisappearingMode disappearingMode) {
        this.disappearingMode = disappearingMode;
        return this;
    }

    public ContextInfo setActionLink(InteractiveActionLink actionLink) {
        this.actionLink = actionLink;
        return this;
    }

    public ContextInfo setGroupSubject(String groupSubject) {
        this.groupSubject = groupSubject;
        return this;
    }

    public ContextInfo setParentGroupJid(Jid parentGroupJid) {
        this.parentGroupJid = parentGroupJid;
        return this;
    }

    public ContextInfo setTrustBannerType(String trustBannerType) {
        this.trustBannerType = trustBannerType;
        return this;
    }

    public ContextInfo setTrustBannerAction(Integer trustBannerAction) {
        this.trustBannerAction = trustBannerAction;
        return this;
    }

    public ContextInfo setSampled(Boolean isSampled) {
        this.isSampled = isSampled;
        return this;
    }

    public ContextInfo setGroupMentions(List<GroupMention> groupMentions) {
        this.groupMentions = groupMentions;
        return this;
    }

    public ContextInfo setUtm(UTMInfo utm) {
        this.utm = utm;
        return this;
    }

    public ContextInfo setForwardedNewsletterMessageInfo(ForwardedNewsletterMessageInfo forwardedNewsletterMessageInfo) {
        this.forwardedNewsletterMessageInfo = forwardedNewsletterMessageInfo;
        return this;
    }

    public ContextInfo setBusinessMessageForwardInfo(BusinessMessageForwardInfo businessMessageForwardInfo) {
        this.businessMessageForwardInfo = businessMessageForwardInfo;
        return this;
    }

    public ContextInfo setSmbClientCampaignId(String smbClientCampaignId) {
        this.smbClientCampaignId = smbClientCampaignId;
        return this;
    }

    public ContextInfo setSmbServerCampaignId(String smbServerCampaignId) {
        this.smbServerCampaignId = smbServerCampaignId;
        return this;
    }

    public ContextInfo setDataSharingContext(DataSharingContext dataSharingContext) {
        this.dataSharingContext = dataSharingContext;
        return this;
    }

    public ContextInfo setAlwaysShowAdAttribution(Boolean alwaysShowAdAttribution) {
        this.alwaysShowAdAttribution = alwaysShowAdAttribution;
        return this;
    }

    public ContextInfo setFeatureEligibilities(FeatureEligibilities featureEligibilities) {
        this.featureEligibilities = featureEligibilities;
        return this;
    }

    public ContextInfo setEntryPointConversionExternalSource(String entryPointConversionExternalSource) {
        this.entryPointConversionExternalSource = entryPointConversionExternalSource;
        return this;
    }

    public ContextInfo setEntryPointConversionExternalMedium(String entryPointConversionExternalMedium) {
        this.entryPointConversionExternalMedium = entryPointConversionExternalMedium;
        return this;
    }

    public ContextInfo setCtwaSignals(String ctwaSignals) {
        this.ctwaSignals = ctwaSignals;
        return this;
    }

    public ContextInfo setCtwaPayload(byte[] ctwaPayload) {
        this.ctwaPayload = ctwaPayload;
        return this;
    }

    public ContextInfo setForwardedAiBotMessageInfo(ForwardedAIBotMessageInfo forwardedAiBotMessageInfo) {
        this.forwardedAiBotMessageInfo = forwardedAiBotMessageInfo;
        return this;
    }

    public ContextInfo setStatusAttributionType(StatusAttributionType statusAttributionType) {
        this.statusAttributionType = statusAttributionType;
        return this;
    }

    public ContextInfo setUrlTrackingMap(UrlTrackingMap urlTrackingMap) {
        this.urlTrackingMap = urlTrackingMap;
        return this;
    }

    public ContextInfo setPairedMediaType(PairedMediaType pairedMediaType) {
        this.pairedMediaType = pairedMediaType;
        return this;
    }

    public ContextInfo setRankingVersion(Integer rankingVersion) {
        this.rankingVersion = rankingVersion;
        return this;
    }

    public ContextInfo setParticipantLabel(GroupParticipantLabel participantLabel) {
        this.participantLabel = participantLabel;
        return this;
    }

    public ContextInfo setQuestion(Boolean isQuestion) {
        this.isQuestion = isQuestion;
        return this;
    }

    public ContextInfo setStatusSourceType(StatusSourceType statusSourceType) {
        this.statusSourceType = statusSourceType;
        return this;
    }

    public ContextInfo setStatusAttributions(List<StatusAttribution> statusAttributions) {
        this.statusAttributions = statusAttributions;
        return this;
    }

    public ContextInfo setGroupStatus(Boolean isGroupStatus) {
        this.isGroupStatus = isGroupStatus;
        return this;
    }

    public ContextInfo setForwardOrigin(ForwardOrigin forwardOrigin) {
        this.forwardOrigin = forwardOrigin;
        return this;
    }

    public ContextInfo setQuestionReplyQuotedMessage(QuestionReplyQuotedMessage questionReplyQuotedMessage) {
        this.questionReplyQuotedMessage = questionReplyQuotedMessage;
        return this;
    }

    public ContextInfo setStatusAudienceMetadata(StatusAudienceMetadata statusAudienceMetadata) {
        this.statusAudienceMetadata = statusAudienceMetadata;
        return this;
    }

    public ContextInfo setNonJidMentions(Integer nonJidMentions) {
        this.nonJidMentions = nonJidMentions;
        return this;
    }

    public ContextInfo setQuotedType(QuotedType quotedType) {
        this.quotedType = quotedType;
        return this;
    }

    public ContextInfo setBotMessageSharingInfo(BotMessageSharingInfo botMessageSharingInfo) {
        this.botMessageSharingInfo = botMessageSharingInfo;
        return this;
    }

    @ProtobufEnum(name = "ContextInfo.ForwardOrigin")
    public static enum ForwardOrigin {
        UNKNOWN(0),
        CHAT(1),
        STATUS(2),
        CHANNELS(3),
        META_AI(4),
        UGC(5);

        ForwardOrigin(@ProtobufEnumIndex int index) {
            this.index = index;
        }

        final int index;

        public int index() {
            return this.index;
        }
    }

    @ProtobufEnum(name = "ContextInfo.PairedMediaType")
    public static enum PairedMediaType {
        NOT_PAIRED_MEDIA(0),
        SD_VIDEO_PARENT(1),
        HD_VIDEO_CHILD(2),
        SD_IMAGE_PARENT(3),
        HD_IMAGE_CHILD(4),
        MOTION_PHOTO_PARENT(5),
        MOTION_PHOTO_CHILD(6),
        HEVC_VIDEO_PARENT(7),
        HEVC_VIDEO_CHILD(8);

        PairedMediaType(@ProtobufEnumIndex int index) {
            this.index = index;
        }

        final int index;

        public int index() {
            return this.index;
        }
    }

    @ProtobufEnum(name = "ContextInfo.QuotedType")
    public static enum QuotedType {
        EXPLICIT(0),
        AUTO(1);

        QuotedType(@ProtobufEnumIndex int index) {
            this.index = index;
        }

        final int index;

        public int index() {
            return this.index;
        }
    }

    @ProtobufEnum(name = "ContextInfo.StatusAttributionType")
    public static enum StatusAttributionType {
        NONE(0),
        RESHARED_FROM_MENTION(1),
        RESHARED_FROM_POST(2),
        RESHARED_FROM_POST_MANY_TIMES(3),
        FORWARDED_FROM_STATUS(4);

        StatusAttributionType(@ProtobufEnumIndex int index) {
            this.index = index;
        }

        final int index;

        public int index() {
            return this.index;
        }
    }

    @ProtobufEnum(name = "ContextInfo.StatusSourceType")
    public static enum StatusSourceType {
        IMAGE(0),
        VIDEO(1),
        GIF(2),
        AUDIO(3),
        TEXT(4),
        MUSIC_STANDALONE(5);

        StatusSourceType(@ProtobufEnumIndex int index) {
            this.index = index;
        }

        final int index;

        public int index() {
            return this.index;
        }
    }

    @ProtobufMessage(name = "ContextInfo.AdReplyInfo")
    public static final class AdReplyInfo {
        @ProtobufProperty(index = 1, type = ProtobufType.STRING)
        String advertiserName;

        @ProtobufProperty(index = 2, type = ProtobufType.ENUM)
        AdReplyInfo.MediaType mediaType;

        @ProtobufProperty(index = 16, type = ProtobufType.BYTES)
        byte[] jpegThumbnail;

        @ProtobufProperty(index = 17, type = ProtobufType.STRING)
        String caption;


        AdReplyInfo(String advertiserName, MediaType mediaType, byte[] jpegThumbnail, String caption) {
            this.advertiserName = advertiserName;
            this.mediaType = mediaType;
            this.jpegThumbnail = jpegThumbnail;
            this.caption = caption;
        }

        public Optional<String> advertiserName() {
            return Optional.ofNullable(advertiserName);
        }

        public Optional<MediaType> mediaType() {
            return Optional.ofNullable(mediaType);
        }

        public Optional<byte[]> jpegThumbnail() {
            return Optional.ofNullable(jpegThumbnail);
        }

        public Optional<String> caption() {
            return Optional.ofNullable(caption);
        }

        public AdReplyInfo setAdvertiserName(String advertiserName) {
            this.advertiserName = advertiserName;
            return this;
        }

        public AdReplyInfo setMediaType(MediaType mediaType) {
            this.mediaType = mediaType;
            return this;
        }

        public AdReplyInfo setJpegThumbnail(byte[] jpegThumbnail) {
            this.jpegThumbnail = jpegThumbnail;
            return this;
        }

        public AdReplyInfo setCaption(String caption) {
            this.caption = caption;
            return this;
        }

        @ProtobufEnum(name = "ContextInfo.AdReplyInfo.MediaType")
        public static enum MediaType {
            NONE(0),
            IMAGE(1),
            VIDEO(2);

            MediaType(@ProtobufEnumIndex int index) {
                this.index = index;
            }

            final int index;

            public int index() {
                return this.index;
            }
        }
    }

    @ProtobufMessage(name = "ContextInfo.BusinessMessageForwardInfo")
    public static final class BusinessMessageForwardInfo {
        @ProtobufProperty(index = 1, type = ProtobufType.STRING)
        Jid businessOwnerJid;


        BusinessMessageForwardInfo(Jid businessOwnerJid) {
            this.businessOwnerJid = businessOwnerJid;
        }

        public Optional<Jid> businessOwnerJid() {
            return Optional.ofNullable(businessOwnerJid);
        }

        public BusinessMessageForwardInfo setBusinessOwnerJid(Jid businessOwnerJid) {
            this.businessOwnerJid = businessOwnerJid;
            return this;
        }
    }

    @ProtobufMessage(name = "ContextInfo.DataSharingContext")
    public static final class DataSharingContext {
        @ProtobufProperty(index = 1, type = ProtobufType.BOOL)
        Boolean showMmDisclosure;

        @ProtobufProperty(index = 2, type = ProtobufType.STRING)
        String encryptedSignalTokenConsented;

        @ProtobufProperty(index = 3, type = ProtobufType.MESSAGE)
        List<Parameters> parameters;

        @ProtobufProperty(index = 4, type = ProtobufType.INT32)
        Integer dataSharingFlags;


        DataSharingContext(Boolean showMmDisclosure, String encryptedSignalTokenConsented, List<Parameters> parameters, Integer dataSharingFlags) {
            this.showMmDisclosure = showMmDisclosure;
            this.encryptedSignalTokenConsented = encryptedSignalTokenConsented;
            this.parameters = parameters;
            this.dataSharingFlags = dataSharingFlags;
        }

        public boolean showMmDisclosure() {
            return showMmDisclosure != null && showMmDisclosure;
        }

        public Optional<String> encryptedSignalTokenConsented() {
            return Optional.ofNullable(encryptedSignalTokenConsented);
        }

        public List<Parameters> parameters() {
            return parameters == null ? List.of() : Collections.unmodifiableList(parameters);
        }

        public OptionalInt dataSharingFlags() {
            return dataSharingFlags == null ? OptionalInt.empty() : OptionalInt.of(dataSharingFlags);
        }

        public DataSharingContext setShowMmDisclosure(Boolean showMmDisclosure) {
            this.showMmDisclosure = showMmDisclosure;
            return this;
        }

        public DataSharingContext setEncryptedSignalTokenConsented(String encryptedSignalTokenConsented) {
            this.encryptedSignalTokenConsented = encryptedSignalTokenConsented;
            return this;
        }

        public DataSharingContext setParameters(List<Parameters> parameters) {
            this.parameters = parameters;
            return this;
        }

        public DataSharingContext setDataSharingFlags(Integer dataSharingFlags) {
            this.dataSharingFlags = dataSharingFlags;
            return this;
        }

        @ProtobufEnum(name = "ContextInfo.DataSharingContext.DataSharingFlags")
        public static enum DataSharingFlags {
            SHOW_MM_DISCLOSURE_ON_CLICK(1),
            SHOW_MM_DISCLOSURE_ON_READ(2);

            DataSharingFlags(@ProtobufEnumIndex int index) {
                this.index = index;
            }

            final int index;

            public int index() {
                return this.index;
            }
        }

        @ProtobufMessage(name = "ContextInfo.DataSharingContext.Parameters")
        public static final class Parameters {
            @ProtobufProperty(index = 1, type = ProtobufType.STRING)
            String key;

            @ProtobufProperty(index = 2, type = ProtobufType.STRING)
            String stringData;

            @ProtobufProperty(index = 3, type = ProtobufType.INT64)
            Long intData;

            @ProtobufProperty(index = 4, type = ProtobufType.FLOAT)
            Float floatData;

            @ProtobufProperty(index = 5, type = ProtobufType.MESSAGE)
            DataSharingContext.Parameters contents;


            Parameters(String key, String stringData, Long intData, Float floatData, Parameters contents) {
                this.key = key;
                this.stringData = stringData;
                this.intData = intData;
                this.floatData = floatData;
                this.contents = contents;
            }

            public Optional<String> key() {
                return Optional.ofNullable(key);
            }

            public Optional<String> stringData() {
                return Optional.ofNullable(stringData);
            }

            public OptionalLong intData() {
                return intData == null ? OptionalLong.empty() : OptionalLong.of(intData);
            }

            public OptionalDouble floatData() {
                return floatData == null ? OptionalDouble.empty() : OptionalDouble.of(floatData);
            }

            public Optional<Parameters> contents() {
                return Optional.ofNullable(contents);
            }

            public Parameters setKey(String key) {
                this.key = key;
                return this;
            }

            public Parameters setStringData(String stringData) {
                this.stringData = stringData;
                return this;
            }

            public Parameters setIntData(Long intData) {
                this.intData = intData;
                return this;
            }

            public Parameters setFloatData(Float floatData) {
                this.floatData = floatData;
                return this;
            }

            public Parameters setContents(Parameters contents) {
                this.contents = contents;
                return this;
            }
        }
    }

    @ProtobufMessage(name = "ContextInfo.ExternalAdReplyInfo")
    public static final class ExternalAdReplyInfo {
        @ProtobufProperty(index = 1, type = ProtobufType.STRING)
        String title;

        @ProtobufProperty(index = 2, type = ProtobufType.STRING)
        String body;

        @ProtobufProperty(index = 3, type = ProtobufType.ENUM)
        ExternalAdReplyInfo.MediaType mediaType;

        @ProtobufProperty(index = 4, type = ProtobufType.STRING)
        String thumbnailUrl;

        @ProtobufProperty(index = 5, type = ProtobufType.STRING)
        String mediaUrl;

        @ProtobufProperty(index = 6, type = ProtobufType.BYTES)
        byte[] thumbnail;

        @ProtobufProperty(index = 7, type = ProtobufType.STRING)
        String sourceType;

        @ProtobufProperty(index = 8, type = ProtobufType.STRING)
        String sourceId;

        @ProtobufProperty(index = 9, type = ProtobufType.STRING)
        String sourceUrl;

        @ProtobufProperty(index = 10, type = ProtobufType.BOOL)
        Boolean containsAutoReply;

        @ProtobufProperty(index = 11, type = ProtobufType.BOOL)
        Boolean renderLargerThumbnail;

        @ProtobufProperty(index = 12, type = ProtobufType.BOOL)
        Boolean showAdAttribution;

        @ProtobufProperty(index = 13, type = ProtobufType.STRING)
        String ctwaClid;

        @ProtobufProperty(index = 14, type = ProtobufType.STRING)
        String ref;

        @ProtobufProperty(index = 15, type = ProtobufType.BOOL)
        Boolean clickToWhatsappCall;

        @ProtobufProperty(index = 16, type = ProtobufType.BOOL)
        Boolean adContextPreviewDismissed;

        @ProtobufProperty(index = 17, type = ProtobufType.STRING)
        String sourceApp;

        @ProtobufProperty(index = 18, type = ProtobufType.BOOL)
        Boolean automatedGreetingMessageShown;

        @ProtobufProperty(index = 19, type = ProtobufType.STRING)
        String greetingMessageBody;

        @ProtobufProperty(index = 20, type = ProtobufType.STRING)
        String ctaPayload;

        @ProtobufProperty(index = 21, type = ProtobufType.BOOL)
        Boolean disableNudge;

        @ProtobufProperty(index = 22, type = ProtobufType.STRING)
        String originalImageUrl;

        @ProtobufProperty(index = 23, type = ProtobufType.STRING)
        String automatedGreetingMessageCtaType;

        @ProtobufProperty(index = 24, type = ProtobufType.BOOL)
        Boolean wtwaAdFormat;

        @ProtobufProperty(index = 25, type = ProtobufType.ENUM)
        ExternalAdReplyInfo.AdType adType;

        @ProtobufProperty(index = 26, type = ProtobufType.STRING)
        String wtwaWebsiteUrl;

        @ProtobufProperty(index = 27, type = ProtobufType.STRING)
        String adPreviewUrl;


        ExternalAdReplyInfo(String title, String body, MediaType mediaType, String thumbnailUrl, String mediaUrl, byte[] thumbnail, String sourceType, String sourceId, String sourceUrl, Boolean containsAutoReply, Boolean renderLargerThumbnail, Boolean showAdAttribution, String ctwaClid, String ref, Boolean clickToWhatsappCall, Boolean adContextPreviewDismissed, String sourceApp, Boolean automatedGreetingMessageShown, String greetingMessageBody, String ctaPayload, Boolean disableNudge, String originalImageUrl, String automatedGreetingMessageCtaType, Boolean wtwaAdFormat, AdType adType, String wtwaWebsiteUrl, String adPreviewUrl) {
            this.title = title;
            this.body = body;
            this.mediaType = mediaType;
            this.thumbnailUrl = thumbnailUrl;
            this.mediaUrl = mediaUrl;
            this.thumbnail = thumbnail;
            this.sourceType = sourceType;
            this.sourceId = sourceId;
            this.sourceUrl = sourceUrl;
            this.containsAutoReply = containsAutoReply;
            this.renderLargerThumbnail = renderLargerThumbnail;
            this.showAdAttribution = showAdAttribution;
            this.ctwaClid = ctwaClid;
            this.ref = ref;
            this.clickToWhatsappCall = clickToWhatsappCall;
            this.adContextPreviewDismissed = adContextPreviewDismissed;
            this.sourceApp = sourceApp;
            this.automatedGreetingMessageShown = automatedGreetingMessageShown;
            this.greetingMessageBody = greetingMessageBody;
            this.ctaPayload = ctaPayload;
            this.disableNudge = disableNudge;
            this.originalImageUrl = originalImageUrl;
            this.automatedGreetingMessageCtaType = automatedGreetingMessageCtaType;
            this.wtwaAdFormat = wtwaAdFormat;
            this.adType = adType;
            this.wtwaWebsiteUrl = wtwaWebsiteUrl;
            this.adPreviewUrl = adPreviewUrl;
        }

        public Optional<String> title() {
            return Optional.ofNullable(title);
        }

        public Optional<String> body() {
            return Optional.ofNullable(body);
        }

        public Optional<MediaType> mediaType() {
            return Optional.ofNullable(mediaType);
        }

        public Optional<String> thumbnailUrl() {
            return Optional.ofNullable(thumbnailUrl);
        }

        public Optional<String> mediaUrl() {
            return Optional.ofNullable(mediaUrl);
        }

        public Optional<byte[]> thumbnail() {
            return Optional.ofNullable(thumbnail);
        }

        public Optional<String> sourceType() {
            return Optional.ofNullable(sourceType);
        }

        public Optional<String> sourceId() {
            return Optional.ofNullable(sourceId);
        }

        public Optional<String> sourceUrl() {
            return Optional.ofNullable(sourceUrl);
        }

        public boolean containsAutoReply() {
            return containsAutoReply != null && containsAutoReply;
        }

        public boolean renderLargerThumbnail() {
            return renderLargerThumbnail != null && renderLargerThumbnail;
        }

        public boolean showAdAttribution() {
            return showAdAttribution != null && showAdAttribution;
        }

        public Optional<String> ctwaClid() {
            return Optional.ofNullable(ctwaClid);
        }

        public Optional<String> ref() {
            return Optional.ofNullable(ref);
        }

        public boolean clickToWhatsappCall() {
            return clickToWhatsappCall != null && clickToWhatsappCall;
        }

        public boolean adContextPreviewDismissed() {
            return adContextPreviewDismissed != null && adContextPreviewDismissed;
        }

        public Optional<String> sourceApp() {
            return Optional.ofNullable(sourceApp);
        }

        public boolean automatedGreetingMessageShown() {
            return automatedGreetingMessageShown != null && automatedGreetingMessageShown;
        }

        public Optional<String> greetingMessageBody() {
            return Optional.ofNullable(greetingMessageBody);
        }

        public Optional<String> ctaPayload() {
            return Optional.ofNullable(ctaPayload);
        }

        public boolean disableNudge() {
            return disableNudge != null && disableNudge;
        }

        public Optional<String> originalImageUrl() {
            return Optional.ofNullable(originalImageUrl);
        }

        public Optional<String> automatedGreetingMessageCtaType() {
            return Optional.ofNullable(automatedGreetingMessageCtaType);
        }

        public boolean wtwaAdFormat() {
            return wtwaAdFormat != null && wtwaAdFormat;
        }

        public Optional<AdType> adType() {
            return Optional.ofNullable(adType);
        }

        public Optional<String> wtwaWebsiteUrl() {
            return Optional.ofNullable(wtwaWebsiteUrl);
        }

        public Optional<String> adPreviewUrl() {
            return Optional.ofNullable(adPreviewUrl);
        }

        public ExternalAdReplyInfo setTitle(String title) {
            this.title = title;
            return this;
        }

        public ExternalAdReplyInfo setBody(String body) {
            this.body = body;
            return this;
        }

        public ExternalAdReplyInfo setMediaType(MediaType mediaType) {
            this.mediaType = mediaType;
            return this;
        }

        public ExternalAdReplyInfo setThumbnailUrl(String thumbnailUrl) {
            this.thumbnailUrl = thumbnailUrl;
            return this;
        }

        public ExternalAdReplyInfo setMediaUrl(String mediaUrl) {
            this.mediaUrl = mediaUrl;
            return this;
        }

        public ExternalAdReplyInfo setThumbnail(byte[] thumbnail) {
            this.thumbnail = thumbnail;
            return this;
        }

        public ExternalAdReplyInfo setSourceType(String sourceType) {
            this.sourceType = sourceType;
            return this;
        }

        public ExternalAdReplyInfo setSourceId(String sourceId) {
            this.sourceId = sourceId;
            return this;
        }

        public ExternalAdReplyInfo setSourceUrl(String sourceUrl) {
            this.sourceUrl = sourceUrl;
            return this;
        }

        public ExternalAdReplyInfo setContainsAutoReply(Boolean containsAutoReply) {
            this.containsAutoReply = containsAutoReply;
            return this;
        }

        public ExternalAdReplyInfo setRenderLargerThumbnail(Boolean renderLargerThumbnail) {
            this.renderLargerThumbnail = renderLargerThumbnail;
            return this;
        }

        public ExternalAdReplyInfo setShowAdAttribution(Boolean showAdAttribution) {
            this.showAdAttribution = showAdAttribution;
            return this;
        }

        public ExternalAdReplyInfo setCtwaClid(String ctwaClid) {
            this.ctwaClid = ctwaClid;
            return this;
        }

        public ExternalAdReplyInfo setRef(String ref) {
            this.ref = ref;
            return this;
        }

        public ExternalAdReplyInfo setClickToWhatsappCall(Boolean clickToWhatsappCall) {
            this.clickToWhatsappCall = clickToWhatsappCall;
            return this;
        }

        public ExternalAdReplyInfo setAdContextPreviewDismissed(Boolean adContextPreviewDismissed) {
            this.adContextPreviewDismissed = adContextPreviewDismissed;
            return this;
        }

        public ExternalAdReplyInfo setSourceApp(String sourceApp) {
            this.sourceApp = sourceApp;
            return this;
        }

        public ExternalAdReplyInfo setAutomatedGreetingMessageShown(Boolean automatedGreetingMessageShown) {
            this.automatedGreetingMessageShown = automatedGreetingMessageShown;
            return this;
        }

        public ExternalAdReplyInfo setGreetingMessageBody(String greetingMessageBody) {
            this.greetingMessageBody = greetingMessageBody;
            return this;
        }

        public ExternalAdReplyInfo setCtaPayload(String ctaPayload) {
            this.ctaPayload = ctaPayload;
            return this;
        }

        public ExternalAdReplyInfo setDisableNudge(Boolean disableNudge) {
            this.disableNudge = disableNudge;
            return this;
        }

        public ExternalAdReplyInfo setOriginalImageUrl(String originalImageUrl) {
            this.originalImageUrl = originalImageUrl;
            return this;
        }

        public ExternalAdReplyInfo setAutomatedGreetingMessageCtaType(String automatedGreetingMessageCtaType) {
            this.automatedGreetingMessageCtaType = automatedGreetingMessageCtaType;
            return this;
        }

        public ExternalAdReplyInfo setWtwaAdFormat(Boolean wtwaAdFormat) {
            this.wtwaAdFormat = wtwaAdFormat;
            return this;
        }

        public ExternalAdReplyInfo setAdType(AdType adType) {
            this.adType = adType;
            return this;
        }

        public ExternalAdReplyInfo setWtwaWebsiteUrl(String wtwaWebsiteUrl) {
            this.wtwaWebsiteUrl = wtwaWebsiteUrl;
            return this;
        }

        public ExternalAdReplyInfo setAdPreviewUrl(String adPreviewUrl) {
            this.adPreviewUrl = adPreviewUrl;
            return this;
        }

        @ProtobufEnum(name = "ContextInfo.ExternalAdReplyInfo.AdType")
        public static enum AdType {
            CTWA(0),
            CAWC(1);

            AdType(@ProtobufEnumIndex int index) {
                this.index = index;
            }

            final int index;

            public int index() {
                return this.index;
            }
        }

        @ProtobufEnum(name = "ContextInfo.ExternalAdReplyInfo.MediaType")
        public static enum MediaType {
            NONE(0),
            IMAGE(1),
            VIDEO(2);

            MediaType(@ProtobufEnumIndex int index) {
                this.index = index;
            }

            final int index;

            public int index() {
                return this.index;
            }
        }
    }

    @ProtobufMessage(name = "ContextInfo.FeatureEligibilities")
    public static final class FeatureEligibilities {
        @ProtobufProperty(index = 1, type = ProtobufType.BOOL)
        Boolean cannotBeReactedTo;

        @ProtobufProperty(index = 2, type = ProtobufType.BOOL)
        Boolean cannotBeRanked;

        @ProtobufProperty(index = 3, type = ProtobufType.BOOL)
        Boolean canRequestFeedback;

        @ProtobufProperty(index = 4, type = ProtobufType.BOOL)
        Boolean canBeReshared;

        @ProtobufProperty(index = 5, type = ProtobufType.BOOL)
        Boolean canReceiveMultiReact;


        FeatureEligibilities(Boolean cannotBeReactedTo, Boolean cannotBeRanked, Boolean canRequestFeedback, Boolean canBeReshared, Boolean canReceiveMultiReact) {
            this.cannotBeReactedTo = cannotBeReactedTo;
            this.cannotBeRanked = cannotBeRanked;
            this.canRequestFeedback = canRequestFeedback;
            this.canBeReshared = canBeReshared;
            this.canReceiveMultiReact = canReceiveMultiReact;
        }

        public boolean cannotBeReactedTo() {
            return cannotBeReactedTo != null && cannotBeReactedTo;
        }

        public boolean cannotBeRanked() {
            return cannotBeRanked != null && cannotBeRanked;
        }

        public boolean canRequestFeedback() {
            return canRequestFeedback != null && canRequestFeedback;
        }

        public boolean canBeReshared() {
            return canBeReshared != null && canBeReshared;
        }

        public boolean canReceiveMultiReact() {
            return canReceiveMultiReact != null && canReceiveMultiReact;
        }

        public FeatureEligibilities setCannotBeReactedTo(Boolean cannotBeReactedTo) {
            this.cannotBeReactedTo = cannotBeReactedTo;
            return this;
        }

        public FeatureEligibilities setCannotBeRanked(Boolean cannotBeRanked) {
            this.cannotBeRanked = cannotBeRanked;
            return this;
        }

        public FeatureEligibilities setCanRequestFeedback(Boolean canRequestFeedback) {
            this.canRequestFeedback = canRequestFeedback;
            return this;
        }

        public FeatureEligibilities setCanBeReshared(Boolean canBeReshared) {
            this.canBeReshared = canBeReshared;
            return this;
        }

        public FeatureEligibilities setCanReceiveMultiReact(Boolean canReceiveMultiReact) {
            this.canReceiveMultiReact = canReceiveMultiReact;
            return this;
        }
    }

    @ProtobufMessage(name = "ContextInfo.ForwardedNewsletterMessageInfo")
    public static final class ForwardedNewsletterMessageInfo implements InteractiveAction {
        @ProtobufProperty(index = 1, type = ProtobufType.STRING)
        Jid newsletterJid;

        @ProtobufProperty(index = 2, type = ProtobufType.INT32)
        Integer serverMessageId;

        @ProtobufProperty(index = 3, type = ProtobufType.STRING)
        String newsletterName;

        @ProtobufProperty(index = 4, type = ProtobufType.ENUM)
        ForwardedNewsletterMessageInfo.ContentType contentType;

        @ProtobufProperty(index = 5, type = ProtobufType.STRING)
        String accessibilityText;

        @ProtobufProperty(index = 6, type = ProtobufType.STRING)
        String profileName;


        ForwardedNewsletterMessageInfo(Jid newsletterJid, Integer serverMessageId, String newsletterName, ContentType contentType, String accessibilityText, String profileName) {
            this.newsletterJid = newsletterJid;
            this.serverMessageId = serverMessageId;
            this.newsletterName = newsletterName;
            this.contentType = contentType;
            this.accessibilityText = accessibilityText;
            this.profileName = profileName;
        }

        public Optional<Jid> newsletterJid() {
            return Optional.ofNullable(newsletterJid);
        }

        public OptionalInt serverMessageId() {
            return serverMessageId == null ? OptionalInt.empty() : OptionalInt.of(serverMessageId);
        }

        public Optional<String> newsletterName() {
            return Optional.ofNullable(newsletterName);
        }

        public Optional<ContentType> contentType() {
            return Optional.ofNullable(contentType);
        }

        public Optional<String> accessibilityText() {
            return Optional.ofNullable(accessibilityText);
        }

        public Optional<String> profileName() {
            return Optional.ofNullable(profileName);
        }

        public ForwardedNewsletterMessageInfo setNewsletterJid(Jid newsletterJid) {
            this.newsletterJid = newsletterJid;
            return this;
        }

        public ForwardedNewsletterMessageInfo setServerMessageId(Integer serverMessageId) {
            this.serverMessageId = serverMessageId;
            return this;
        }

        public ForwardedNewsletterMessageInfo setNewsletterName(String newsletterName) {
            this.newsletterName = newsletterName;
            return this;
        }

        public ForwardedNewsletterMessageInfo setContentType(ContentType contentType) {
            this.contentType = contentType;
            return this;
        }

        public ForwardedNewsletterMessageInfo setAccessibilityText(String accessibilityText) {
            this.accessibilityText = accessibilityText;
            return this;
        }

        public ForwardedNewsletterMessageInfo setProfileName(String profileName) {
            this.profileName = profileName;
            return this;
        }

        @ProtobufEnum(name = "ContextInfo.ForwardedNewsletterMessageInfo.ContentType")
        public static enum ContentType {
            UPDATE(1),
            UPDATE_CARD(2),
            LINK_CARD(3);

            ContentType(@ProtobufEnumIndex int index) {
                this.index = index;
            }

            final int index;

            public int index() {
                return this.index;
            }
        }
    }

    @ProtobufMessage(name = "ContextInfo.QuestionReplyQuotedMessage")
    public static final class QuestionReplyQuotedMessage {
        @ProtobufProperty(index = 1, type = ProtobufType.INT32)
        Integer serverQuestionId;

        @ProtobufProperty(index = 2, type = ProtobufType.MESSAGE)
        MessageContainer quotedQuestion;

        @ProtobufProperty(index = 3, type = ProtobufType.MESSAGE)
        MessageContainer quotedResponse;


        QuestionReplyQuotedMessage(Integer serverQuestionId, MessageContainer quotedQuestion, MessageContainer quotedResponse) {
            this.serverQuestionId = serverQuestionId;
            this.quotedQuestion = quotedQuestion;
            this.quotedResponse = quotedResponse;
        }

        public OptionalInt serverQuestionId() {
            return serverQuestionId == null ? OptionalInt.empty() : OptionalInt.of(serverQuestionId);
        }

        public Optional<MessageContainer> quotedQuestion() {
            return Optional.ofNullable(quotedQuestion);
        }

        public Optional<MessageContainer> quotedResponse() {
            return Optional.ofNullable(quotedResponse);
        }

        public QuestionReplyQuotedMessage setServerQuestionId(Integer serverQuestionId) {
            this.serverQuestionId = serverQuestionId;
            return this;
        }

        public QuestionReplyQuotedMessage setQuotedQuestion(MessageContainer quotedQuestion) {
            this.quotedQuestion = quotedQuestion;
            return this;
        }

        public QuestionReplyQuotedMessage setQuotedResponse(MessageContainer quotedResponse) {
            this.quotedResponse = quotedResponse;
            return this;
        }
    }

    @ProtobufMessage(name = "ContextInfo.StatusAudienceMetadata")
    public static final class StatusAudienceMetadata {
        @ProtobufProperty(index = 1, type = ProtobufType.ENUM)
        StatusAudienceMetadata.AudienceType audienceType;

        @ProtobufProperty(index = 2, type = ProtobufType.STRING)
        String listName;

        @ProtobufProperty(index = 3, type = ProtobufType.STRING)
        String listEmoji;


        StatusAudienceMetadata(AudienceType audienceType, String listName, String listEmoji) {
            this.audienceType = audienceType;
            this.listName = listName;
            this.listEmoji = listEmoji;
        }

        public Optional<AudienceType> audienceType() {
            return Optional.ofNullable(audienceType);
        }

        public Optional<String> listName() {
            return Optional.ofNullable(listName);
        }

        public Optional<String> listEmoji() {
            return Optional.ofNullable(listEmoji);
        }

        public StatusAudienceMetadata setAudienceType(AudienceType audienceType) {
            this.audienceType = audienceType;
            return this;
        }

        public StatusAudienceMetadata setListName(String listName) {
            this.listName = listName;
            return this;
        }

        public StatusAudienceMetadata setListEmoji(String listEmoji) {
            this.listEmoji = listEmoji;
            return this;
        }

        @ProtobufEnum(name = "ContextInfo.StatusAudienceMetadata.AudienceType")
        public static enum AudienceType {
            UNKNOWN(0),
            CLOSE_FRIENDS(1);

            AudienceType(@ProtobufEnumIndex int index) {
                this.index = index;
            }

            final int index;

            public int index() {
                return this.index;
            }
        }
    }

    @ProtobufMessage(name = "ContextInfo.UTMInfo")
    public static final class UTMInfo {
        @ProtobufProperty(index = 1, type = ProtobufType.STRING)
        String utmSource;

        @ProtobufProperty(index = 2, type = ProtobufType.STRING)
        String utmCampaign;


        UTMInfo(String utmSource, String utmCampaign) {
            this.utmSource = utmSource;
            this.utmCampaign = utmCampaign;
        }

        public Optional<String> utmSource() {
            return Optional.ofNullable(utmSource);
        }

        public Optional<String> utmCampaign() {
            return Optional.ofNullable(utmCampaign);
        }

        public UTMInfo setUtmSource(String utmSource) {
            this.utmSource = utmSource;
            return this;
        }

        public UTMInfo setUtmCampaign(String utmCampaign) {
            this.utmCampaign = utmCampaign;
            return this;
        }
    }
}
