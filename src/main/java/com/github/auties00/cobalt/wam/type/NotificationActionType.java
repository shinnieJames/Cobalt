package com.github.auties00.cobalt.wam.type;

import com.github.auties00.cobalt.wam.annotation.WamEnum;
import com.github.auties00.cobalt.wam.annotation.WamEnumConstant;

@WamEnum
public enum NotificationActionType {
    @WamEnumConstant(1) SHOW,
    @WamEnumConstant(2) REMOVE
}
