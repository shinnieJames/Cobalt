package com.github.auties00.cobalt.wam.event;

import com.github.auties00.cobalt.wam.annotation.WamEvent;
import com.github.auties00.cobalt.wam.annotation.WamProperty;
import com.github.auties00.cobalt.wam.type.WamChannel;
import com.github.auties00.cobalt.wam.type.WamType;
import com.github.auties00.cobalt.wam.type.DisclosureAction;
import com.github.auties00.cobalt.wam.type.DisclosureContextType;
import com.github.auties00.cobalt.wam.type.DisclosureEntryPointType;
import com.github.auties00.cobalt.wam.type.DisclosureType;

import java.util.Optional;
import java.util.OptionalInt;

@WamEvent(id = 4406, channel = WamChannel.PRIVATE, privateStatsId = 0)
public interface CtwaConsumerDisclosureEvent extends WamEventSpec {
    @WamProperty(index = 3, type = WamType.INTEGER)
    OptionalInt ctwaConsumerDisclosureVersion();

    @WamProperty(index = 1, type = WamType.ENUM)
    Optional<DisclosureAction> disclosureAction();

    @WamProperty(index = 5, type = WamType.ENUM)
    Optional<DisclosureContextType> disclosureContext();

    @WamProperty(index = 4, type = WamType.ENUM)
    Optional<DisclosureEntryPointType> disclosureEntryPoint();

    @WamProperty(index = 2, type = WamType.ENUM)
    Optional<DisclosureType> disclosureType();
}
