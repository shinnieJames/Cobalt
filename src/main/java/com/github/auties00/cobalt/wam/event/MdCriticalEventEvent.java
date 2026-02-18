package com.github.auties00.cobalt.wam.event;

import com.github.auties00.cobalt.wam.annotation.WamEvent;
import com.github.auties00.cobalt.wam.annotation.WamProperty;
import com.github.auties00.cobalt.wam.type.WamType;
import com.github.auties00.cobalt.wam.type.Collection;
import com.github.auties00.cobalt.wam.type.MdSyncdCriticalEventCode;

import java.util.Optional;

@WamEvent(id = 2746)
public interface MdCriticalEventEvent extends WamEventSpec {
    @WamProperty(index = 2, type = WamType.ENUM)
    Optional<Collection> collection();

    @WamProperty(index = 1, type = WamType.ENUM)
    Optional<MdSyncdCriticalEventCode> mdCriticalEventCode();

    @WamProperty(index = 3, type = WamType.STRING)
    Optional<String> mutationActionName();
}
