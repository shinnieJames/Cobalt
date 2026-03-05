package com.github.auties00.cobalt.wam.type;

import com.github.auties00.cobalt.wam.annotation.WamEnum;
import com.github.auties00.cobalt.wam.annotation.WamEnumConstant;

@WamEnum
public enum AdminFlowType {
    @WamEnumConstant(1) CREATION,
    @WamEnumConstant(2) EDIT
}
