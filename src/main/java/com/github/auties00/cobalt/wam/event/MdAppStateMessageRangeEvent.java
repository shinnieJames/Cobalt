package com.github.auties00.cobalt.wam.event;

import com.github.auties00.cobalt.wam.annotation.WamEvent;
import com.github.auties00.cobalt.wam.annotation.WamProperty;
import com.github.auties00.cobalt.wam.type.WamType;

import java.util.OptionalInt;

@WamEvent(id = 2522, betaWeight = 20, releaseWeight = 1000)
public interface MdAppStateMessageRangeEvent extends WamEventSpec {
    @WamProperty(index = 1, type = WamType.INTEGER)
    OptionalInt additionalMessagesCount();
}
