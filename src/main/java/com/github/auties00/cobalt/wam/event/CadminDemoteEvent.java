package com.github.auties00.cobalt.wam.event;

import com.github.auties00.cobalt.wam.annotation.WamEvent;
import com.github.auties00.cobalt.wam.annotation.WamProperty;
import com.github.auties00.cobalt.wam.type.WamType;
import com.github.auties00.cobalt.wam.type.CadminDemoteOriginType;
import com.github.auties00.cobalt.wam.type.CadminDemoteResultType;

import java.util.Optional;

@WamEvent(id = 3426)
public interface CadminDemoteEvent extends WamEventSpec {
    @WamProperty(index = 1, type = WamType.ENUM)
    Optional<CadminDemoteOriginType> cadminDemoteOrigin();

    @WamProperty(index = 2, type = WamType.ENUM)
    Optional<CadminDemoteResultType> cadminDemoteResult();

    @WamProperty(index = 4, type = WamType.BOOLEAN)
    Optional<Boolean> isLastCadminOrCreator();
}
