package com.github.auties00.cobalt.wam.event;

import com.github.auties00.cobalt.wam.annotation.WamEvent;
import com.github.auties00.cobalt.wam.annotation.WamProperty;
import com.github.auties00.cobalt.wam.type.WamType;
import com.github.auties00.cobalt.wam.type.EphemeralSettingEntryPointType;
import com.github.auties00.cobalt.wam.type.PreciseSizeBucket;

import java.util.Optional;
import java.util.OptionalInt;

@WamEvent(id = 2370)
public interface EphemeralSettingChangeEvent extends WamEventSpec {
    @WamProperty(index = 1, type = WamType.INTEGER)
    OptionalInt chatEphemeralityDuration();

    @WamProperty(index = 3, type = WamType.ENUM)
    Optional<EphemeralSettingEntryPointType> ephemeralSettingEntryPoint();

    @WamProperty(index = 5, type = WamType.ENUM)
    Optional<PreciseSizeBucket> ephemeralSettingGroupSize();

    @WamProperty(index = 2, type = WamType.INTEGER)
    OptionalInt previousEphemeralityDuration();

    @WamProperty(index = 6, type = WamType.STRING)
    Optional<String> threadId();
}
