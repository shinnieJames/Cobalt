package com.github.auties00.cobalt.model.message.system;

import com.github.auties00.cobalt.model.message.MessageKey;
import com.github.auties00.cobalt.model.message.Message;

import java.time.Instant;

import com.github.auties00.cobalt.model.mixin.InstantMillisMixin;
import it.auties.protobuf.annotation.*;
import it.auties.protobuf.model.*;
import java.util.Optional;

@ProtobufMessage(name = "Message.PinInChatMessage")
public final class PinInChatMessage implements Message {
    @ProtobufProperty(index = 1, type = ProtobufType.MESSAGE)
    MessageKey key;

    @ProtobufProperty(index = 2, type = ProtobufType.ENUM)
    Type type;

    @ProtobufProperty(index = 3, type = ProtobufType.INT64, mixins = InstantMillisMixin.class)
    Instant senderTimestampMs;


    PinInChatMessage(MessageKey key, Type type, Instant senderTimestampMs) {
        this.key = key;
        this.type = type;
        this.senderTimestampMs = senderTimestampMs;
    }

    public Optional<MessageKey> key() {
        return Optional.ofNullable(key);
    }

    public Optional<Type> type() {
        return Optional.ofNullable(type);
    }

    public Optional<Instant> senderTimestampMs() {
        return Optional.ofNullable(senderTimestampMs);
    }

    public void setKey(MessageKey key) {
        this.key = key;
    }

    public void setType(Type type) {
        this.type = type;
    }

    public void setSenderTimestampMs(Instant senderTimestampMs) {
        this.senderTimestampMs = senderTimestampMs;
    }

    @ProtobufEnum(name = "Message.PinInChatMessage.Type")
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
