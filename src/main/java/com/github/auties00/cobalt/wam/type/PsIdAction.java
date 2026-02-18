package com.github.auties00.cobalt.wam.type;

import com.github.auties00.cobalt.wam.annotation.WamEnum;
import com.github.auties00.cobalt.wam.annotation.WamEnumConstant;

@WamEnum
public enum PsIdAction {
    @WamEnumConstant(1) CREATED,
    @WamEnumConstant(2) ROTATED,
    @WamEnumConstant(3) DELETED
}
