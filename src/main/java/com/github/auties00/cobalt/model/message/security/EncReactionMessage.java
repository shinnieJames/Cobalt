package com.github.auties00.cobalt.model.message.security;

import com.github.auties00.cobalt.model.message.MessageKey;
import com.github.auties00.cobalt.model.message.Message;

import it.auties.protobuf.annotation.*;
import it.auties.protobuf.model.*;
import java.util.Optional;

@ProtobufMessage(name = "Message.EncReactionMessage")
public final class EncReactionMessage implements Message {
    @ProtobufProperty(index = 1, type = ProtobufType.MESSAGE)
    MessageKey targetMessageKey;

    @ProtobufProperty(index = 2, type = ProtobufType.BYTES)
    byte[] encPayload;

    @ProtobufProperty(index = 3, type = ProtobufType.BYTES)
    byte[] encIv;


    EncReactionMessage(MessageKey targetMessageKey, byte[] encPayload, byte[] encIv) {
        this.targetMessageKey = targetMessageKey;
        this.encPayload = encPayload;
        this.encIv = encIv;
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

    public EncReactionMessage setTargetMessageKey(MessageKey targetMessageKey) {
        this.targetMessageKey = targetMessageKey;
        return this;
    }

    public EncReactionMessage setEncPayload(byte[] encPayload) {
        this.encPayload = encPayload;
        return this;
    }

    public EncReactionMessage setEncIv(byte[] encIv) {
        this.encIv = encIv;
        return this;
    }
}
