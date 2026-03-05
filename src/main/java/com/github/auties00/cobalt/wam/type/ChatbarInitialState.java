package com.github.auties00.cobalt.wam.type;

import com.github.auties00.cobalt.wam.annotation.WamEnum;
import com.github.auties00.cobalt.wam.annotation.WamEnumConstant;

@WamEnum
public enum ChatbarInitialState {
    @WamEnumConstant(1) EMPTY,
    @WamEnumConstant(2) CONTAINS_DRAFT
}
