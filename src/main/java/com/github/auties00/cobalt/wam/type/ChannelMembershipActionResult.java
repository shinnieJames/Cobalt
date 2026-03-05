package com.github.auties00.cobalt.wam.type;

import com.github.auties00.cobalt.wam.annotation.WamEnum;
import com.github.auties00.cobalt.wam.annotation.WamEnumConstant;

@WamEnum
public enum ChannelMembershipActionResult {
    @WamEnumConstant(1) FOLLOW_SUCCESS,
    @WamEnumConstant(2) UNFOLLOW_SUCCESS,
    @WamEnumConstant(3) FOLLOW_SUCCESS_NOTIFICATION,
    @WamEnumConstant(4) UNFOLLOW_SUCCESS_NOTIFICATION
}
