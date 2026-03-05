package com.github.auties00.cobalt.wam.type;

import com.github.auties00.cobalt.wam.annotation.WamEnum;
import com.github.auties00.cobalt.wam.annotation.WamEnumConstant;

@WamEnum
public enum CallSizeType {
    @WamEnumConstant(1) ONE_TO_ONE,
    @WamEnumConstant(2) ADHOC,
    @WamEnumConstant(3) LGC
}
