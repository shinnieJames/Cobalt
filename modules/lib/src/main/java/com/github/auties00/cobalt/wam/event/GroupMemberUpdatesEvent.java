package com.github.auties00.cobalt.wam.event;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;

import com.github.auties00.cobalt.wam.annotation.WamEvent;
import com.github.auties00.cobalt.wam.model.WamEventSpec;
import com.github.auties00.cobalt.wam.annotation.WamProperty;
import com.github.auties00.cobalt.wam.model.WamType;
import com.github.auties00.cobalt.wam.type.GroupMemberUpdatesActionName;
import com.github.auties00.cobalt.wam.type.GroupMemberUpdatesCurrentScreen;

import java.util.Optional;
import java.util.OptionalInt;

@WhatsAppWebModule(moduleName = "WAWebGroupMemberUpdatesWamEvent")
@WamEvent(id = 7768)
public interface GroupMemberUpdatesEvent extends WamEventSpec {
    @WamProperty(index = 1, type = WamType.INTEGER)
    OptionalInt fetchedMessageCount();

    @WamProperty(index = 5, type = WamType.INTEGER)
    OptionalInt fetchedMessageLatency();

    @WamProperty(index = 2, type = WamType.ENUM)
    Optional<GroupMemberUpdatesActionName> groupMemberUpdatesActionName();

    @WamProperty(index = 3, type = WamType.ENUM)
    Optional<GroupMemberUpdatesCurrentScreen> groupMemberUpdatesCurrentScreen();

    @WamProperty(index = 4, type = WamType.STRING)
    Optional<String> groupMemberUpdatesSessionId();
}
