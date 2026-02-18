package com.github.auties00.cobalt.wam.type;

import com.github.auties00.cobalt.wam.annotation.WamEnum;
import com.github.auties00.cobalt.wam.annotation.WamEnumConstant;

@WamEnum
public enum PlaceholderAction {
    @WamEnumConstant(0) OTHER,
    @WamEnumConstant(1) ADD,
    @WamEnumConstant(2) VIEW,
    @WamEnumConstant(3) POPULATE
}
