package com.github.auties00.cobalt.model.message.addon;

import com.github.auties00.cobalt.model.message.MessageStatus;
import com.github.auties00.cobalt.model.message.MessageKey;
import com.github.auties00.cobalt.model.message.MessageContainer;
import com.github.auties00.cobalt.model.mixin.InstantMillisMixin;
import it.auties.protobuf.annotation.ProtobufEnum;
import it.auties.protobuf.annotation.ProtobufEnumIndex;
import it.auties.protobuf.annotation.ProtobufMessage;
import it.auties.protobuf.annotation.ProtobufProperty;
import it.auties.protobuf.model.ProtobufType;

import java.time.Instant;
import java.util.Optional;

@ProtobufMessage(name = "MessageAddOn")
public final class MessageAddOn {
    @ProtobufProperty(index = 1, type = ProtobufType.ENUM)
    MessageAddOnType messageAddOnType;

    @ProtobufProperty(index = 2, type = ProtobufType.MESSAGE)
    MessageContainer messageContainerAddOn;

    @ProtobufProperty(index = 3, type = ProtobufType.INT64, mixins = InstantMillisMixin.class)
    Instant senderTimestampMs;

    @ProtobufProperty(index = 4, type = ProtobufType.INT64, mixins = InstantMillisMixin.class)
    Instant serverTimestampMs;

    @ProtobufProperty(index = 5, type = ProtobufType.ENUM)
    MessageStatus status;

    @ProtobufProperty(index = 6, type = ProtobufType.MESSAGE)
    MessageAddOnContextInfo addOnContextInfo;

    @ProtobufProperty(index = 7, type = ProtobufType.MESSAGE)
    MessageKey messageAddOnKey;

    @ProtobufProperty(index = 8, type = ProtobufType.MESSAGE)
    LegacyMessageContainer legacyMessage;


    MessageAddOn(MessageAddOnType messageAddOnType, MessageContainer messageContainerAddOn, Instant senderTimestampMs, Instant serverTimestampMs, MessageStatus status, MessageAddOnContextInfo addOnContextInfo, MessageKey messageAddOnKey, LegacyMessageContainer legacyMessage) {
        this.messageAddOnType = messageAddOnType;
        this.messageContainerAddOn = messageContainerAddOn;
        this.senderTimestampMs = senderTimestampMs;
        this.serverTimestampMs = serverTimestampMs;
        this.status = status;
        this.addOnContextInfo = addOnContextInfo;
        this.messageAddOnKey = messageAddOnKey;
        this.legacyMessage = legacyMessage;
    }

    public Optional<MessageAddOnType> messageAddOnType() {
        return Optional.ofNullable(messageAddOnType);
    }

    public Optional<MessageContainer> messageAddOn() {
        return Optional.ofNullable(messageContainerAddOn);
    }

    public Optional<Instant> senderTimestampMs() {
        return Optional.ofNullable(senderTimestampMs);
    }

    public Optional<Instant> serverTimestampMs() {
        return Optional.ofNullable(serverTimestampMs);
    }

    public Optional<MessageStatus> status() {
        return Optional.ofNullable(status);
    }

    public Optional<MessageAddOnContextInfo> addOnContextInfo() {
        return Optional.ofNullable(addOnContextInfo);
    }

    public Optional<MessageKey> messageAddOnKey() {
        return Optional.ofNullable(messageAddOnKey);
    }

    public Optional<LegacyMessageContainer> legacyMessage() {
        return Optional.ofNullable(legacyMessage);
    }

    public void setMessageAddOnType(MessageAddOnType messageAddOnType) {
        this.messageAddOnType = messageAddOnType;
    }

    public void setMessageAddOn(MessageContainer messageContainerAddOn) {
        this.messageContainerAddOn = messageContainerAddOn;
    }

    public void setSenderTimestampMs(Instant senderTimestampMs) {
        this.senderTimestampMs = senderTimestampMs;
    }

    public void setServerTimestampMs(Instant serverTimestampMs) {
        this.serverTimestampMs = serverTimestampMs;
    }

    public void setStatus(MessageStatus status) {
        this.status = status;
    }

    public void setAddOnContextInfo(MessageAddOnContextInfo addOnContextInfo) {
        this.addOnContextInfo = addOnContextInfo;
    }

    public void setMessageAddOnKey(MessageKey messageAddOnKey) {
        this.messageAddOnKey = messageAddOnKey;
    }

    public void setLegacyMessage(LegacyMessageContainer legacyMessage) {
        this.legacyMessage = legacyMessage;
    }

    @ProtobufEnum(name = "MessageAddOn.MessageAddOnType")
    public static enum MessageAddOnType {
        UNDEFINED(0),
        REACTION(1),
        EVENT_RESPONSE(2),
        POLL_UPDATE(3),
        PIN_IN_CHAT(4);

        MessageAddOnType(@ProtobufEnumIndex int index) {
            this.index = index;
        }

        final int index;

        public int index() {
            return this.index;
        }
    }
}
