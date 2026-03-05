package com.github.auties00.cobalt.wam.event;

import com.github.auties00.cobalt.wam.annotation.WamEvent;
import com.github.auties00.cobalt.wam.annotation.WamProperty;
import com.github.auties00.cobalt.wam.type.WamType;
import com.github.auties00.cobalt.wam.type.GroupRoleType;
import com.github.auties00.cobalt.wam.type.GroupTypeClient;
import com.github.auties00.cobalt.wam.type.MediaType;
import com.github.auties00.cobalt.wam.type.PinInChatInteractionType;

import java.util.Optional;
import java.util.OptionalInt;

@WamEvent(id = 4436)
public interface PinInChatInteractionEvent extends WamEventSpec {
    @WamProperty(index = 1, type = WamType.ENUM)
    Optional<GroupRoleType> groupRole();

    @WamProperty(index = 2, type = WamType.INTEGER)
    OptionalInt groupSize();

    @WamProperty(index = 3, type = WamType.ENUM)
    Optional<GroupTypeClient> groupTypeClient();

    @WamProperty(index = 4, type = WamType.BOOLEAN)
    Optional<Boolean> isAGroup();

    @WamProperty(index = 8, type = WamType.BOOLEAN)
    Optional<Boolean> isSelfPin();

    @WamProperty(index = 5, type = WamType.ENUM)
    Optional<MediaType> mediaType();

    @WamProperty(index = 6, type = WamType.INTEGER)
    OptionalInt pinCount();

    @WamProperty(index = 7, type = WamType.ENUM)
    Optional<PinInChatInteractionType> pinInChatInteractionType();

    @WamProperty(index = 9, type = WamType.INTEGER)
    OptionalInt pinIndex();
}
