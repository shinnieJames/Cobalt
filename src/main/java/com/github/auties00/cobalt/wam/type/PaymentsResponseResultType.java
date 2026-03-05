package com.github.auties00.cobalt.wam.type;

import com.github.auties00.cobalt.wam.annotation.WamEnum;
import com.github.auties00.cobalt.wam.annotation.WamEnumConstant;

@WamEnum
public enum PaymentsResponseResultType {
    @WamEnumConstant(1) OK,
    @WamEnumConstant(2) ERROR
}
