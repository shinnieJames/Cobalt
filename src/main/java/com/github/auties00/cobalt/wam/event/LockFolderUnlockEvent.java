package com.github.auties00.cobalt.wam.event;

import com.github.auties00.cobalt.wam.annotation.WamEvent;
import com.github.auties00.cobalt.wam.annotation.WamProperty;
import com.github.auties00.cobalt.wam.type.WamType;
import com.github.auties00.cobalt.wam.type.LandingSurface;
import com.github.auties00.cobalt.wam.type.UnlockEntryPoint;

import java.util.Optional;
import java.util.OptionalInt;

@WamEvent(id = 4218)
public interface LockFolderUnlockEvent extends WamEventSpec {
    @WamProperty(index = 1, type = WamType.ENUM)
    Optional<LandingSurface> landingSurface();

    @WamProperty(index = 2, type = WamType.INTEGER)
    OptionalInt totalChatCount();

    @WamProperty(index = 3, type = WamType.ENUM)
    Optional<UnlockEntryPoint> unlockEntryPoint();
}
