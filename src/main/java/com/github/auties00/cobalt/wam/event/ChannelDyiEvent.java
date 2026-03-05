package com.github.auties00.cobalt.wam.event;

import com.github.auties00.cobalt.wam.annotation.WamEvent;
import com.github.auties00.cobalt.wam.annotation.WamProperty;
import com.github.auties00.cobalt.wam.type.WamType;
import com.github.auties00.cobalt.wam.type.ChannelDyiEventType;

import java.util.Optional;
import java.util.OptionalInt;

@WamEvent(id = 4726)
public interface ChannelDyiEvent extends WamEventSpec {
    @WamProperty(index = 1, type = WamType.ENUM)
    Optional<ChannelDyiEventType> channelDyiEventType();

    @WamProperty(index = 2, type = WamType.STRING)
    Optional<String> unifiedSessionId();

    @WamProperty(index = 3, type = WamType.INTEGER)
    OptionalInt updatesTabSessionId();
}
