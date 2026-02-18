package com.github.auties00.cobalt.wam.event;

import com.github.auties00.cobalt.wam.annotation.WamEvent;
import com.github.auties00.cobalt.wam.annotation.WamProperty;
import com.github.auties00.cobalt.wam.type.WamChannel;
import com.github.auties00.cobalt.wam.type.WamType;
import com.github.auties00.cobalt.wam.type.MmSignalType;

import java.util.Optional;

@WamEvent(id = 5572, channel = WamChannel.PRIVATE, privateStatsId = 0)
public interface MmSignalEvent extends WamEventSpec {
    @WamProperty(index = 4, type = WamType.BOOLEAN)
    Optional<Boolean> disclosed();

    @WamProperty(index = 1, type = WamType.STRING)
    Optional<String> mmSignalData();

    @WamProperty(index = 2, type = WamType.ENUM)
    Optional<MmSignalType> mmSignalType();
}
