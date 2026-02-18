package com.github.auties00.cobalt.model.message.system;

import com.github.auties00.cobalt.model.chat.ChatKeepType;
import com.github.auties00.cobalt.model.message.MessageKey;
import com.github.auties00.cobalt.model.message.Message;

import java.time.Instant;
import it.auties.protobuf.annotation.*;
import it.auties.protobuf.model.*;
import java.util.Optional;

@ProtobufMessage(name = "Message.KeepInChatMessage")
public final class KeepInChatMessage implements Message {
    @ProtobufProperty(index = 1, type = ProtobufType.MESSAGE)
    MessageKey key;

    @ProtobufProperty(index = 2, type = ProtobufType.ENUM)
    ChatKeepType keepType;

    @ProtobufProperty(index = 3, type = ProtobufType.INT64, mixins = InstantProtobufMixin.class)
    Instant timestampMs;


    KeepInChatMessage(MessageKey key, ChatKeepType keepType, Instant timestampMs) {
        this.key = key;
        this.keepType = keepType;
        this.timestampMs = timestampMs;
    }

    public Optional<MessageKey> key() {
        return Optional.ofNullable(key);
    }

    public Optional<ChatKeepType> keepType() {
        return Optional.ofNullable(keepType);
    }

    public Optional<Instant> timestampMs() {
        return Optional.ofNullable(timestampMs);
    }

    public KeepInChatMessage setKey(MessageKey key) {
        this.key = key;
        return this;
    }

    public KeepInChatMessage setKeepType(ChatKeepType keepType) {
        this.keepType = keepType;
        return this;
    }

    public KeepInChatMessage setTimestampMs(Instant timestampMs) {
        this.timestampMs = timestampMs;
        return this;
    }
}
