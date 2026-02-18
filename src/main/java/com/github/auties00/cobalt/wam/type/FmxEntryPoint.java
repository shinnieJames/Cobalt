package com.github.auties00.cobalt.wam.type;

import com.github.auties00.cobalt.wam.annotation.WamEnum;
import com.github.auties00.cobalt.wam.annotation.WamEnumConstant;

@WamEnum
public enum FmxEntryPoint {
    @WamEnumConstant(0) FMX_CARD,
    @WamEnumConstant(1) SAFETY_TOOLS
}
