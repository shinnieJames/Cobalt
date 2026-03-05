package com.github.auties00.cobalt.wam.type;

import com.github.auties00.cobalt.wam.annotation.WamEnum;
import com.github.auties00.cobalt.wam.annotation.WamEnumConstant;

@WamEnum
public enum DyiTriggerTypeCode {
    @WamEnumConstant(1) ADHOC,
    @WamEnumConstant(2) SCHEDULED
}
