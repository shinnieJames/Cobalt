package com.github.auties00.cobalt.model.message.system.appstate;

import com.github.auties00.cobalt.model.message.Message;
import com.github.auties00.cobalt.model.mixin.InstantMillisMixin;
import it.auties.protobuf.annotation.ProtobufMessage;
import it.auties.protobuf.annotation.ProtobufProperty;
import it.auties.protobuf.model.ProtobufType;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@ProtobufMessage(name = "Message.AppStateFatalExceptionNotification")
public final class AppStateFatalExceptionNotification implements Message {
    @ProtobufProperty(index = 1, type = ProtobufType.STRING)
    List<String> collectionNames;

    @ProtobufProperty(index = 2, type = ProtobufType.INT64, mixins = InstantMillisMixin.class)
    Instant timestamp;


    AppStateFatalExceptionNotification(List<String> collectionNames, Instant timestamp) {
        this.collectionNames = collectionNames;
        this.timestamp = timestamp;
    }

    public List<String> collectionNames() {
        return collectionNames == null ? List.of() : Collections.unmodifiableList(collectionNames);
    }

    public Optional<Instant> timestamp() {
        return Optional.ofNullable(timestamp);
    }

    public void setCollectionNames(List<String> collectionNames) {
        this.collectionNames = collectionNames;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }
}
