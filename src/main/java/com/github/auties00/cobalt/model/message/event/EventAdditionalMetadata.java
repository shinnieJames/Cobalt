package com.github.auties00.cobalt.model.message.event;

import it.auties.protobuf.annotation.ProtobufMessage;
import it.auties.protobuf.annotation.ProtobufProperty;
import it.auties.protobuf.model.ProtobufType;

@ProtobufMessage(name = "EventAdditionalMetadata")
public final class EventAdditionalMetadata {
    @ProtobufProperty(index = 1, type = ProtobufType.BOOL)
    Boolean isStale;


    EventAdditionalMetadata(Boolean isStale) {
        this.isStale = isStale;
    }

    public boolean isStale() {
        return isStale != null && isStale;
    }

    public void setStale(Boolean isStale) {
        this.isStale = isStale;
    }
}
