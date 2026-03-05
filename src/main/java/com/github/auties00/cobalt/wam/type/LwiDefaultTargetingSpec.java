package com.github.auties00.cobalt.wam.type;

import com.github.auties00.cobalt.wam.annotation.WamEnum;
import com.github.auties00.cobalt.wam.annotation.WamEnumConstant;

@WamEnum
public enum LwiDefaultTargetingSpec {
    @WamEnumConstant(1) UNKOWN,
    @WamEnumConstant(2) MATCHES_TARGETING_SPEC,
    @WamEnumConstant(3) DIFFERS_FROM_TARGETING_SPEC
}
