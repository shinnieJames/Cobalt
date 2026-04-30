package com.github.auties00.cobalt.wam.event;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;

import com.github.auties00.cobalt.wam.annotation.WamEvent;
import com.github.auties00.cobalt.wam.model.WamEventSpec;
import com.github.auties00.cobalt.wam.annotation.WamProperty;
import com.github.auties00.cobalt.wam.model.WamType;

import java.util.Optional;
import java.util.OptionalInt;

@WhatsAppWebModule(moduleName = "WAWebUserActivityWamEvent")
@WamEvent(id = 1384)
public interface UserActivityEvent extends WamEventSpec {
    @WamProperty(index = 4, type = WamType.INTEGER)
    OptionalInt userActivityBitmapHigh();

    @WamProperty(index = 5, type = WamType.INTEGER)
    OptionalInt userActivityBitmapLen();

    @WamProperty(index = 3, type = WamType.INTEGER)
    OptionalInt userActivityBitmapLow();

    @WamProperty(index = 7, type = WamType.INTEGER)
    OptionalInt userActivitySessionCum();

    @WamProperty(index = 1, type = WamType.STRING)
    Optional<String> userActivitySessionId();

    @WamProperty(index = 6, type = WamType.INTEGER)
    OptionalInt userActivitySessionSeq();

    @WamProperty(index = 2, type = WamType.INTEGER)
    OptionalInt userActivityStartTime();
}
