package com.github.auties00.cobalt.wam.event;

import com.github.auties00.cobalt.wam.annotation.WamEvent;
import com.github.auties00.cobalt.wam.annotation.WamProperty;
import com.github.auties00.cobalt.wam.type.WamType;
import com.github.auties00.cobalt.wam.type.LwiEntryPoint;
import com.github.auties00.cobalt.wam.type.LwiSubEntryPoint;

import java.util.Optional;
import java.util.OptionalInt;

@WamEvent(id = 2906)
public interface LwiEntryPointImpressionEvent extends WamEventSpec {
    @WamProperty(index = 2, type = WamType.STRING)
    Optional<String> businessToolsSessionId();

    @WamProperty(index = 1, type = WamType.STRING)
    Optional<String> catalogSessionId();

    @WamProperty(index = 7, type = WamType.INTEGER)
    OptionalInt itemsCount();

    @WamProperty(index = 3, type = WamType.ENUM)
    Optional<LwiEntryPoint> lwiEntryPoint();

    @WamProperty(index = 6, type = WamType.ENUM)
    Optional<LwiSubEntryPoint> lwiSubEntryPoint();

    @WamProperty(index = 5, type = WamType.INTEGER)
    OptionalInt statusSessionId();

    @WamProperty(index = 4, type = WamType.BOOLEAN)
    Optional<Boolean> userHasLinkedFbPage();
}
