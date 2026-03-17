package com.github.auties00.cobalt.model.message.system;

import com.github.auties00.cobalt.model.bot.ai.AIQueryFanout;
import com.github.auties00.cobalt.model.bot.feedback.BotFeedbackMessage;
import com.github.auties00.cobalt.model.chat.ChatDisappearingMode;
import com.github.auties00.cobalt.model.chat.ChatLimitSharing;
import com.github.auties00.cobalt.model.chat.group.GroupParticipantLabel;
import com.github.auties00.cobalt.model.jid.Jid;
import com.github.auties00.cobalt.model.jid.migration.LIDMigrationMappingSyncMessage;
import com.github.auties00.cobalt.model.media.MediaNotifyMessage;
import com.github.auties00.cobalt.model.message.Message;
import com.github.auties00.cobalt.model.message.MessageContainer;
import com.github.auties00.cobalt.model.message.MessageKey;
import com.github.auties00.cobalt.model.message.system.appstate.AppStateFatalExceptionNotification;
import com.github.auties00.cobalt.model.message.system.appstate.AppStateSyncKeyRequest;
import com.github.auties00.cobalt.model.message.system.appstate.AppStateSyncKeyShare;
import com.github.auties00.cobalt.model.message.system.history.HistorySyncNotification;
import com.github.auties00.cobalt.model.message.system.peer.PeerDataOperationRequestMessage;
import com.github.auties00.cobalt.model.message.system.peer.PeerDataOperationRequestResponseMessage;
import com.github.auties00.cobalt.model.mixin.InstantMillisMixin;
import com.github.auties00.cobalt.model.mixin.InstantSecondsMixin;
import it.auties.protobuf.annotation.ProtobufEnum;
import it.auties.protobuf.annotation.ProtobufEnumIndex;
import it.auties.protobuf.annotation.ProtobufMessage;
import it.auties.protobuf.annotation.ProtobufProperty;
import it.auties.protobuf.model.ProtobufType;

import java.time.Instant;
import java.util.Optional;
import java.util.OptionalInt;

@ProtobufMessage(name = "Message.ProtocolMessage")
public final class ProtocolMessage implements Message {
    @ProtobufProperty(index = 1, type = ProtobufType.MESSAGE)
    MessageKey key;

    @ProtobufProperty(index = 2, type = ProtobufType.ENUM)
    Type type;

    @ProtobufProperty(index = 4, type = ProtobufType.UINT32)
    Integer ephemeralExpiration;

    @ProtobufProperty(index = 5, type = ProtobufType.INT64, mixins = InstantSecondsMixin.class)
    Instant ephemeralSettingTimestamp;

    @ProtobufProperty(index = 6, type = ProtobufType.MESSAGE)
    HistorySyncNotification historySyncNotification;

    @ProtobufProperty(index = 7, type = ProtobufType.MESSAGE)
    AppStateSyncKeyShare appStateSyncKeyShare;

    @ProtobufProperty(index = 8, type = ProtobufType.MESSAGE)
    AppStateSyncKeyRequest appStateSyncKeyRequest;

    @ProtobufProperty(index = 9, type = ProtobufType.MESSAGE)
    InitialSecurityNotificationSettingSync initialSecurityNotificationSettingSync;

    @ProtobufProperty(index = 10, type = ProtobufType.MESSAGE)
    AppStateFatalExceptionNotification appStateFatalExceptionNotification;

    @ProtobufProperty(index = 11, type = ProtobufType.MESSAGE)
    ChatDisappearingMode disappearingMode;

    @ProtobufProperty(index = 14, type = ProtobufType.MESSAGE)
    MessageContainer editedMessageContainer;

    @ProtobufProperty(index = 15, type = ProtobufType.INT64, mixins = InstantMillisMixin.class)
    Instant timestampMs;

    @ProtobufProperty(index = 16, type = ProtobufType.MESSAGE)
    PeerDataOperationRequestMessage peerDataOperationRequestMessage;

    @ProtobufProperty(index = 17, type = ProtobufType.MESSAGE)
    PeerDataOperationRequestResponseMessage peerDataOperationRequestResponseMessage;

    @ProtobufProperty(index = 18, type = ProtobufType.MESSAGE)
    BotFeedbackMessage botFeedbackMessage;

    @ProtobufProperty(index = 19, type = ProtobufType.STRING)
    Jid invokerJid;

    @ProtobufProperty(index = 20, type = ProtobufType.MESSAGE)
    RequestWelcomeMessageMetadata requestWelcomeMessageMetadata;

    @ProtobufProperty(index = 21, type = ProtobufType.MESSAGE)
    MediaNotifyMessage mediaNotifyMessage;

    @ProtobufProperty(index = 22, type = ProtobufType.MESSAGE)
    CloudAPIThreadControlNotification cloudApiThreadControlNotification;

    @ProtobufProperty(index = 23, type = ProtobufType.MESSAGE)
    LIDMigrationMappingSyncMessage lidMigrationMappingSyncMessage;

    @ProtobufProperty(index = 24, type = ProtobufType.MESSAGE)
    ChatLimitSharing limitSharing;

    @ProtobufProperty(index = 25, type = ProtobufType.BYTES)
    byte[] aiPsiMetadata;

    @ProtobufProperty(index = 26, type = ProtobufType.MESSAGE)
    AIQueryFanout aiQueryFanout;

    @ProtobufProperty(index = 27, type = ProtobufType.MESSAGE)
    GroupParticipantLabel participantLabel;


    ProtocolMessage(MessageKey key, Type type, Integer ephemeralExpiration, Instant ephemeralSettingTimestamp, HistorySyncNotification historySyncNotification, AppStateSyncKeyShare appStateSyncKeyShare, AppStateSyncKeyRequest appStateSyncKeyRequest, InitialSecurityNotificationSettingSync initialSecurityNotificationSettingSync, AppStateFatalExceptionNotification appStateFatalExceptionNotification, ChatDisappearingMode disappearingMode, MessageContainer editedMessageContainer, Instant timestampMs, PeerDataOperationRequestMessage peerDataOperationRequestMessage, PeerDataOperationRequestResponseMessage peerDataOperationRequestResponseMessage, BotFeedbackMessage botFeedbackMessage, Jid invokerJid, RequestWelcomeMessageMetadata requestWelcomeMessageMetadata, MediaNotifyMessage mediaNotifyMessage, CloudAPIThreadControlNotification cloudApiThreadControlNotification, LIDMigrationMappingSyncMessage lidMigrationMappingSyncMessage, ChatLimitSharing limitSharing, byte[] aiPsiMetadata, AIQueryFanout aiQueryFanout, GroupParticipantLabel participantLabel) {
        this.key = key;
        this.type = type;
        this.ephemeralExpiration = ephemeralExpiration;
        this.ephemeralSettingTimestamp = ephemeralSettingTimestamp;
        this.historySyncNotification = historySyncNotification;
        this.appStateSyncKeyShare = appStateSyncKeyShare;
        this.appStateSyncKeyRequest = appStateSyncKeyRequest;
        this.initialSecurityNotificationSettingSync = initialSecurityNotificationSettingSync;
        this.appStateFatalExceptionNotification = appStateFatalExceptionNotification;
        this.disappearingMode = disappearingMode;
        this.editedMessageContainer = editedMessageContainer;
        this.timestampMs = timestampMs;
        this.peerDataOperationRequestMessage = peerDataOperationRequestMessage;
        this.peerDataOperationRequestResponseMessage = peerDataOperationRequestResponseMessage;
        this.botFeedbackMessage = botFeedbackMessage;
        this.invokerJid = invokerJid;
        this.requestWelcomeMessageMetadata = requestWelcomeMessageMetadata;
        this.mediaNotifyMessage = mediaNotifyMessage;
        this.cloudApiThreadControlNotification = cloudApiThreadControlNotification;
        this.lidMigrationMappingSyncMessage = lidMigrationMappingSyncMessage;
        this.limitSharing = limitSharing;
        this.aiPsiMetadata = aiPsiMetadata;
        this.aiQueryFanout = aiQueryFanout;
        this.participantLabel = participantLabel;
    }

    public Optional<MessageKey> key() {
        return Optional.ofNullable(key);
    }

    public Optional<Type> type() {
        return Optional.ofNullable(type);
    }

    public OptionalInt ephemeralExpiration() {
        return ephemeralExpiration == null ? OptionalInt.empty() : OptionalInt.of(ephemeralExpiration);
    }

    public Optional<Instant> ephemeralSettingTimestamp() {
        return Optional.ofNullable(ephemeralSettingTimestamp);
    }

    public Optional<HistorySyncNotification> historySyncNotification() {
        return Optional.ofNullable(historySyncNotification);
    }

    public Optional<AppStateSyncKeyShare> appStateSyncKeyShare() {
        return Optional.ofNullable(appStateSyncKeyShare);
    }

    public Optional<AppStateSyncKeyRequest> appStateSyncKeyRequest() {
        return Optional.ofNullable(appStateSyncKeyRequest);
    }

    public Optional<InitialSecurityNotificationSettingSync> initialSecurityNotificationSettingSync() {
        return Optional.ofNullable(initialSecurityNotificationSettingSync);
    }

    public Optional<AppStateFatalExceptionNotification> appStateFatalExceptionNotification() {
        return Optional.ofNullable(appStateFatalExceptionNotification);
    }

    public Optional<ChatDisappearingMode> disappearingMode() {
        return Optional.ofNullable(disappearingMode);
    }

    public Optional<MessageContainer> editedMessage() {
        return Optional.ofNullable(editedMessageContainer);
    }

    public Optional<Instant> timestampMs() {
        return Optional.ofNullable(timestampMs);
    }

    public Optional<PeerDataOperationRequestMessage> peerDataOperationRequestMessage() {
        return Optional.ofNullable(peerDataOperationRequestMessage);
    }

    public Optional<PeerDataOperationRequestResponseMessage> peerDataOperationRequestResponseMessage() {
        return Optional.ofNullable(peerDataOperationRequestResponseMessage);
    }

    public Optional<BotFeedbackMessage> botFeedbackMessage() {
        return Optional.ofNullable(botFeedbackMessage);
    }

    public Optional<Jid> invokerJid() {
        return Optional.ofNullable(invokerJid);
    }

    public Optional<RequestWelcomeMessageMetadata> requestWelcomeMessageMetadata() {
        return Optional.ofNullable(requestWelcomeMessageMetadata);
    }

    public Optional<MediaNotifyMessage> mediaNotifyMessage() {
        return Optional.ofNullable(mediaNotifyMessage);
    }

    public Optional<CloudAPIThreadControlNotification> cloudApiThreadControlNotification() {
        return Optional.ofNullable(cloudApiThreadControlNotification);
    }

    public Optional<LIDMigrationMappingSyncMessage> lidMigrationMappingSyncMessage() {
        return Optional.ofNullable(lidMigrationMappingSyncMessage);
    }

    public Optional<ChatLimitSharing> limitSharing() {
        return Optional.ofNullable(limitSharing);
    }

    public Optional<byte[]> aiPsiMetadata() {
        return Optional.ofNullable(aiPsiMetadata);
    }

    public Optional<AIQueryFanout> aiQueryFanout() {
        return Optional.ofNullable(aiQueryFanout);
    }

    public Optional<GroupParticipantLabel> memberLabel() {
        return Optional.ofNullable(participantLabel);
    }

    public void setKey(MessageKey key) {
        this.key = key;
    }

    public void setType(Type type) {
        this.type = type;
    }

    public void setEphemeralExpiration(Integer ephemeralExpiration) {
        this.ephemeralExpiration = ephemeralExpiration;
    }

    public void setEphemeralSettingTimestamp(Instant ephemeralSettingTimestamp) {
        this.ephemeralSettingTimestamp = ephemeralSettingTimestamp;
    }

    public void setHistorySyncNotification(HistorySyncNotification historySyncNotification) {
        this.historySyncNotification = historySyncNotification;
    }

    public void setAppStateSyncKeyShare(AppStateSyncKeyShare appStateSyncKeyShare) {
        this.appStateSyncKeyShare = appStateSyncKeyShare;
    }

    public void setAppStateSyncKeyRequest(AppStateSyncKeyRequest appStateSyncKeyRequest) {
        this.appStateSyncKeyRequest = appStateSyncKeyRequest;
    }

    public void setInitialSecurityNotificationSettingSync(InitialSecurityNotificationSettingSync initialSecurityNotificationSettingSync) {
        this.initialSecurityNotificationSettingSync = initialSecurityNotificationSettingSync;
    }

    public void setAppStateFatalExceptionNotification(AppStateFatalExceptionNotification appStateFatalExceptionNotification) {
        this.appStateFatalExceptionNotification = appStateFatalExceptionNotification;
    }

    public void setDisappearingMode(ChatDisappearingMode disappearingMode) {
        this.disappearingMode = disappearingMode;
    }

    public void setEditedMessage(MessageContainer editedMessageContainer) {
        this.editedMessageContainer = editedMessageContainer;
    }

    public void setTimestampMs(Instant timestampMs) {
        this.timestampMs = timestampMs;
    }

    public void setPeerDataOperationRequestMessage(PeerDataOperationRequestMessage peerDataOperationRequestMessage) {
        this.peerDataOperationRequestMessage = peerDataOperationRequestMessage;
    }

    public void setPeerDataOperationRequestResponseMessage(PeerDataOperationRequestResponseMessage peerDataOperationRequestResponseMessage) {
        this.peerDataOperationRequestResponseMessage = peerDataOperationRequestResponseMessage;
    }

    public void setBotFeedbackMessage(BotFeedbackMessage botFeedbackMessage) {
        this.botFeedbackMessage = botFeedbackMessage;
    }

    public void setInvokerJid(Jid invokerJid) {
        this.invokerJid = invokerJid;
    }

    public void setRequestWelcomeMessageMetadata(RequestWelcomeMessageMetadata requestWelcomeMessageMetadata) {
        this.requestWelcomeMessageMetadata = requestWelcomeMessageMetadata;
    }

    public void setMediaNotifyMessage(MediaNotifyMessage mediaNotifyMessage) {
        this.mediaNotifyMessage = mediaNotifyMessage;
    }

    public void setCloudApiThreadControlNotification(CloudAPIThreadControlNotification cloudApiThreadControlNotification) {
        this.cloudApiThreadControlNotification = cloudApiThreadControlNotification;
    }

    public void setLidMigrationMappingSyncMessage(LIDMigrationMappingSyncMessage lidMigrationMappingSyncMessage) {
        this.lidMigrationMappingSyncMessage = lidMigrationMappingSyncMessage;
    }

    public void setLimitSharing(ChatLimitSharing limitSharing) {
        this.limitSharing = limitSharing;
    }

    public void setAiPsiMetadata(byte[] aiPsiMetadata) {
        this.aiPsiMetadata = aiPsiMetadata;
    }

    public void setAiQueryFanout(AIQueryFanout aiQueryFanout) {
        this.aiQueryFanout = aiQueryFanout;
    }

    public void setMemberLabel(GroupParticipantLabel participantLabel) {
        this.participantLabel = participantLabel;
    }

    @ProtobufEnum(name = "Message.ProtocolMessage.Type")
    public static enum Type {
        REVOKE(0),
        EPHEMERAL_SETTING(3),
        EPHEMERAL_SYNC_RESPONSE(4),
        HISTORY_SYNC_NOTIFICATION(5),
        APP_STATE_SYNC_KEY_SHARE(6),
        APP_STATE_SYNC_KEY_REQUEST(7),
        MSG_FANOUT_BACKFILL_REQUEST(8),
        INITIAL_SECURITY_NOTIFICATION_SETTING_SYNC(9),
        APP_STATE_FATAL_EXCEPTION_NOTIFICATION(10),
        SHARE_PHONE_NUMBER(11),
        MESSAGE_EDIT(14),
        PEER_DATA_OPERATION_REQUEST_MESSAGE(16),
        PEER_DATA_OPERATION_REQUEST_RESPONSE_MESSAGE(17),
        REQUEST_WELCOME_MESSAGE(18),
        BOT_FEEDBACK_MESSAGE(19),
        MEDIA_NOTIFY_MESSAGE(20),
        CLOUD_API_THREAD_CONTROL_NOTIFICATION(21),
        LID_MIGRATION_MAPPING_SYNC(22),
        REMINDER_MESSAGE(23),
        BOT_MEMU_ONBOARDING_MESSAGE(24),
        STATUS_MENTION_MESSAGE(25),
        STOP_GENERATION_MESSAGE(26),
        LIMIT_SHARING(27),
        AI_PSI_METADATA(28),
        AI_QUERY_FANOUT(29),
        GROUP_MEMBER_LABEL_CHANGE(30);

        Type(@ProtobufEnumIndex int index) {
            this.index = index;
        }

        final int index;

        public int index() {
            return this.index;
        }
    }
}
