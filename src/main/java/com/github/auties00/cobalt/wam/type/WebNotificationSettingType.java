package com.github.auties00.cobalt.wam.type;

import com.github.auties00.cobalt.wam.annotation.WamEnum;
import com.github.auties00.cobalt.wam.annotation.WamEnumConstant;

@WamEnum
public enum WebNotificationSettingType {
    @WamEnumConstant(1) ALLOWED,
    @WamEnumConstant(2) BLOCKED,
    @WamEnumConstant(3) UNKNOWN
}
