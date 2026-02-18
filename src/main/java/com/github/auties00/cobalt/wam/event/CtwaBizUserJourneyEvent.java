package com.github.auties00.cobalt.wam.event;

import com.github.auties00.cobalt.wam.annotation.WamEvent;
import com.github.auties00.cobalt.wam.annotation.WamProperty;
import com.github.auties00.cobalt.wam.type.WamType;
import com.github.auties00.cobalt.wam.type.CtwaBizUserJourneyOperation;

import java.util.Optional;

@WamEvent(id = 5992)
public interface CtwaBizUserJourneyEvent extends WamEventSpec {
    @WamProperty(index = 3, type = WamType.STRING)
    Optional<String> adId();

    @WamProperty(index = 4, type = WamType.STRING)
    Optional<String> bizFeatureEnabled();

    @WamProperty(index = 7, type = WamType.STRING)
    Optional<String> ctwaBizEventReason();

    @WamProperty(index = 6, type = WamType.STRING)
    Optional<String> ctwaBizUserJouneryEntryPoint();

    @WamProperty(index = 1, type = WamType.STRING)
    Optional<String> ctwaBizUserJourneyMetadata();

    @WamProperty(index = 2, type = WamType.ENUM)
    Optional<CtwaBizUserJourneyOperation> ctwaBizUserJourneyOperation();
}
