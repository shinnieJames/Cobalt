package com.github.auties00.cobalt.wam.type;

import com.github.auties00.cobalt.wam.annotation.WamEnum;
import com.github.auties00.cobalt.wam.annotation.WamEnumConstant;

@WamEnum
public enum ConnectionOriginType {
    @WamEnumConstant(1) PERSON,
    @WamEnumConstant(2) PUSH,
    @WamEnumConstant(3) OTHER,
    @WamEnumConstant(4) BACKOFF
}
