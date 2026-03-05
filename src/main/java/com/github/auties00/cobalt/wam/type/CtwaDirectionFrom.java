package com.github.auties00.cobalt.wam.type;

import com.github.auties00.cobalt.wam.annotation.WamEnum;
import com.github.auties00.cobalt.wam.annotation.WamEnumConstant;

@WamEnum
public enum CtwaDirectionFrom {
    @WamEnumConstant(0) CUSTOMER,
    @WamEnumConstant(1) BUSINESS
}
