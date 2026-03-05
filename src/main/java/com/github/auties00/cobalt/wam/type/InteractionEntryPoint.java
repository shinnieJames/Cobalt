package com.github.auties00.cobalt.wam.type;

import com.github.auties00.cobalt.wam.annotation.WamEnum;
import com.github.auties00.cobalt.wam.annotation.WamEnumConstant;

@WamEnum
public enum InteractionEntryPoint {
    @WamEnumConstant(0) ALERT_BANNER,
    @WamEnumConstant(1) INFO_DRAWER_ALERT_OPTION
}
