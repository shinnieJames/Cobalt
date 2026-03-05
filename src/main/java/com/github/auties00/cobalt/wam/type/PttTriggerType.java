package com.github.auties00.cobalt.wam.type;

import com.github.auties00.cobalt.wam.annotation.WamEnum;
import com.github.auties00.cobalt.wam.annotation.WamEnumConstant;

@WamEnum
public enum PttTriggerType {
    @WamEnumConstant(0) MANUAL,
    @WamEnumConstant(1) SEQUENTIAL
}
