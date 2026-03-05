package com.github.auties00.cobalt.wam.event;

import com.github.auties00.cobalt.wam.annotation.WamEvent;
import com.github.auties00.cobalt.wam.annotation.WamProperty;
import com.github.auties00.cobalt.wam.type.WamType;
import com.github.auties00.cobalt.wam.type.AdminFlowType;
import com.github.auties00.cobalt.wam.type.ChannelAdminAction;

import java.util.Optional;
import java.util.OptionalInt;

@WamEvent(id = 4556)
public interface ChannelAdminEvent extends WamEventSpec {
    @WamProperty(index = 1, type = WamType.INTEGER)
    OptionalInt adminFlowActionSequenceNumber();

    @WamProperty(index = 2, type = WamType.ENUM)
    Optional<AdminFlowType> adminFlowType();

    @WamProperty(index = 3, type = WamType.ENUM)
    Optional<ChannelAdminAction> channelAdminAction();

    @WamProperty(index = 4, type = WamType.INTEGER)
    OptionalInt channelAdminSessionId();

    @WamProperty(index = 5, type = WamType.STRING)
    Optional<String> unifiedSessionId();

    @WamProperty(index = 6, type = WamType.INTEGER)
    OptionalInt updatesTabSessionId();
}
