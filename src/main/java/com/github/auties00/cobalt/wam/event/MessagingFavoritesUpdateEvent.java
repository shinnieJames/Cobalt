package com.github.auties00.cobalt.wam.event;

import com.github.auties00.cobalt.wam.annotation.WamEvent;
import com.github.auties00.cobalt.wam.annotation.WamProperty;
import com.github.auties00.cobalt.wam.type.WamType;
import com.github.auties00.cobalt.wam.type.FavoritesUpdateEntryPoint;

import java.util.Optional;
import java.util.OptionalInt;

@WamEvent(id = 5460)
public interface MessagingFavoritesUpdateEvent extends WamEventSpec {
    @WamProperty(index = 1, type = WamType.INTEGER)
    OptionalInt contactFavCountAfterUpdate();

    @WamProperty(index = 2, type = WamType.INTEGER)
    OptionalInt contactFavCountBeforeUpdate();

    @WamProperty(index = 3, type = WamType.ENUM)
    Optional<FavoritesUpdateEntryPoint> favoritesUpdateEntryPoint();

    @WamProperty(index = 4, type = WamType.INTEGER)
    OptionalInt groupFavCountAfterUpdate();

    @WamProperty(index = 5, type = WamType.INTEGER)
    OptionalInt groupFavCountBeforeUpdate();
}
