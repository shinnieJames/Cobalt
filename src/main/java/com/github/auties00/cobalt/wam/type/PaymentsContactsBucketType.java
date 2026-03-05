package com.github.auties00.cobalt.wam.type;

import com.github.auties00.cobalt.wam.annotation.WamEnum;
import com.github.auties00.cobalt.wam.annotation.WamEnumConstant;

@WamEnum
public enum PaymentsContactsBucketType {
    @WamEnumConstant(1) SMALL,
    @WamEnumConstant(2) MEDIUM,
    @WamEnumConstant(3) LARGE,
    @WamEnumConstant(4) EXTRA_LARGE
}
