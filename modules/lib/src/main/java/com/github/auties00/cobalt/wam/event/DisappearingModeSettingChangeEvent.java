package com.github.auties00.cobalt.wam.event;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;

import com.github.auties00.cobalt.wam.annotation.WamEvent;
import com.github.auties00.cobalt.wam.model.WamEventSpec;
import com.github.auties00.cobalt.wam.annotation.WamProperty;
import com.github.auties00.cobalt.wam.model.WamType;
import com.github.auties00.cobalt.wam.type.DisappearingModeEntryPointType;
import com.github.auties00.cobalt.wam.type.PreviousEphemeralityType;

import java.util.Optional;
import java.util.OptionalInt;

@WhatsAppWebModule(moduleName = "WAWebDisappearingModeSettingChangeWamEvent")
@WamEvent(id = 3056)
public interface DisappearingModeSettingChangeEvent extends WamEventSpec {
    @WamProperty(index = 5, type = WamType.INTEGER)
    OptionalInt afterReadDuration();

    @WamProperty(index = 4, type = WamType.ENUM)
    Optional<DisappearingModeEntryPointType> disappearingModeEntryPoint();

    @WamProperty(index = 7, type = WamType.INTEGER)
    OptionalInt errorCode();

    @WamProperty(index = 8, type = WamType.BOOLEAN)
    Optional<Boolean> isAfterRead();

    @WamProperty(index = 9, type = WamType.BOOLEAN)
    Optional<Boolean> isSuccess();

    @WamProperty(index = 3, type = WamType.INTEGER)
    OptionalInt lastToggleTimestamp();

    @WamProperty(index = 2, type = WamType.INTEGER)
    OptionalInt newEphemeralityDuration();

    @WamProperty(index = 1, type = WamType.INTEGER)
    OptionalInt previousEphemeralityDuration();

    @WamProperty(index = 10, type = WamType.ENUM)
    Optional<PreviousEphemeralityType> previousEphemeralityType();
}
