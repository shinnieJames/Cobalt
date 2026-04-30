package com.github.auties00.cobalt.wam.event;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;

import com.github.auties00.cobalt.wam.annotation.WamEvent;
import com.github.auties00.cobalt.wam.model.WamEventSpec;
import com.github.auties00.cobalt.wam.annotation.WamProperty;
import com.github.auties00.cobalt.wam.model.WamType;
import com.github.auties00.cobalt.wam.type.TsExternalEventSource;

import java.util.Optional;
import java.util.OptionalInt;

@WhatsAppWebModule(moduleName = "WAWebTsExternalWamEvent")
@WamEvent(id = 4574)
public interface TsExternalEvent extends WamEventSpec {
    @WamProperty(index = 1, type = WamType.INTEGER)
    OptionalInt relativeTimestampMs();

    @WamProperty(index = 2, type = WamType.INTEGER)
    OptionalInt tsDuration();

    @WamProperty(index = 3, type = WamType.ENUM)
    Optional<TsExternalEventSource> tsExternalEventSource();

    @WamProperty(index = 4, type = WamType.INTEGER)
    OptionalInt tsSessionId();

    @WamProperty(index = 5, type = WamType.INTEGER)
    OptionalInt tsTimestampMs();

    @WamProperty(index = 6, type = WamType.STRING)
    Optional<String> unifiedSessionId();
}
