package com.github.auties00.cobalt.wam.event;

import com.github.auties00.cobalt.wam.annotation.WamEvent;
import com.github.auties00.cobalt.wam.annotation.WamProperty;
import com.github.auties00.cobalt.wam.type.WamType;
import com.github.auties00.cobalt.wam.type.ListAction;
import com.github.auties00.cobalt.wam.type.ListType;
import com.github.auties00.cobalt.wam.type.ListUpdateUserJourneyAction;
import com.github.auties00.cobalt.wam.type.UpdateEntryPoint;

import java.util.Optional;
import java.util.OptionalInt;

@WamEvent(id = 5958)
public interface ListUpdateUserJourneyEvent extends WamEventSpec {
    @WamProperty(index = 1, type = WamType.ENUM)
    Optional<ListAction> listAction();

    @WamProperty(index = 2, type = WamType.INTEGER)
    OptionalInt listId();

    @WamProperty(index = 3, type = WamType.ENUM)
    Optional<ListType> listType();

    @WamProperty(index = 4, type = WamType.ENUM)
    Optional<ListUpdateUserJourneyAction> listUpdateUserJourneyAction();

    @WamProperty(index = 5, type = WamType.INTEGER)
    OptionalInt predefinedId();

    @WamProperty(index = 6, type = WamType.ENUM)
    Optional<UpdateEntryPoint> updateEntryPoint();
}
