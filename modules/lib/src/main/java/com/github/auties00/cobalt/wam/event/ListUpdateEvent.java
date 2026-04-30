package com.github.auties00.cobalt.wam.event;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;

import com.github.auties00.cobalt.wam.annotation.WamEvent;
import com.github.auties00.cobalt.wam.model.WamEventSpec;
import com.github.auties00.cobalt.wam.annotation.WamProperty;
import com.github.auties00.cobalt.wam.model.WamType;
import com.github.auties00.cobalt.wam.type.ListAction;
import com.github.auties00.cobalt.wam.type.ListType;
import com.github.auties00.cobalt.wam.type.UpdateEntryPoint;

import java.util.Optional;
import java.util.OptionalInt;

@WhatsAppWebModule(moduleName = "WAWebListUpdateWamEvent")
@WamEvent(id = 5830)
public interface ListUpdateEvent extends WamEventSpec {
    @WamProperty(index = 9, type = WamType.INTEGER)
    OptionalInt groupsAdded();

    @WamProperty(index = 10, type = WamType.INTEGER)
    OptionalInt groupsAfterUpdate();

    @WamProperty(index = 1, type = WamType.ENUM)
    Optional<ListAction> listAction();

    @WamProperty(index = 2, type = WamType.INTEGER)
    OptionalInt listId();

    @WamProperty(index = 3, type = WamType.ENUM)
    Optional<ListType> listType();

    @WamProperty(index = 13, type = WamType.INTEGER)
    OptionalInt predefinedId();

    @WamProperty(index = 8, type = WamType.ENUM)
    Optional<UpdateEntryPoint> updateEntryPoint();

    @WamProperty(index = 11, type = WamType.INTEGER)
    OptionalInt usersAdded();

    @WamProperty(index = 12, type = WamType.INTEGER)
    OptionalInt usersAfterUpdate();
}
