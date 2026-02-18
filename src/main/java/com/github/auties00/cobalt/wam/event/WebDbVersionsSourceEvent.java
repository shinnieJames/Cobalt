package com.github.auties00.cobalt.wam.event;

import com.github.auties00.cobalt.wam.annotation.WamEvent;
import com.github.auties00.cobalt.wam.annotation.WamProperty;
import com.github.auties00.cobalt.wam.type.WamType;
import com.github.auties00.cobalt.wam.type.WebDbVersionSourceType;
import com.github.auties00.cobalt.wam.type.WebSchemaInitiatorType;

import java.util.Optional;

@WamEvent(id = 4784)
public interface WebDbVersionsSourceEvent extends WamEventSpec {
    @WamProperty(index = 1, type = WamType.ENUM)
    Optional<WebDbVersionSourceType> webDbVersionSource();

    @WamProperty(index = 2, type = WamType.ENUM)
    Optional<WebSchemaInitiatorType> webSchemaInitiator();
}
