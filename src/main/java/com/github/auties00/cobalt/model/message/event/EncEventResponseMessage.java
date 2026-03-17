package com.github.auties00.cobalt.model.message.event;

import com.github.auties00.cobalt.model.message.MessageKey;
import com.github.auties00.cobalt.model.message.Message;

import it.auties.protobuf.annotation.*;
import it.auties.protobuf.model.*;
import java.util.Optional;

@ProtobufMessage(name = "Message.EncEventResponseMessage")
public final class EncEventResponseMessage implements Message {
    @ProtobufProperty(index = 1, type = ProtobufType.MESSAGE)
    MessageKey eventCreationMessageKey;

    @ProtobufProperty(index = 2, type = ProtobufType.BYTES)
    byte[] encPayload;

    @ProtobufProperty(index = 3, type = ProtobufType.BYTES)
    byte[] encIv;


    EncEventResponseMessage(MessageKey eventCreationMessageKey, byte[] encPayload, byte[] encIv) {
        this.eventCreationMessageKey = eventCreationMessageKey;
        this.encPayload = encPayload;
        this.encIv = encIv;
    }

    public Optional<MessageKey> eventCreationMessageKey() {
        return Optional.ofNullable(eventCreationMessageKey);
    }

    public Optional<byte[]> encPayload() {
        return Optional.ofNullable(encPayload);
    }

    public Optional<byte[]> encIv() {
        return Optional.ofNullable(encIv);
    }

    public void setEventCreationMessageKey(MessageKey eventCreationMessageKey) {
        this.eventCreationMessageKey = eventCreationMessageKey;
    }

    public void setEncPayload(byte[] encPayload) {
        this.encPayload = encPayload;
    }

    public void setEncIv(byte[] encIv) {
        this.encIv = encIv;
    }
}
