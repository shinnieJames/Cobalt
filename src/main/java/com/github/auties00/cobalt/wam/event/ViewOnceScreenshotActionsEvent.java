package com.github.auties00.cobalt.wam.event;

import com.github.auties00.cobalt.wam.annotation.WamEvent;
import com.github.auties00.cobalt.wam.annotation.WamProperty;
import com.github.auties00.cobalt.wam.type.WamType;
import com.github.auties00.cobalt.wam.type.VoMessageType;
import com.github.auties00.cobalt.wam.type.VoSsAction;

import java.util.Optional;

@WamEvent(id = 3606)
public interface ViewOnceScreenshotActionsEvent extends WamEventSpec {
    @WamProperty(index = 1, type = WamType.BOOLEAN)
    Optional<Boolean> isAGroup();

    @WamProperty(index = 2, type = WamType.STRING)
    Optional<String> threadId();

    @WamProperty(index = 3, type = WamType.ENUM)
    Optional<VoMessageType> voMessageType();

    @WamProperty(index = 4, type = WamType.ENUM)
    Optional<VoSsAction> voSsAction();
}
