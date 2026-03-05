package com.github.auties00.cobalt.wam.type;

import com.github.auties00.cobalt.wam.annotation.WamEnum;
import com.github.auties00.cobalt.wam.annotation.WamEnumConstant;

@WamEnum
public enum CadminDemoteOriginType {
    @WamEnumConstant(1) PROMOTION_NOTIFICATION,
    @WamEnumConstant(2) MEMBER_LIST
}
