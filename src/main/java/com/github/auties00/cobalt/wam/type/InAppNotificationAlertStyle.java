package com.github.auties00.cobalt.wam.type;

import com.github.auties00.cobalt.wam.annotation.WamEnum;
import com.github.auties00.cobalt.wam.annotation.WamEnumConstant;

@WamEnum
public enum InAppNotificationAlertStyle {
    @WamEnumConstant(1) NONE,
    @WamEnumConstant(2) BANNERS,
    @WamEnumConstant(3) ALERTS
}
