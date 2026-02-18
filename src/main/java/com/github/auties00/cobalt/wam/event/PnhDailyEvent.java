package com.github.auties00.cobalt.wam.event;

import com.github.auties00.cobalt.wam.annotation.WamEvent;
import com.github.auties00.cobalt.wam.annotation.WamProperty;
import com.github.auties00.cobalt.wam.type.WamType;
import com.github.auties00.cobalt.wam.type.TypeOfGroupEnum;

import java.util.Optional;
import java.util.OptionalInt;

@WamEvent(id = 3806)
public interface PnhDailyEvent extends WamEventSpec {
    @WamProperty(index = 1, type = WamType.STRING)
    Optional<String> communityId();

    @WamProperty(index = 7, type = WamType.INTEGER)
    OptionalInt mappingMissing();

    @WamProperty(index = 2, type = WamType.INTEGER)
    OptionalInt pnhIndicatorClicksChat();

    @WamProperty(index = 3, type = WamType.INTEGER)
    OptionalInt pnhIndicatorClicksInfoScreen();

    @WamProperty(index = 4, type = WamType.INTEGER)
    OptionalInt reactionDeleteCount();

    @WamProperty(index = 5, type = WamType.INTEGER)
    OptionalInt reactionOpenTrayCount();

    @WamProperty(index = 8, type = WamType.INTEGER)
    OptionalInt totalContacts();

    @WamProperty(index = 6, type = WamType.ENUM)
    Optional<TypeOfGroupEnum> typeOfGroup();
}
