package com.github.auties00.cobalt.wam.type;

import com.github.auties00.cobalt.wam.annotation.WamEnum;
import com.github.auties00.cobalt.wam.annotation.WamEnumConstant;

@WamEnum
public enum PttMessageUserJourneyStage {
    @WamEnumConstant(1) NORMAL,
    @WamEnumConstant(2) LOCKED
}
