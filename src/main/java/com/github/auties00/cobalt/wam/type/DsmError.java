package com.github.auties00.cobalt.wam.type;

import com.github.auties00.cobalt.wam.annotation.WamEnum;
import com.github.auties00.cobalt.wam.annotation.WamEnumConstant;

@WamEnum
public enum DsmError {
    @WamEnumConstant(1) INVALID_SENDER,
    @WamEnumConstant(2) MISSING_DSM,
    @WamEnumConstant(3) INVALID_DSM
}
