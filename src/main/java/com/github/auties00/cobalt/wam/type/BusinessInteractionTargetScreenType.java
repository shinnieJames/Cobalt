package com.github.auties00.cobalt.wam.type;

import com.github.auties00.cobalt.wam.annotation.WamEnum;
import com.github.auties00.cobalt.wam.annotation.WamEnumConstant;

@WamEnum
public enum BusinessInteractionTargetScreenType {
    @WamEnumConstant(1) INDIVIDUAL_CHAT,
    @WamEnumConstant(2) LANDING_PAGE,
    @WamEnumConstant(3) OTHER
}
