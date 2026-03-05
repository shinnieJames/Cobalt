package com.github.auties00.cobalt.wam.event;

import com.github.auties00.cobalt.wam.annotation.WamEvent;
import com.github.auties00.cobalt.wam.annotation.WamProperty;
import com.github.auties00.cobalt.wam.type.WamType;
import com.github.auties00.cobalt.wam.type.LwiEntryPoint;
import com.github.auties00.cobalt.wam.type.LwiSubEntryPoint;
import com.github.auties00.cobalt.wam.type.StatusTypeMedia;

import java.util.Optional;
import java.util.OptionalInt;

@WamEvent(id = 2770)
public interface LwiEntryTapEvent extends WamEventSpec {
    @WamProperty(index = 4, type = WamType.STRING)
    Optional<String> businessToolsSessionId();

    @WamProperty(index = 2, type = WamType.STRING)
    Optional<String> catalogSessionId();

    @WamProperty(index = 11, type = WamType.INTEGER)
    OptionalInt itemsCount();

    @WamProperty(index = 3, type = WamType.ENUM)
    Optional<LwiEntryPoint> lwiEntryPoint();

    @WamProperty(index = 1, type = WamType.STRING)
    Optional<String> lwiFlowId();

    @WamProperty(index = 7, type = WamType.ENUM)
    Optional<LwiSubEntryPoint> lwiSubEntryPoint();

    @WamProperty(index = 13, type = WamType.STRING)
    Optional<String> previousLwiFlowId();

    @WamProperty(index = 6, type = WamType.INTEGER)
    OptionalInt statusSessionId();

    @WamProperty(index = 12, type = WamType.ENUM)
    Optional<StatusTypeMedia> statusTypeMedia();

    @WamProperty(index = 5, type = WamType.BOOLEAN)
    Optional<Boolean> userHasLinkedFbPage();

    @WamProperty(index = 10, type = WamType.STRING)
    Optional<String> waCampaignId();
}
