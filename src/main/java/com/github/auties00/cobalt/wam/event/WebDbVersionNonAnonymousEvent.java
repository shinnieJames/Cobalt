package com.github.auties00.cobalt.wam.event;

import com.github.auties00.cobalt.wam.annotation.WamEvent;
import com.github.auties00.cobalt.wam.annotation.WamProperty;
import com.github.auties00.cobalt.wam.type.WamType;
import com.github.auties00.cobalt.wam.type.WebDbNameType;

import java.util.Optional;
import java.util.OptionalInt;

@WamEvent(id = 4816, releaseWeight = 20)
public interface WebDbVersionNonAnonymousEvent extends WamEventSpec {
    @WamProperty(index = 1, type = WamType.ENUM)
    Optional<WebDbNameType> webDbName();

    @WamProperty(index = 2, type = WamType.INTEGER)
    OptionalInt webDbVersionNumber();
}
