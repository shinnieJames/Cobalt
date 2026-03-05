package com.github.auties00.cobalt.wam.type;

import com.github.auties00.cobalt.wam.annotation.WamEnum;
import com.github.auties00.cobalt.wam.annotation.WamEnumConstant;

@WamEnum
public enum BusinessInteractionInternalEntryPoint {
    @WamEnumConstant(1) STATUS,
    @WamEnumConstant(2) INDIVIDUAL_CHAT,
    @WamEnumConstant(3) GROUP,
    @WamEnumConstant(4) OTHER,
    @WamEnumConstant(5) OUTSIDE_OF_WA,
    @WamEnumConstant(6) CHANNEL
}
