package com.github.auties00.cobalt.wam.type;

import com.github.auties00.cobalt.wam.annotation.WamEnum;
import com.github.auties00.cobalt.wam.annotation.WamEnumConstant;

@WamEnum
public enum WebTableLogReasonCode {
    @WamEnumConstant(0) BASE,
    @WamEnumConstant(1) EXCEEDED_THRESHOLD
}
