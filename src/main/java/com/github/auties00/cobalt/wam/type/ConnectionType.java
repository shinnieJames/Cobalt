package com.github.auties00.cobalt.wam.type;

import com.github.auties00.cobalt.wam.annotation.WamEnum;
import com.github.auties00.cobalt.wam.annotation.WamEnumConstant;

@WamEnum
public enum ConnectionType {
    @WamEnumConstant(0) HOSTNAME,
    @WamEnumConstant(1) IP4,
    @WamEnumConstant(2) IP6
}
