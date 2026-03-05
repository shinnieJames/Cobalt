package com.github.auties00.cobalt.wam.event;

import com.github.auties00.cobalt.wam.annotation.WamEvent;
import com.github.auties00.cobalt.wam.annotation.WamProperty;
import com.github.auties00.cobalt.wam.type.WamType;
import com.github.auties00.cobalt.wam.type.WebcNavbarItemLabel;

import java.util.Optional;

@WamEvent(id = 5258)
public interface WebcNavbarEvent extends WamEventSpec {
    @WamProperty(index = 1, type = WamType.ENUM)
    Optional<WebcNavbarItemLabel> webcNavbarItemLabel();
}
