package com.github.auties00.cobalt.wam.event;

import com.github.auties00.cobalt.wam.annotation.WamEvent;
import com.github.auties00.cobalt.wam.annotation.WamProperty;
import com.github.auties00.cobalt.wam.type.WamType;
import com.github.auties00.cobalt.wam.type.WebcMediaLoadResultCode;

import java.time.Instant;
import java.util.Optional;

@WamEvent(id = 1202)
public interface WebcMediaLoadEvent extends WamEventSpec {
    @WamProperty(index = 2, type = WamType.ENUM)
    Optional<WebcMediaLoadResultCode> webcMediaLoadResult();

    @WamProperty(index = 1, type = WamType.TIMER)
    Optional<Instant> webcMediaLoadT();
}
