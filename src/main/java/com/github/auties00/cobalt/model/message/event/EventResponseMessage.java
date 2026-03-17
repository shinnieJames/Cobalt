package com.github.auties00.cobalt.model.message.event;

import com.github.auties00.cobalt.model.message.Message;

import java.time.Instant;

import com.github.auties00.cobalt.model.mixin.InstantMillisMixin;
import it.auties.protobuf.annotation.*;
import it.auties.protobuf.model.*;
import java.util.Optional;
import java.util.OptionalInt;

@ProtobufMessage(name = "Message.EventResponseMessage")
public final class EventResponseMessage implements Message {
    @ProtobufProperty(index = 1, type = ProtobufType.ENUM)
    EventResponseType response;

    @ProtobufProperty(index = 2, type = ProtobufType.INT64, mixins = InstantMillisMixin.class)
    Instant timestampMs;

    @ProtobufProperty(index = 3, type = ProtobufType.INT32)
    Integer extraGuestCount;


    EventResponseMessage(EventResponseType response, Instant timestampMs, Integer extraGuestCount) {
        this.response = response;
        this.timestampMs = timestampMs;
        this.extraGuestCount = extraGuestCount;
    }

    public Optional<EventResponseType> response() {
        return Optional.ofNullable(response);
    }

    public Optional<Instant> timestampMs() {
        return Optional.ofNullable(timestampMs);
    }

    public OptionalInt extraGuestCount() {
        return extraGuestCount == null ? OptionalInt.empty() : OptionalInt.of(extraGuestCount);
    }

    public void setResponse(EventResponseType response) {
        this.response = response;
    }

    public void setTimestampMs(Instant timestampMs) {
        this.timestampMs = timestampMs;
    }

    public void setExtraGuestCount(Integer extraGuestCount) {
        this.extraGuestCount = extraGuestCount;
    }

    @ProtobufEnum(name = "Message.EventResponseMessage.EventResponseType")
    public static enum EventResponseType {
        UNKNOWN(0),
        GOING(1),
        NOT_GOING(2),
        MAYBE(3);

        EventResponseType(@ProtobufEnumIndex int index) {
            this.index = index;
        }

        final int index;

        public int index() {
            return this.index;
        }
    }
}
