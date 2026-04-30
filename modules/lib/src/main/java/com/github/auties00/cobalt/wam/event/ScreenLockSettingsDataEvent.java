package com.github.auties00.cobalt.wam.event;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;

import com.github.auties00.cobalt.wam.annotation.WamEvent;
import com.github.auties00.cobalt.wam.model.WamEventSpec;
import com.github.auties00.cobalt.wam.annotation.WamProperty;
import com.github.auties00.cobalt.wam.model.WamType;

import java.util.OptionalInt;

@WhatsAppWebModule(moduleName = "WAWebScreenLockSettingsDataWamEvent")
@WamEvent(id = 4802)
public interface ScreenLockSettingsDataEvent extends WamEventSpec {
    @WamProperty(index = 1, type = WamType.INTEGER)
    OptionalInt screenAutoLockDuration();
}
