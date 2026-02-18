package com.github.auties00.cobalt.wam.type;

import com.github.auties00.cobalt.wam.annotation.WamEnum;
import com.github.auties00.cobalt.wam.annotation.WamEnumConstant;

@WamEnum
public enum ActiveTimeAfterPairing {
    @WamEnumConstant(1) MINS_10,
    @WamEnumConstant(2) MINS_20,
    @WamEnumConstant(3) MINS_40,
    @WamEnumConstant(4) MINS_60,
    @WamEnumConstant(5) MINS_5
}
