package com.github.auties00.cobalt.wam.event;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;

import com.github.auties00.cobalt.wam.annotation.WamEvent;
import com.github.auties00.cobalt.wam.model.WamEventSpec;
import com.github.auties00.cobalt.wam.annotation.WamProperty;
import com.github.auties00.cobalt.wam.model.WamType;
import com.github.auties00.cobalt.wam.type.SearchActionEntryPointType;
import com.github.auties00.cobalt.wam.type.SearchActionType;

import java.util.Optional;
import java.util.OptionalInt;

@WhatsAppWebModule(moduleName = "WAWebSearchActionEventWamEvent")
@WamEvent(id = 5308)
public interface SearchActionEventEvent extends WamEventSpec {
    @WamProperty(index = 1, type = WamType.INTEGER)
    OptionalInt bizSearchCount();

    @WamProperty(index = 11, type = WamType.BOOLEAN)
    Optional<Boolean> resultPageShown();

    @WamProperty(index = 2, type = WamType.ENUM)
    Optional<SearchActionType> searchAction();

    @WamProperty(index = 3, type = WamType.ENUM)
    Optional<SearchActionEntryPointType> searchActionEntryPoint();

    @WamProperty(index = 4, type = WamType.INTEGER)
    OptionalInt searchAiSuggestionCount();

    @WamProperty(index = 5, type = WamType.INTEGER)
    OptionalInt searchChatsCount();

    @WamProperty(index = 6, type = WamType.INTEGER)
    OptionalInt searchContactsCount();

    @WamProperty(index = 7, type = WamType.INTEGER)
    OptionalInt searchFilterCount();

    @WamProperty(index = 8, type = WamType.INTEGER)
    OptionalInt searchGroupsCount();

    @WamProperty(index = 9, type = WamType.INTEGER)
    OptionalInt searchMessagesCount();

    @WamProperty(index = 10, type = WamType.INTEGER)
    OptionalInt selectedItemRank();
}
