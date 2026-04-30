package com.github.auties00.cobalt.wam.event;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;

import com.github.auties00.cobalt.wam.annotation.WamEvent;
import com.github.auties00.cobalt.wam.model.WamEventSpec;
import com.github.auties00.cobalt.wam.annotation.WamProperty;
import com.github.auties00.cobalt.wam.model.WamType;

import java.util.OptionalInt;

@WhatsAppWebModule(moduleName = "WAWebMdAppStateSyncDailyWamEvent")
@WamEvent(id = 2300)
public interface MdAppStateSyncDailyEvent extends WamEventSpec {
    @WamProperty(index = 11, type = WamType.INTEGER)
    OptionalInt crossIndexConflictCount();

    @WamProperty(index = 4, type = WamType.INTEGER)
    OptionalInt invalidActionCount();

    @WamProperty(index = 12, type = WamType.INTEGER)
    OptionalInt keyRotationRemoveCount();

    @WamProperty(index = 9, type = WamType.INTEGER)
    OptionalInt missingKeyCount();

    @WamProperty(index = 1, type = WamType.INTEGER)
    OptionalInt mutationCount();

    @WamProperty(index = 7, type = WamType.INTEGER)
    OptionalInt storedMutationCount();

    @WamProperty(index = 8, type = WamType.INTEGER)
    OptionalInt unsetActionCount();

    @WamProperty(index = 5, type = WamType.INTEGER)
    OptionalInt unsupportedActionCount();

    @WamProperty(index = 10, type = WamType.INTEGER)
    OptionalInt uploadConflictCount();
}
