package com.github.auties00.cobalt.wam.event;

import com.github.auties00.cobalt.wam.annotation.WamEvent;
import com.github.auties00.cobalt.wam.annotation.WamProperty;
import com.github.auties00.cobalt.wam.type.WamType;

import java.util.Optional;
import java.util.OptionalInt;

@WamEvent(id = 3448)
public interface UnknownStanzaEvent extends WamEventSpec {
    @WamProperty(index = 3, type = WamType.INTEGER)
    OptionalInt unknownStanzaDropReason();

    @WamProperty(index = 1, type = WamType.STRING)
    Optional<String> unknownStanzaTag();

    @WamProperty(index = 2, type = WamType.STRING)
    Optional<String> unknownStanzaType();
}
