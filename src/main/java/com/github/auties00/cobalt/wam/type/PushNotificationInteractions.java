package com.github.auties00.cobalt.wam.type;

import com.github.auties00.cobalt.wam.annotation.WamEnum;
import com.github.auties00.cobalt.wam.annotation.WamEnumConstant;

@WamEnum
public enum PushNotificationInteractions {
    @WamEnumConstant(1) SHOWN,
    @WamEnumConstant(2) CLICKED
}
