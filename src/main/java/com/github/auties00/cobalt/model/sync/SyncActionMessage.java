package com.github.auties00.cobalt.model.sync;

import com.github.auties00.cobalt.model.message.MessageKey;

import java.time.Instant;
import it.auties.protobuf.annotation.*;
import it.auties.protobuf.model.*;
import java.util.Optional;

@ProtobufMessage(name = "SyncActionValue.SyncActionMessage")
public final class SyncActionMessage implements SyncAction {
    @ProtobufProperty(index = 1, type = ProtobufType.MESSAGE)
    MessageKey key;

    @ProtobufProperty(index = 2, type = ProtobufType.INT64, mixins = InstantProtobufMixin.class)
    Instant timestamp;


    SyncActionMessage(MessageKey key, Instant timestamp) {
        this.key = key;
        this.timestamp = timestamp;
    }

    public Optional<MessageKey> key() {
        return Optional.ofNullable(key);
    }

    public Optional<Instant> timestamp() {
        return Optional.ofNullable(timestamp);
    }

    public SyncActionMessage setKey(MessageKey key) {
        this.key = key;
        return this;
    }

    public SyncActionMessage setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
        return this;
    }
}
