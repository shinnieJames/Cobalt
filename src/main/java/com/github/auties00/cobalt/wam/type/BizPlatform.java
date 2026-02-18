package com.github.auties00.cobalt.wam.type;

import com.github.auties00.cobalt.wam.annotation.WamEnum;
import com.github.auties00.cobalt.wam.annotation.WamEnumConstant;

@WamEnum
public enum BizPlatform {
    @WamEnumConstant(1) UNKNOWN,
    @WamEnumConstant(2) SMB,
    @WamEnumConstant(3) ENT,
    @WamEnumConstant(4) CLOUDAPI
}
