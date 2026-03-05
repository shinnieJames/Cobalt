package com.github.auties00.cobalt.wam.type;

import com.github.auties00.cobalt.wam.annotation.WamEnum;
import com.github.auties00.cobalt.wam.annotation.WamEnumConstant;

@WamEnum
public enum ToggleUpdateAction {
    @WamEnumConstant(0) TURN_ON,
    @WamEnumConstant(1) TURN_OFF
}
