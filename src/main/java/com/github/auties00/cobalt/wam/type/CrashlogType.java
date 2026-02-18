package com.github.auties00.cobalt.wam.type;

import com.github.auties00.cobalt.wam.annotation.WamEnum;
import com.github.auties00.cobalt.wam.annotation.WamEnumConstant;

@WamEnum
public enum CrashlogType {
    @WamEnumConstant(0) UNKNOWN,
    @WamEnumConstant(1) ZERO_EVENT_EXPECTED,
    @WamEnumConstant(2) TRACKING
}
