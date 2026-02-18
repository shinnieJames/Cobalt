package com.github.auties00.cobalt.wam.type;

import com.github.auties00.cobalt.wam.annotation.WamEnum;
import com.github.auties00.cobalt.wam.annotation.WamEnumConstant;

@WamEnum
public enum MessageCappingActionType {
    @WamEnumConstant(0) CLICK,
    @WamEnumConstant(1) VIEW,
    @WamEnumConstant(2) API,
    @WamEnumConstant(3) ENTER,
    @WamEnumConstant(4) DEBUG
}
