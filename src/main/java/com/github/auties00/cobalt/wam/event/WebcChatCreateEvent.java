package com.github.auties00.cobalt.wam.event;

import com.github.auties00.cobalt.wam.annotation.WamEvent;
import com.github.auties00.cobalt.wam.annotation.WamProperty;
import com.github.auties00.cobalt.wam.type.WamType;
import com.github.auties00.cobalt.wam.type.WebcChatCreateCreationMethod;

import java.util.Optional;
import java.util.OptionalInt;

@WamEvent(id = 6132)
public interface WebcChatCreateEvent extends WamEventSpec {
    @WamProperty(index = 1, type = WamType.ENUM)
    Optional<WebcChatCreateCreationMethod> creationMethod();

    @WamProperty(index = 2, type = WamType.INTEGER)
    OptionalInt noCreated();
}
