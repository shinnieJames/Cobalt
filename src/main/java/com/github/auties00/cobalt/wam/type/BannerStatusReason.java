package com.github.auties00.cobalt.wam.type;

import com.github.auties00.cobalt.wam.annotation.WamEnum;
import com.github.auties00.cobalt.wam.annotation.WamEnumConstant;

@WamEnum
public enum BannerStatusReason {
    @WamEnumConstant(0) NO_SIMILAR_CHANNELS_FOUND,
    @WamEnumConstant(1) NOT_ENOUGH_SIMILAR_CHANNELS,
    @WamEnumConstant(2) SIMILAR_CHANNELS_FOUND,
    @WamEnumConstant(3) CLOSE_TAP,
    @WamEnumConstant(4) UNFOLLOW_TAP
}
