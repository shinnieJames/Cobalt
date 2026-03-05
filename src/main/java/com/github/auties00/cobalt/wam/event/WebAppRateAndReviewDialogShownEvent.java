package com.github.auties00.cobalt.wam.event;

import com.github.auties00.cobalt.wam.annotation.WamEvent;
import com.github.auties00.cobalt.wam.annotation.WamProperty;
import com.github.auties00.cobalt.wam.type.WamType;
import com.github.auties00.cobalt.wam.type.CloseTypeEnum;
import com.github.auties00.cobalt.wam.type.SourceType;

import java.util.Optional;

@WamEvent(id = 6788)
public interface WebAppRateAndReviewDialogShownEvent extends WamEventSpec {
    @WamProperty(index = 1, type = WamType.ENUM)
    Optional<CloseTypeEnum> reviewDialogAction();

    @WamProperty(index = 2, type = WamType.ENUM)
    Optional<SourceType> reviewSource();
}
