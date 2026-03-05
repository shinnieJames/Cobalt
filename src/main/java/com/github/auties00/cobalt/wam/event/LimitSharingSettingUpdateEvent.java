package com.github.auties00.cobalt.wam.event;

import com.github.auties00.cobalt.wam.annotation.WamEvent;
import com.github.auties00.cobalt.wam.annotation.WamProperty;
import com.github.auties00.cobalt.wam.type.WamType;
import com.github.auties00.cobalt.wam.type.ToggleUpdateAction;

import java.util.Optional;

@WamEvent(id = 6390)
public interface LimitSharingSettingUpdateEvent extends WamEventSpec {
    @WamProperty(index = 1, type = WamType.STRING)
    Optional<String> threadId();

    @WamProperty(index = 2, type = WamType.ENUM)
    Optional<ToggleUpdateAction> toggleUpdateAction();
}
