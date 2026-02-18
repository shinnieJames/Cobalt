package com.github.auties00.cobalt.wam.event;

import com.github.auties00.cobalt.wam.annotation.WamEvent;
import com.github.auties00.cobalt.wam.annotation.WamProperty;
import com.github.auties00.cobalt.wam.type.WamType;

import java.util.OptionalDouble;

@WamEvent(id = 1700)
public interface WebcImgErrorEvent extends WamEventSpec {
    @WamProperty(index = 1, type = WamType.FLOAT)
    OptionalDouble webcImgErrorCode();
}
