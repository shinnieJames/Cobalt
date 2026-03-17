package com.github.auties00.cobalt.model.message.security;

import com.github.auties00.cobalt.model.message.MessageKey;
import com.github.auties00.cobalt.model.message.Message;

import it.auties.protobuf.annotation.*;
import it.auties.protobuf.model.*;
import java.util.Optional;

@ProtobufMessage(name = "Message.SecretEncryptedMessage")
public final class SecretEncMessage implements Message {
    @ProtobufProperty(index = 1, type = ProtobufType.MESSAGE)
    MessageKey targetMessageKey;

    @ProtobufProperty(index = 2, type = ProtobufType.BYTES)
    byte[] encPayload;

    @ProtobufProperty(index = 3, type = ProtobufType.BYTES)
    byte[] encIv;

    @ProtobufProperty(index = 4, type = ProtobufType.ENUM)
    SecretEncType secretEncType;


    SecretEncMessage(MessageKey targetMessageKey, byte[] encPayload, byte[] encIv, SecretEncType secretEncType) {
        this.targetMessageKey = targetMessageKey;
        this.encPayload = encPayload;
        this.encIv = encIv;
        this.secretEncType = secretEncType;
    }

    public Optional<MessageKey> targetMessageKey() {
        return Optional.ofNullable(targetMessageKey);
    }

    public Optional<byte[]> encPayload() {
        return Optional.ofNullable(encPayload);
    }

    public Optional<byte[]> encIv() {
        return Optional.ofNullable(encIv);
    }

    public Optional<SecretEncType> secretEncType() {
        return Optional.ofNullable(secretEncType);
    }

    public void setTargetMessageKey(MessageKey targetMessageKey) {
        this.targetMessageKey = targetMessageKey;
    }

    public void setEncPayload(byte[] encPayload) {
        this.encPayload = encPayload;
    }

    public void setEncIv(byte[] encIv) {
        this.encIv = encIv;
    }

    public void setSecretEncType(SecretEncType secretEncType) {
        this.secretEncType = secretEncType;
    }

    @ProtobufEnum(name = "Message.SecretEncryptedMessage.SecretEncType")
    public static enum SecretEncType {
        UNKNOWN(0),
        EVENT_EDIT(1),
        MESSAGE_EDIT(2);

        SecretEncType(@ProtobufEnumIndex int index) {
            this.index = index;
        }

        final int index;

        public int index() {
            return this.index;
        }
    }
}
