package com.github.auties00.cobalt.wam.type;

import com.github.auties00.cobalt.wam.annotation.WamEnum;
import com.github.auties00.cobalt.wam.annotation.WamEnumConstant;

@WamEnum
public enum PinInChatType {
    @WamEnumConstant(1) PIN_FOR_ALL,
    @WamEnumConstant(2) UNPIN_FOR_ALL
}
