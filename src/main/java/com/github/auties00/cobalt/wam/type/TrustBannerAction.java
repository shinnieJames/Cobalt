package com.github.auties00.cobalt.wam.type;

import com.github.auties00.cobalt.wam.annotation.WamEnum;
import com.github.auties00.cobalt.wam.annotation.WamEnumConstant;

@WamEnum
public enum TrustBannerAction {
    @WamEnumConstant(0) VIEWED,
    @WamEnumConstant(1) DISMISSED
}
