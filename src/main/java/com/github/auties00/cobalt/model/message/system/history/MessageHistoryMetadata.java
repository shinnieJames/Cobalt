package com.github.auties00.cobalt.model.message.system.history;

import com.github.auties00.cobalt.model.message.Message;

import java.time.Instant;
import java.util.Collections;
import java.util.List;

import com.github.auties00.cobalt.model.mixin.InstantSecondsMixin;
import it.auties.protobuf.annotation.*;
import it.auties.protobuf.model.*;
import java.util.Optional;
import java.util.OptionalLong;

@ProtobufMessage(name = "Message.MessageHistoryMetadata")
public final class MessageHistoryMetadata implements Message {
    @ProtobufProperty(index = 1, type = ProtobufType.STRING)
    List<String> historyReceivers;

    @ProtobufProperty(index = 2, type = ProtobufType.INT64, mixins = InstantSecondsMixin.class)
    Instant oldestMessageTimestamp;

    @ProtobufProperty(index = 3, type = ProtobufType.INT64)
    Long messageCount;


    MessageHistoryMetadata(List<String> historyReceivers, Instant oldestMessageTimestamp, Long messageCount) {
        this.historyReceivers = historyReceivers;
        this.oldestMessageTimestamp = oldestMessageTimestamp;
        this.messageCount = messageCount;
    }

    public List<String> historyReceivers() {
        return historyReceivers == null ? List.of() : Collections.unmodifiableList(historyReceivers);
    }

    public Optional<Instant> oldestMessageTimestamp() {
        return Optional.ofNullable(oldestMessageTimestamp);
    }

    public OptionalLong messageCount() {
        return messageCount == null ? OptionalLong.empty() : OptionalLong.of(messageCount);
    }

    public void setHistoryReceivers(List<String> historyReceivers) {
        this.historyReceivers = historyReceivers;
    }

    public void setOldestMessageTimestamp(Instant oldestMessageTimestamp) {
        this.oldestMessageTimestamp = oldestMessageTimestamp;
    }

    public void setMessageCount(Long messageCount) {
        this.messageCount = messageCount;
    }
}
