package com.github.auties00.cobalt.wam.event;

import com.github.auties00.cobalt.wam.annotation.WamEvent;
import com.github.auties00.cobalt.wam.annotation.WamProperty;
import com.github.auties00.cobalt.wam.type.WamType;
import com.github.auties00.cobalt.wam.type.DisappearingModeEntryPointType;
import com.github.auties00.cobalt.wam.type.DisappearingModeSettingEventNameType;

import java.util.Optional;
import java.util.OptionalInt;

@WamEvent(id = 3446)
public interface DisappearingModeSettingEventsEvent extends WamEventSpec {
    @WamProperty(index = 5, type = WamType.ENUM)
    Optional<DisappearingModeEntryPointType> disappearingModeEntryPoint();

    @WamProperty(index = 1, type = WamType.ENUM)
    Optional<DisappearingModeSettingEventNameType> disappearingModeSettingEventName();

    @WamProperty(index = 2, type = WamType.INTEGER)
    OptionalInt lastToggleTimestamp();

    @WamProperty(index = 3, type = WamType.INTEGER)
    OptionalInt newEphemeralityDuration();

    @WamProperty(index = 4, type = WamType.INTEGER)
    OptionalInt previousEphemeralityDuration();
}
