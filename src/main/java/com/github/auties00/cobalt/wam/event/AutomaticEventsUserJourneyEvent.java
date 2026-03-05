package com.github.auties00.cobalt.wam.event;

import com.github.auties00.cobalt.wam.annotation.WamEvent;
import com.github.auties00.cobalt.wam.annotation.WamProperty;
import com.github.auties00.cobalt.wam.type.WamType;
import com.github.auties00.cobalt.wam.type.AutomaticEventsTargetComponentEnum;
import com.github.auties00.cobalt.wam.type.SmbUserActionTypeEnum;
import com.github.auties00.cobalt.wam.type.SurfaceType;

import java.util.Optional;

@WamEvent(id = 6636)
public interface AutomaticEventsUserJourneyEvent extends WamEventSpec {
    @WamProperty(index = 1, type = WamType.ENUM)
    Optional<AutomaticEventsTargetComponentEnum> automaticEventsTargetComponent();

    @WamProperty(index = 4, type = WamType.STRING)
    Optional<String> extraAttributes();

    @WamProperty(index = 2, type = WamType.ENUM)
    Optional<SmbUserActionTypeEnum> smbUserActionType();

    @WamProperty(index = 3, type = WamType.ENUM)
    Optional<SurfaceType> surface();
}
