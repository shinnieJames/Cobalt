package com.github.auties00.cobalt.wam.event;

import com.github.auties00.cobalt.wam.annotation.WamEvent;
import com.github.auties00.cobalt.wam.annotation.WamProperty;
import com.github.auties00.cobalt.wam.type.WamType;

import java.util.Optional;
import java.util.OptionalInt;

@WamEvent(id = 1504)
public interface WebcStorageStatEvent extends WamEventSpec {
    @WamProperty(index = 3, type = WamType.INTEGER)
    OptionalInt webcAgeOfStorage();

    @WamProperty(index = 4, type = WamType.BOOLEAN)
    Optional<Boolean> webcPackingEnabled();

    @WamProperty(index = 2, type = WamType.INTEGER)
    OptionalInt webcStorageQuota();

    @WamProperty(index = 1, type = WamType.INTEGER)
    OptionalInt webcStorageUsage();
}
