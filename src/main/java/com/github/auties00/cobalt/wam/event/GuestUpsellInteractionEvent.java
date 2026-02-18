package com.github.auties00.cobalt.wam.event;

import com.github.auties00.cobalt.wam.annotation.WamEvent;
import com.github.auties00.cobalt.wam.annotation.WamProperty;
import com.github.auties00.cobalt.wam.type.WamType;
import com.github.auties00.cobalt.wam.type.GuestUpsellActionType;
import com.github.auties00.cobalt.wam.type.GuestUpsellEntryPointType;

import java.util.Optional;

@WamEvent(id = 7146)
public interface GuestUpsellInteractionEvent extends WamEventSpec {
    @WamProperty(index = 1, type = WamType.ENUM)
    Optional<GuestUpsellActionType> guestUpsellAction();

    @WamProperty(index = 2, type = WamType.ENUM)
    Optional<GuestUpsellEntryPointType> guestUpsellEntryPoint();
}
