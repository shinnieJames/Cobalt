package com.github.auties00.cobalt.wam.event;

import com.github.auties00.cobalt.wam.annotation.WamEvent;
import com.github.auties00.cobalt.wam.annotation.WamProperty;
import com.github.auties00.cobalt.wam.type.WamChannel;
import com.github.auties00.cobalt.wam.type.WamType;

import java.util.Optional;

@WamEvent(id = 6236, channel = WamChannel.PRIVATE, privateStatsId = 216763284)
public interface PsGroupOpenedEvent extends WamEventSpec {
    @WamProperty(index = 4, type = WamType.STRING)
    Optional<String> integrityGroupUserHashedId();

    @WamProperty(index = 2, type = WamType.BOOLEAN)
    Optional<Boolean> isGroupSafetyCheckAbpropEnabled();

    @WamProperty(index = 3, type = WamType.BOOLEAN)
    Optional<Boolean> isPartOfGroupSafetyCheckExperiment();

    @WamProperty(index = 1, type = WamType.STRING)
    Optional<String> openedGroupJid();
}
