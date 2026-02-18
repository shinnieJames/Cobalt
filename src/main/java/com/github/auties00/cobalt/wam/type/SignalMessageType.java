package com.github.auties00.cobalt.wam.type;

import com.github.auties00.cobalt.wam.annotation.WamEnum;
import com.github.auties00.cobalt.wam.annotation.WamEnumConstant;

@WamEnum
public enum SignalMessageType {
    @WamEnumConstant(0) NFM,
    @WamEnumConstant(1) HSM
}
