package com.github.auties00.cobalt.wam.event;

import com.github.auties00.cobalt.wam.annotation.WamEvent;
import com.github.auties00.cobalt.wam.annotation.WamProperty;
import com.github.auties00.cobalt.wam.type.WamType;

import java.util.Optional;
import java.util.OptionalInt;

@WamEvent(id = 4358)
public interface WamDroppedEventEvent extends WamEventSpec {
    @WamProperty(index = 1, type = WamType.INTEGER)
    OptionalInt droppedEventCode();

    @WamProperty(index = 2, type = WamType.INTEGER)
    OptionalInt droppedEventCount();

    @WamProperty(index = 3, type = WamType.BOOLEAN)
    Optional<Boolean> isFromWamsys();
}
