package com.github.auties00.cobalt.wam.type;

import com.github.auties00.cobalt.wam.annotation.WamEnum;
import com.github.auties00.cobalt.wam.annotation.WamEnumConstant;

@WamEnum
public enum SignalSharingStatus {
    @WamEnumConstant(0) ONE_PD,
    @WamEnumConstant(1) SP,
    @WamEnumConstant(2) NOT_SHARED
}
