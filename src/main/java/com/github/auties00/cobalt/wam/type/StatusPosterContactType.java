package com.github.auties00.cobalt.wam.type;

import com.github.auties00.cobalt.wam.annotation.WamEnum;
import com.github.auties00.cobalt.wam.annotation.WamEnumConstant;

@WamEnum
public enum StatusPosterContactType {
    @WamEnumConstant(0) UNKNOWN,
    @WamEnumConstant(1) CONTACT,
    @WamEnumConstant(2) TRUSTED_INDIVIDUAL,
    @WamEnumConstant(3) TRUSTED_GROUP_MEMBER
}
