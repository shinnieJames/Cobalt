package com.github.auties00.cobalt.wam.type;

import com.github.auties00.cobalt.wam.annotation.WamEnum;
import com.github.auties00.cobalt.wam.annotation.WamEnumConstant;

@WamEnum
public enum PlaceholderPopulationType {
    @WamEnumConstant(0) OTHER,
    @WamEnumConstant(1) RETRY,
    @WamEnumConstant(2) PEER_MESSAGE,
    @WamEnumConstant(3) RESEND
}
