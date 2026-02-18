package com.github.auties00.cobalt.wam.type;

import com.github.auties00.cobalt.wam.annotation.WamEnum;
import com.github.auties00.cobalt.wam.annotation.WamEnumConstant;

@WamEnum
public enum ChatGatedReason {
    @WamEnumConstant(1) TOS3,
    @WamEnumConstant(2) COUNTRY
}
