package com.github.auties00.cobalt.model.message.security;

import com.github.auties00.cobalt.model.message.MessageKey;
import com.github.auties00.cobalt.model.message.Message;

import it.auties.protobuf.annotation.*;
import it.auties.protobuf.model.*;
import java.util.Optional;

@ProtobufMessage(name = "Message.EncCommentMessage")
public final class EncCommentMessage implements Message {
    @ProtobufProperty(index = 1, type = ProtobufType.MESSAGE)
    MessageKey targetMessageKey;

    @ProtobufProperty(index = 2, type = ProtobufType.BYTES)
    byte[] encPayload;

    @ProtobufProperty(index = 3, type = ProtobufType.BYTES)
    byte[] encIv;


    EncCommentMessage(MessageKey targetMessageKey, byte[] encPayload, byte[] encIv) {
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

    public EncCommentMessage setTargetMessageKey(MessageKey targetMessageKey) {
        this.targetMessageKey = targetMessageKey;
        return this;
    }

    public EncCommentMessage setEncPayload(byte[] encPayload) {
        this.encPayload = encPayload;
        return this;
    }

    public EncCommentMessage setEncIv(byte[] encIv) {
        this.encIv = encIv;
        return this;
    }
}
