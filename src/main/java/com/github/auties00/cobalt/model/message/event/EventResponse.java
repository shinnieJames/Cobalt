package com.github.auties00.cobalt.model.message.event;

import com.github.auties00.cobalt.model.message.MessageKey;

import java.time.Instant;

import com.github.auties00.cobalt.model.mixin.InstantMillisMixin;
import it.auties.protobuf.annotation.*;
import it.auties.protobuf.model.*;
import java.util.Optional;

@ProtobufMessage(name = "EventResponse")
public final class EventResponse {
    @ProtobufProperty(index = 1, type = ProtobufType.MESSAGE)
    MessageKey eventResponseMessageKey;

    @ProtobufProperty(index = 2, type = ProtobufType.INT64, mixins = InstantMillisMixin.class)
    Instant timestampMs;

    @ProtobufProperty(index = 3, type = ProtobufType.MESSAGE)
    EventResponseMessage eventResponseMessage;

    @ProtobufProperty(index = 4, type = ProtobufType.BOOL)
    Boolean unread;


    EventResponse(MessageKey eventResponseMessageKey, Instant timestampMs, EventResponseMessage eventResponseMessage, Boolean unread) {
        this.eventResponseMessageKey = eventResponseMessageKey;
        this.timestampMs = timestampMs;
        this.eventResponseMessage = eventResponseMessage;
        this.unread = unread;
    }

    public Optional<MessageKey> eventResponseMessageKey() {
        return Optional.ofNullable(eventResponseMessageKey);
    }

    public Optional<Instant> timestampMs() {
        return Optional.ofNullable(timestampMs);
    }

    public Optional<EventResponseMessage> eventResponseMessage() {
        return Optional.ofNullable(eventResponseMessage);
    }

    public boolean unread() {
        return unread != null && unread;
    }

    public void setEventResponseMessageKey(MessageKey eventResponseMessageKey) {
        this.eventResponseMessageKey = eventResponseMessageKey;
    }

    public void setTimestampMs(Instant timestampMs) {
        this.timestampMs = timestampMs;
    }

    public void setEventResponseMessage(EventResponseMessage eventResponseMessage) {
        this.eventResponseMessage = eventResponseMessage;
    }

    public void setUnread(Boolean unread) {
        this.unread = unread;
    }
}
