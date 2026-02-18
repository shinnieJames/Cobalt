package com.github.auties00.cobalt.wam.type;

import com.github.auties00.cobalt.wam.annotation.WamEnum;
import com.github.auties00.cobalt.wam.annotation.WamEnumConstant;

@WamEnum
public enum DeviceType {
    @WamEnumConstant(1) PRIMARY,
    @WamEnumConstant(2) COMPANION
}
