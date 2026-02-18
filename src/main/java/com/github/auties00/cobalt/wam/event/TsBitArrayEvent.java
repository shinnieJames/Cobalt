package com.github.auties00.cobalt.wam.event;

import com.github.auties00.cobalt.wam.annotation.WamEvent;
import com.github.auties00.cobalt.wam.annotation.WamProperty;
import com.github.auties00.cobalt.wam.type.WamType;

import java.util.Optional;
import java.util.OptionalInt;

@WamEvent(id = 4332)
public interface TsBitArrayEvent extends WamEventSpec {
    @WamProperty(index = 1, type = WamType.INTEGER)
    OptionalInt bitarrayHigh();

    @WamProperty(index = 2, type = WamType.INTEGER)
    OptionalInt bitarrayLength();

    @WamProperty(index = 3, type = WamType.INTEGER)
    OptionalInt bitarrayLow();

    @WamProperty(index = 4, type = WamType.INTEGER)
    OptionalInt cumulativeBits();

    @WamProperty(index = 5, type = WamType.INTEGER)
    OptionalInt relativeTimestampMs();

    @WamProperty(index = 6, type = WamType.INTEGER)
    OptionalInt sessionSeq();

    @WamProperty(index = 7, type = WamType.INTEGER)
    OptionalInt tsSessionId();

    @WamProperty(index = 8, type = WamType.INTEGER)
    OptionalInt tsTimestampMs();

    @WamProperty(index = 9, type = WamType.STRING)
    Optional<String> unifiedSessionId();
}
