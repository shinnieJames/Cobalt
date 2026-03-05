package com.github.auties00.cobalt.wam.type;

import com.github.auties00.cobalt.wam.annotation.WamEnum;
import com.github.auties00.cobalt.wam.annotation.WamEnumConstant;

@WamEnum
public enum BannerStatus {
    @WamEnumConstant(0) DISPLAYED,
    @WamEnumConstant(1) HIDDEN,
    @WamEnumConstant(2) CLOSED
}
