package com.github.auties00.cobalt.wam.event;

import com.github.auties00.cobalt.wam.annotation.WamEvent;
import com.github.auties00.cobalt.wam.annotation.WamProperty;
import com.github.auties00.cobalt.wam.type.WamType;

import java.util.OptionalInt;

@WamEvent(id = 3058)
public interface GroupCatchUpEvent extends WamEventSpec {
    @WamProperty(index = 4, type = WamType.INTEGER)
    OptionalInt mentionsCountPendingPercentage();
}
