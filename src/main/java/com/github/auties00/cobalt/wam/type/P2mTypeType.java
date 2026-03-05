package com.github.auties00.cobalt.wam.type;

import com.github.auties00.cobalt.wam.annotation.WamEnum;
import com.github.auties00.cobalt.wam.annotation.WamEnumConstant;

@WamEnum
public enum P2mTypeType {
    @WamEnumConstant(1) P2M_LITE,
    @WamEnumConstant(2) P2M_PRO,
    @WamEnumConstant(3) P2M_BASIC
}
