package com.github.auties00.cobalt.wam.event;

import com.github.auties00.cobalt.wam.annotation.WamEvent;
import com.github.auties00.cobalt.wam.annotation.WamProperty;
import com.github.auties00.cobalt.wam.type.WamType;
import com.github.auties00.cobalt.wam.type.WebcButterbarActionType;
import com.github.auties00.cobalt.wam.type.WebcButterbarBbType;

import java.util.Optional;

@WamEvent(id = 3932)
public interface WebcButterbarEventEvent extends WamEventSpec {
    @WamProperty(index = 1, type = WamType.ENUM)
    Optional<WebcButterbarActionType> webcButterbarAction();

    @WamProperty(index = 2, type = WamType.ENUM)
    Optional<WebcButterbarBbType> webcButterbarType();
}
