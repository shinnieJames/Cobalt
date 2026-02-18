package com.github.auties00.cobalt.wam.type;

import com.github.auties00.cobalt.wam.annotation.WamEnum;
import com.github.auties00.cobalt.wam.annotation.WamEnumConstant;

@WamEnum
public enum HttpProtocolVersionType {
    @WamEnumConstant(0) HTTP1,
    @WamEnumConstant(1) HTTP2,
    @WamEnumConstant(2) HTTP3
}
