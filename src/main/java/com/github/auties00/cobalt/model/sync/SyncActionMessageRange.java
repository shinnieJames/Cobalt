package com.github.auties00.cobalt.model.sync;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import it.auties.protobuf.annotation.*;
import it.auties.protobuf.model.*;
import java.util.Optional;

@ProtobufMessage(name = "SyncActionValue.SyncActionMessageRange")
public final class SyncActionMessageRange implements SyncAction {
    @ProtobufProperty(index = 1, type = ProtobufType.INT64, mixins = InstantProtobufMixin.class)
    Instant lastMessageTimestamp;

    @ProtobufProperty(index = 2, type = ProtobufType.INT64, mixins = InstantProtobufMixin.class)
    Instant lastSystemMessageTimestamp;

    @ProtobufProperty(index = 3, type = ProtobufType.MESSAGE)
    List<SyncActionMessage> messages;


    SyncActionMessageRange(Instant lastMessageTimestamp, Instant lastSystemMessageTimestamp, List<SyncActionMessage> messages) {
        this.lastMessageTimestamp = lastMessageTimestamp;
        this.lastSystemMessageTimestamp = lastSystemMessageTimestamp;
        this.messages = messages;
    }

    public Optional<Instant> lastMessageTimestamp() {
        return Optional.ofNullable(lastMessageTimestamp);
    }

    public Optional<Instant> lastSystemMessageTimestamp() {
        return Optional.ofNullable(lastSystemMessageTimestamp);
    }

    public List<SyncActionMessage> messages() {
        return messages == null ? List.of() : Collections.unmodifiableList(messages);
    }

    public SyncActionMessageRange setLastMessageTimestamp(Instant lastMessageTimestamp) {
        this.lastMessageTimestamp = lastMessageTimestamp;
        return this;
    }

    public SyncActionMessageRange setLastSystemMessageTimestamp(Instant lastSystemMessageTimestamp) {
        this.lastSystemMessageTimestamp = lastSystemMessageTimestamp;
        return this;
    }

    public SyncActionMessageRange setMessages(List<SyncActionMessage> messages) {
        this.messages = messages;
        return this;
    }
}
