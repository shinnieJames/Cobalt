package com.github.auties00.cobalt.wam.type;

import com.github.auties00.cobalt.wam.annotation.WamEnum;
import com.github.auties00.cobalt.wam.annotation.WamEnumConstant;

@WamEnum
public enum MutationOperationType {
    @WamEnumConstant(0) SET,
    @WamEnumConstant(1) REMOVE
}
