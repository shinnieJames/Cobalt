package com.github.auties00.cobalt.wam.event;

import com.github.auties00.cobalt.wam.annotation.WamEvent;
import com.github.auties00.cobalt.wam.annotation.WamProperty;
import com.github.auties00.cobalt.wam.type.WamChannel;
import com.github.auties00.cobalt.wam.type.WamType;

import java.util.Optional;

@WamEvent(id = 6240, channel = WamChannel.PRIVATE, privateStatsId = 216763284)
public interface PsGroupSafetyCheckSheetSeenEvent extends WamEventSpec {
    @WamProperty(index = 2, type = WamType.STRING)
    Optional<String> integrityGroupUserHashedId();

    @WamProperty(index = 1, type = WamType.STRING)
    Optional<String> psSafetyCheckGroupJid();
}
