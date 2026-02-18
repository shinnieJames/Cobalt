package com.github.auties00.cobalt.wam.type;

import com.github.auties00.cobalt.wam.annotation.WamEnum;
import com.github.auties00.cobalt.wam.annotation.WamEnumConstant;

@WamEnum
public enum OffboardSources {
    @WamEnumConstant(1) IN_APP_SETTING,
    @WamEnumConstant(2) OS_SETTING
}
