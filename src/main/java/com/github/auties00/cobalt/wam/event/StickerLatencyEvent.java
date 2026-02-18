package com.github.auties00.cobalt.wam.event;

import com.github.auties00.cobalt.wam.annotation.WamEvent;
import com.github.auties00.cobalt.wam.annotation.WamProperty;
import com.github.auties00.cobalt.wam.type.WamType;
import com.github.auties00.cobalt.wam.type.StickerLatencyAction;

import java.util.Optional;
import java.util.OptionalInt;

@WamEvent(id = 5026)
public interface StickerLatencyEvent extends WamEventSpec {
    @WamProperty(index = 1, type = WamType.INTEGER)
    OptionalInt size();

    @WamProperty(index = 2, type = WamType.ENUM)
    Optional<StickerLatencyAction> stickerLatencyAction();

    @WamProperty(index = 3, type = WamType.INTEGER)
    OptionalInt stickerLatencyTtAction();
}
