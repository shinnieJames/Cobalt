package com.github.auties00.cobalt.model.chat;

import com.github.auties00.cobalt.model.bot.BotMetadata;
import com.github.auties00.cobalt.model.message.MessageAssociation;
import com.github.auties00.cobalt.model.message.MessageThreadId;
import com.github.auties00.cobalt.model.device.DeviceListMetadata;
import com.github.auties00.cobalt.model.message.MessageLinkRender;
import com.github.auties00.cobalt.model.message.addon.MessageAddOnContextInfo;
import it.auties.protobuf.annotation.ProtobufMessage;
import it.auties.protobuf.annotation.ProtobufProperty;
import it.auties.protobuf.model.ProtobufType;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

@ProtobufMessage(name = "MessageContextInfo")
public final class ChatMessageInfoContext {
    @ProtobufProperty(index = 1, type = ProtobufType.MESSAGE)
    DeviceListMetadata deviceListMetadata;

    @ProtobufProperty(index = 2, type = ProtobufType.INT32)
    Integer deviceListMetadataVersion;

    @ProtobufProperty(index = 3, type = ProtobufType.BYTES)
    byte[] messageSecret;

    @ProtobufProperty(index = 4, type = ProtobufType.BYTES)
    byte[] paddingBytes;

    @ProtobufProperty(index = 5, type = ProtobufType.UINT32)
    Integer messageAddOnDurationInSecs;

    @ProtobufProperty(index = 6, type = ProtobufType.BYTES)
    byte[] botMessageSecret;

    @ProtobufProperty(index = 7, type = ProtobufType.MESSAGE)
    BotMetadata botMetadata;

    @ProtobufProperty(index = 8, type = ProtobufType.INT32)
    Integer reportingTokenVersion;

    @ProtobufProperty(index = 9, type = ProtobufType.ENUM)
    MessageAddOnContextInfo.ExpiryType messageAddOnExpiryType;

    @ProtobufProperty(index = 10, type = ProtobufType.MESSAGE)
    MessageAssociation messageAssociation;

    @ProtobufProperty(index = 11, type = ProtobufType.BOOL)
    Boolean capiCreatedGroup;

    @ProtobufProperty(index = 12, type = ProtobufType.STRING)
    String supportPayload;

    @ProtobufProperty(index = 13, type = ProtobufType.MESSAGE)
    ChatLimitSharing limitSharing;

    @ProtobufProperty(index = 14, type = ProtobufType.MESSAGE)
    ChatLimitSharing limitSharingV2;

    @ProtobufProperty(index = 15, type = ProtobufType.MESSAGE)
    List<MessageThreadId> threadId;

    @ProtobufProperty(index = 16, type = ProtobufType.ENUM)
    MessageLinkRender weblinkRenderConfig;


    ChatMessageInfoContext(DeviceListMetadata deviceListMetadata, Integer deviceListMetadataVersion, byte[] messageSecret, byte[] paddingBytes, Integer messageAddOnDurationInSecs, byte[] botMessageSecret, BotMetadata botMetadata, Integer reportingTokenVersion, MessageAddOnContextInfo.ExpiryType messageAddOnExpiryType, MessageAssociation messageAssociation, Boolean capiCreatedGroup, String supportPayload, ChatLimitSharing limitSharing, ChatLimitSharing limitSharingV2, List<MessageThreadId> threadId, MessageLinkRender weblinkRenderConfig) {
        this.deviceListMetadata = deviceListMetadata;
        this.deviceListMetadataVersion = deviceListMetadataVersion;
        this.messageSecret = messageSecret;
        this.paddingBytes = paddingBytes;
        this.messageAddOnDurationInSecs = messageAddOnDurationInSecs;
        this.botMessageSecret = botMessageSecret;
        this.botMetadata = botMetadata;
        this.reportingTokenVersion = reportingTokenVersion;
        this.messageAddOnExpiryType = messageAddOnExpiryType;
        this.messageAssociation = messageAssociation;
        this.capiCreatedGroup = capiCreatedGroup;
        this.supportPayload = supportPayload;
        this.limitSharing = limitSharing;
        this.limitSharingV2 = limitSharingV2;
        this.threadId = threadId;
        this.weblinkRenderConfig = weblinkRenderConfig;
    }

    public Optional<DeviceListMetadata> deviceListMetadata() {
        return Optional.ofNullable(deviceListMetadata);
    }

    public OptionalInt deviceListMetadataVersion() {
        return deviceListMetadataVersion == null ? OptionalInt.empty() : OptionalInt.of(deviceListMetadataVersion);
    }

    public Optional<byte[]> messageSecret() {
        return Optional.ofNullable(messageSecret);
    }

    public Optional<byte[]> paddingBytes() {
        return Optional.ofNullable(paddingBytes);
    }

    public OptionalInt messageAddOnDurationInSecs() {
        return messageAddOnDurationInSecs == null ? OptionalInt.empty() : OptionalInt.of(messageAddOnDurationInSecs);
    }

    public Optional<byte[]> botMessageSecret() {
        return Optional.ofNullable(botMessageSecret);
    }

    public Optional<BotMetadata> botMetadata() {
        return Optional.ofNullable(botMetadata);
    }

    public OptionalInt reportingTokenVersion() {
        return reportingTokenVersion == null ? OptionalInt.empty() : OptionalInt.of(reportingTokenVersion);
    }

    public Optional<MessageAddOnContextInfo.ExpiryType> messageAddOnExpiryType() {
        return Optional.ofNullable(messageAddOnExpiryType);
    }

    public Optional<MessageAssociation> messageAssociation() {
        return Optional.ofNullable(messageAssociation);
    }

    public boolean capiCreatedGroup() {
        return capiCreatedGroup != null && capiCreatedGroup;
    }

    public Optional<String> supportPayload() {
        return Optional.ofNullable(supportPayload);
    }

    public Optional<ChatLimitSharing> limitSharing() {
        return Optional.ofNullable(limitSharing);
    }

    public Optional<ChatLimitSharing> limitSharingV2() {
        return Optional.ofNullable(limitSharingV2);
    }

    public List<MessageThreadId> threadId() {
        return threadId == null ? List.of() : Collections.unmodifiableList(threadId);
    }

    public Optional<MessageLinkRender> weblinkRenderConfig() {
        return Optional.ofNullable(weblinkRenderConfig);
    }

    public ChatMessageInfoContext setDeviceListMetadata(DeviceListMetadata deviceListMetadata) {
        this.deviceListMetadata = deviceListMetadata;
        return this;
    }

    public ChatMessageInfoContext setDeviceListMetadataVersion(Integer deviceListMetadataVersion) {
        this.deviceListMetadataVersion = deviceListMetadataVersion;
        return this;
    }

    public ChatMessageInfoContext setMessageSecret(byte[] messageSecret) {
        this.messageSecret = messageSecret;
        return this;
    }

    public ChatMessageInfoContext setPaddingBytes(byte[] paddingBytes) {
        this.paddingBytes = paddingBytes;
        return this;
    }

    public ChatMessageInfoContext setMessageAddOnDurationInSecs(Integer messageAddOnDurationInSecs) {
        this.messageAddOnDurationInSecs = messageAddOnDurationInSecs;
        return this;
    }

    public ChatMessageInfoContext setBotMessageSecret(byte[] botMessageSecret) {
        this.botMessageSecret = botMessageSecret;
        return this;
    }

    public ChatMessageInfoContext setBotMetadata(BotMetadata botMetadata) {
        this.botMetadata = botMetadata;
        return this;
    }

    public ChatMessageInfoContext setReportingTokenVersion(Integer reportingTokenVersion) {
        this.reportingTokenVersion = reportingTokenVersion;
        return this;
    }

    public ChatMessageInfoContext setMessageAddOnExpiryType(MessageAddOnContextInfo.ExpiryType messageAddOnExpiryType) {
        this.messageAddOnExpiryType = messageAddOnExpiryType;
        return this;
    }

    public ChatMessageInfoContext setMessageAssociation(MessageAssociation messageAssociation) {
        this.messageAssociation = messageAssociation;
        return this;
    }

    public ChatMessageInfoContext setCapiCreatedGroup(Boolean capiCreatedGroup) {
        this.capiCreatedGroup = capiCreatedGroup;
        return this;
    }

    public ChatMessageInfoContext setSupportPayload(String supportPayload) {
        this.supportPayload = supportPayload;
        return this;
    }

    public ChatMessageInfoContext setLimitSharing(ChatLimitSharing limitSharing) {
        this.limitSharing = limitSharing;
        return this;
    }

    public ChatMessageInfoContext setLimitSharingV2(ChatLimitSharing limitSharingV2) {
        this.limitSharingV2 = limitSharingV2;
        return this;
    }

    public ChatMessageInfoContext setThreadId(List<MessageThreadId> threadId) {
        this.threadId = threadId;
        return this;
    }

    public ChatMessageInfoContext setWeblinkRenderConfig(MessageLinkRender weblinkRenderConfig) {
        this.weblinkRenderConfig = weblinkRenderConfig;
        return this;
    }

}
