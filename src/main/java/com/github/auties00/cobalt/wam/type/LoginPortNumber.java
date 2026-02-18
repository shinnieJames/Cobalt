package com.github.auties00.cobalt.wam.type;

import com.github.auties00.cobalt.wam.annotation.WamEnum;
import com.github.auties00.cobalt.wam.annotation.WamEnumConstant;

@WamEnum
public enum LoginPortNumber {
    @WamEnumConstant(1) P5222,
    @WamEnumConstant(2) P443,
    @WamEnumConstant(3) P80,
    @WamEnumConstant(4) UNKNOWN
}
