package com.github.auties00.cobalt.wam.event;

import com.github.auties00.cobalt.wam.annotation.WamEvent;
import com.github.auties00.cobalt.wam.annotation.WamProperty;
import com.github.auties00.cobalt.wam.type.WamType;

import java.time.Instant;
import java.util.Optional;

@WamEvent(id = 1376)
public interface BusinessMuteEvent extends WamEventSpec {
    @WamProperty(index = 2, type = WamType.TIMER)
    Optional<Instant> muteT();

    @WamProperty(index = 1, type = WamType.STRING)
    Optional<String> muteeId();
}
