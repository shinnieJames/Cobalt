package com.github.auties00.cobalt.wam.type;

import com.github.auties00.cobalt.wam.annotation.WamEnum;
import com.github.auties00.cobalt.wam.annotation.WamEnumConstant;

@WamEnum
public enum BlockEventActionType {
    @WamEnumConstant(0) BLOCK,
    @WamEnumConstant(1) UNBLOCK
}
