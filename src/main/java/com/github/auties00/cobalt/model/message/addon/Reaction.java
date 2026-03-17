package com.github.auties00.cobalt.model.message.addon;

import com.github.auties00.cobalt.model.message.MessageKey;
import com.github.auties00.cobalt.model.mixin.InstantMillisMixin;
import it.auties.protobuf.annotation.ProtobufMessage;
import it.auties.protobuf.annotation.ProtobufProperty;
import it.auties.protobuf.model.ProtobufType;

import java.time.Instant;
import java.util.Optional;

@ProtobufMessage(name = "Reaction")
public final class Reaction {
    @ProtobufProperty(index = 1, type = ProtobufType.MESSAGE)
    MessageKey key;

    @ProtobufProperty(index = 2, type = ProtobufType.STRING)
    String text;

    @ProtobufProperty(index = 3, type = ProtobufType.STRING)
    String groupingKey;

    @ProtobufProperty(index = 4, type = ProtobufType.INT64, mixins = InstantMillisMixin.class)
    Instant senderTimestampMs;

    @ProtobufProperty(index = 5, type = ProtobufType.BOOL)
    Boolean unread;


    Reaction(MessageKey key, String text, String groupingKey, Instant senderTimestampMs, Boolean unread) {
        this.key = key;
        this.text = text;
        this.groupingKey = groupingKey;
        this.senderTimestampMs = senderTimestampMs;
        this.unread = unread;
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

    public boolean unread() {
        return unread != null && unread;
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

    public void setUnread(Boolean unread) {
        this.unread = unread;
    }
}
