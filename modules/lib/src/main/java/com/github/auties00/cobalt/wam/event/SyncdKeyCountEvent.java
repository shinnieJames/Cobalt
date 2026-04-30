package com.github.auties00.cobalt.wam.event;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;

import com.github.auties00.cobalt.wam.annotation.WamEvent;
import com.github.auties00.cobalt.wam.model.WamEventSpec;
import com.github.auties00.cobalt.wam.annotation.WamProperty;
import com.github.auties00.cobalt.wam.model.WamType;

import java.util.OptionalInt;

@WhatsAppWebModule(moduleName = "WAWebSyncdKeyCountWamEvent")
@WamEvent(id = 3978)
public interface SyncdKeyCountEvent extends WamEventSpec {
    @WamProperty(index = 1, type = WamType.INTEGER)
    OptionalInt keysUsedInSnapshotCount();

    @WamProperty(index = 2, type = WamType.INTEGER)
    OptionalInt p80MuationsPerKey();

    @WamProperty(index = 3, type = WamType.INTEGER)
    OptionalInt p95MuationsPerKey();

    @WamProperty(index = 4, type = WamType.INTEGER)
    OptionalInt syncdSessionLengthDays();

    @WamProperty(index = 5, type = WamType.INTEGER)
    OptionalInt totalKeyCount();
}
