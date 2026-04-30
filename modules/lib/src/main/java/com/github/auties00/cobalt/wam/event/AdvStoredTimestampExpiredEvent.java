package com.github.auties00.cobalt.wam.event;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;

import com.github.auties00.cobalt.wam.annotation.WamEvent;
import com.github.auties00.cobalt.wam.model.WamEventSpec;
import com.github.auties00.cobalt.wam.annotation.WamProperty;
import com.github.auties00.cobalt.wam.model.WamType;

import java.util.OptionalInt;

@WhatsAppWebModule(moduleName = "WAWebAdvStoredTimestampExpiredWamEvent")
@WamEvent(id = 3036)
public interface AdvStoredTimestampExpiredEvent extends WamEventSpec {
    @WamProperty(index = 1, type = WamType.INTEGER)
    OptionalInt advExpireTimeInHours();
}
