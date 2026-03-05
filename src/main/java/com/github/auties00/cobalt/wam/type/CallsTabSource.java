package com.github.auties00.cobalt.wam.type;

import com.github.auties00.cobalt.wam.annotation.WamEnum;
import com.github.auties00.cobalt.wam.annotation.WamEnumConstant;

@WamEnum
public enum CallsTabSource {
    @WamEnumConstant(1) NOTIFICATION,
    @WamEnumConstant(2) SWITCH,
    @WamEnumConstant(3) LAUNCH,
    @WamEnumConstant(4) NONE,
    @WamEnumConstant(5) OS_CALL_LOG
}
