package com.github.auties00.cobalt.wam.event;

import com.github.auties00.cobalt.wam.annotation.WamEvent;
import com.github.auties00.cobalt.wam.annotation.WamProperty;
import com.github.auties00.cobalt.wam.type.WamType;

import java.time.Instant;
import java.util.Optional;
import java.util.OptionalInt;

@WamEvent(id = 3782)
public interface MexEventEvent extends WamEventSpec {
    @WamProperty(index = 1, type = WamType.BOOLEAN)
    Optional<Boolean> isMex();

    @WamProperty(index = 2, type = WamType.STRING)
    Optional<String> mexEventData();

    @WamProperty(index = 12, type = WamType.TIMER)
    Optional<Instant> mexEventDurationT();

    @WamProperty(index = 3, type = WamType.INTEGER)
    OptionalInt mexEventEndTime();

    @WamProperty(index = 13, type = WamType.INTEGER)
    OptionalInt mexEventEnvelopeResponseStatus();

    @WamProperty(index = 7, type = WamType.STRING)
    Optional<String> mexEventOperation();

    @WamProperty(index = 14, type = WamType.INTEGER)
    OptionalInt mexEventPayloadResponseStatus();

    @WamProperty(index = 8, type = WamType.INTEGER)
    OptionalInt mexEventRequestSize();

    @WamProperty(index = 9, type = WamType.INTEGER)
    OptionalInt mexEventResponseSize();

    @WamProperty(index = 10, type = WamType.INTEGER)
    OptionalInt mexEventRetries();

    @WamProperty(index = 11, type = WamType.INTEGER)
    OptionalInt mexEventStartTime();
}
