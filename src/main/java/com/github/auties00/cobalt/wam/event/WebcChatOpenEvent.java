package com.github.auties00.cobalt.wam.event;

import com.github.auties00.cobalt.wam.annotation.WamEvent;
import com.github.auties00.cobalt.wam.annotation.WamProperty;
import com.github.auties00.cobalt.wam.type.WamType;

import java.time.Instant;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;

@WamEvent(id = 864, releaseWeight = 5)
public interface WebcChatOpenEvent extends WamEventSpec {
    @WamProperty(index = 7, type = WamType.TIMER)
    Optional<Instant> webcChatOpenBeforePaintT();

    @WamProperty(index = 6, type = WamType.TIMER)
    Optional<Instant> webcChatOpenPaintedT();

    @WamProperty(index = 2, type = WamType.TIMER)
    Optional<Instant> webcChatOpenT();

    @WamProperty(index = 5, type = WamType.INTEGER)
    OptionalInt webcFinalRenderedMessageCount();

    @WamProperty(index = 4, type = WamType.INTEGER)
    OptionalInt webcRenderedMessageCount();

    @WamProperty(index = 1, type = WamType.FLOAT)
    OptionalDouble webcUnreadCount();

    @WamProperty(index = 8, type = WamType.FLOAT)
    OptionalDouble webcWindowHeightFloat();
}
