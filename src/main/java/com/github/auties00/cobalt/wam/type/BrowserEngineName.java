package com.github.auties00.cobalt.wam.type;

import com.github.auties00.cobalt.wam.annotation.WamEnum;
import com.github.auties00.cobalt.wam.annotation.WamEnumConstant;

@WamEnum
public enum BrowserEngineName {
    @WamEnumConstant(0) BLINK,
    @WamEnumConstant(1) GECKO,
    @WamEnumConstant(2) WEBKIT,
    @WamEnumConstant(3) UNKNOWN
}
