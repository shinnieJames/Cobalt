package com.github.auties00.cobalt.model.message.addon;

import it.auties.protobuf.annotation.ProtobufEnum;
import it.auties.protobuf.annotation.ProtobufEnumIndex;
import it.auties.protobuf.annotation.ProtobufMessage;
import it.auties.protobuf.annotation.ProtobufProperty;
import it.auties.protobuf.model.ProtobufType;

import java.util.Optional;
import java.util.OptionalInt;

@ProtobufMessage(name = "MessageAddOnContextInfo")
public final class MessageAddOnContextInfo {
    @ProtobufProperty(index = 1, type = ProtobufType.UINT32)
    Integer messageAddOnDurationInSecs;

    @ProtobufProperty(index = 2, type = ProtobufType.ENUM)
    ExpiryType messageAddOnExpiryType;


    MessageAddOnContextInfo(Integer messageAddOnDurationInSecs, ExpiryType messageAddOnExpiryType) {
        this.messageAddOnDurationInSecs = messageAddOnDurationInSecs;
        this.messageAddOnExpiryType = messageAddOnExpiryType;
    }

    public OptionalInt messageAddOnDurationInSecs() {
        return messageAddOnDurationInSecs == null ? OptionalInt.empty() : OptionalInt.of(messageAddOnDurationInSecs);
    }

    public Optional<ExpiryType> expiryType() {
        return Optional.ofNullable(messageAddOnExpiryType);
    }

    public void setMessageAddOnDurationInSecs(Integer messageAddOnDurationInSecs) {
        this.messageAddOnDurationInSecs = messageAddOnDurationInSecs;
    }

    public void setExpiryType(ExpiryType expiryType) {
        this.messageAddOnExpiryType = expiryType;
    }

    @ProtobufEnum(name = "MessageContextInfo.MessageAddonExpiryType")
    public enum ExpiryType {
        STATIC(1),
        DEPENDENT_ON_PARENT(2);

        ExpiryType(@ProtobufEnumIndex int index) {
            this.index = index;
        }

        final int index;

        public int index() {
            return this.index;
        }
    }
}
