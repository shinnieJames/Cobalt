package com.github.auties00.cobalt.wam.event;

import com.github.auties00.cobalt.wam.annotation.WamEvent;
import com.github.auties00.cobalt.wam.annotation.WamProperty;
import com.github.auties00.cobalt.wam.type.WamType;

import java.util.Optional;

@WamEvent(id = 2520, betaWeight = 20, releaseWeight = 1000)
public interface MdAppStateDirtyBitsEvent extends WamEventSpec {
    @WamProperty(index = 2, type = WamType.BOOLEAN)
    Optional<Boolean> dirtyBitsFalsePositive();
}
