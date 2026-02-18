package com.github.auties00.cobalt.wam.type;

import com.github.auties00.cobalt.wam.annotation.WamEnum;
import com.github.auties00.cobalt.wam.annotation.WamEnumConstant;

@WamEnum
public enum RetryRejectReason {
    @WamEnumConstant(0) OTHER,
    @WamEnumConstant(1) DOUBLE_CHECKMARK,
    @WamEnumConstant(2) IDENTITY_CHANGE,
    @WamEnumConstant(3) MESSAGE_NOT_EXIST,
    @WamEnumConstant(4) HIGH_RETRY_COUNT
}
