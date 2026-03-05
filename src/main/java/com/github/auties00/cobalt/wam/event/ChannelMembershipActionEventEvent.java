package com.github.auties00.cobalt.wam.event;

import com.github.auties00.cobalt.wam.annotation.WamEvent;
import com.github.auties00.cobalt.wam.annotation.WamProperty;
import com.github.auties00.cobalt.wam.type.WamType;
import com.github.auties00.cobalt.wam.type.ChannelMembershipActionResult;

import java.util.Optional;
import java.util.OptionalInt;

@WamEvent(id = 5762)
public interface ChannelMembershipActionEventEvent extends WamEventSpec {
    @WamProperty(index = 1, type = WamType.ENUM)
    Optional<ChannelMembershipActionResult> actionResult();

    @WamProperty(index = 2, type = WamType.STRING)
    Optional<String> cid();

    @WamProperty(index = 3, type = WamType.STRING)
    Optional<String> unifiedSessionId();

    @WamProperty(index = 4, type = WamType.INTEGER)
    OptionalInt updatesTabSessionId();
}
