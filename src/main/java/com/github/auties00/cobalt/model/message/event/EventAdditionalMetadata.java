package com.github.auties00.cobalt.model.message.event;

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

    public EventAdditionalMetadata setStale(Boolean isStale) {
        this.isStale = isStale;
        return this;
    }
}
