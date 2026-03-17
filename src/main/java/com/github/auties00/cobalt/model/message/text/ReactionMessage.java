package com.github.auties00.cobalt.model.message.text;

import com.github.auties00.cobalt.model.message.MessageKey;
import com.github.auties00.cobalt.model.message.Message;

import java.time.Instant;

import com.github.auties00.cobalt.model.mixin.InstantMillisMixin;
import it.auties.protobuf.annotation.*;
import it.auties.protobuf.model.*;
import java.util.Optional;

@ProtobufMessage(name = "Message.ReactionMessage")
public final class ReactionMessage implements Message {
    @ProtobufProperty(index = 1, type = ProtobufType.MESSAGE)
    MessageKey key;

    @ProtobufProperty(index = 2, type = ProtobufType.STRING)
    String text;

    @ProtobufProperty(index = 3, type = ProtobufType.STRING)
    String groupingKey;

    @ProtobufProperty(index = 4, type = ProtobufType.INT64, mixins = InstantMillisMixin.class)
    Instant senderTimestampMs;


    ReactionMessage(MessageKey key, String text, String groupingKey, Instant senderTimestampMs) {
        this.key = key;
        this.text = text;
        this.groupingKey = groupingKey;
        this.senderTimestampMs = senderTimestampMs;
    }

    public Optional<MessageKey> key() {
        return Optional.ofNullable(key);
    }

    public Optional<String> text() {
        return Optional.ofNullable(text);
    }

    public Optional<String> groupingKey() {
        return Optional.ofNullable(groupingKey);
    }

    public Optional<Instant> senderTimestampMs() {
        return Optional.ofNullable(senderTimestampMs);
    }

    public void setKey(MessageKey key) {
        this.key = key;
    }

    public void setText(String text) {
        this.text = text;
    }

    public void setGroupingKey(String groupingKey) {
        this.groupingKey = groupingKey;
    }

    public void setSenderTimestampMs(Instant senderTimestampMs) {
        this.senderTimestampMs = senderTimestampMs;
    }
}
