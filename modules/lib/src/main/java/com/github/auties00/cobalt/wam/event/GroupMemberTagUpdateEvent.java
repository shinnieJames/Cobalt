package com.github.auties00.cobalt.wam.event;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;

import com.github.auties00.cobalt.wam.annotation.WamEvent;
import com.github.auties00.cobalt.wam.model.WamEventSpec;
import com.github.auties00.cobalt.wam.annotation.WamProperty;
import com.github.auties00.cobalt.wam.model.WamType;
import com.github.auties00.cobalt.wam.type.GroupMemberTagEntryPointType;
import com.github.auties00.cobalt.wam.type.GroupMemberTagUpdateActionType;
import com.github.auties00.cobalt.wam.type.TsSurface;

import java.util.Optional;
import java.util.OptionalInt;

@WhatsAppWebModule(moduleName = "WAWebGroupMemberTagUpdateWamEvent")
@WamEvent(id = 7010)
public interface GroupMemberTagUpdateEvent extends WamEventSpec {
    @WamProperty(index = 1, type = WamType.STRING)
    Optional<String> groupId();

    @WamProperty(index = 2, type = WamType.ENUM)
    Optional<GroupMemberTagUpdateActionType> groupMemberTagUpdateAction();

    @WamProperty(index = 3, type = WamType.BOOLEAN)
    Optional<Boolean> hasMemberTagAtStart();

    @WamProperty(index = 4, type = WamType.ENUM)
    Optional<GroupMemberTagEntryPointType> memberTagEntryPoint();

    @WamProperty(index = 5, type = WamType.ENUM)
    Optional<TsSurface> uiSurface();

    @WamProperty(index = 6, type = WamType.STRING)
    Optional<String> unifiedSessionId();

    @WamProperty(index = 7, type = WamType.INTEGER)
    OptionalInt userJourneyEventMs();
}
