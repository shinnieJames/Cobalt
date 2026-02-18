package com.github.auties00.cobalt.wam.type;

import com.github.auties00.cobalt.wam.annotation.WamEnum;
import com.github.auties00.cobalt.wam.annotation.WamEnumConstant;

@WamEnum
public enum EmailState {
    @WamEnumConstant(1) NOT_ADDED,
    @WamEnumConstant(2) UNVERIFIED,
    @WamEnumConstant(3) VERIFIED,
    @WamEnumConstant(4) UNCONFIRMED
}
