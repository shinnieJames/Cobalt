package com.github.auties00.cobalt.wam.type;

import com.github.auties00.cobalt.wam.annotation.WamEnum;
import com.github.auties00.cobalt.wam.annotation.WamEnumConstant;

@WamEnum
public enum BundleSendSource {
    @WamEnumConstant(1) NOTIFICATION,
    @WamEnumConstant(2) IQ_RESPONSE
}
