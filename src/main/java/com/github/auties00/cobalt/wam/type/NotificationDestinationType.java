package com.github.auties00.cobalt.wam.type;

import com.github.auties00.cobalt.wam.annotation.WamEnum;
import com.github.auties00.cobalt.wam.annotation.WamEnumConstant;

@WamEnum
public enum NotificationDestinationType {
    @WamEnumConstant(1) INDIVIDUAL,
    @WamEnumConstant(2) GROUP,
    @WamEnumConstant(3) OTHER,
    @WamEnumConstant(4) CHANNEL,
    @WamEnumConstant(5) INTEROP,
    @WamEnumConstant(6) STATUS
}
