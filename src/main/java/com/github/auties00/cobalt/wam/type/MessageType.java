package com.github.auties00.cobalt.wam.type;

import com.github.auties00.cobalt.wam.annotation.WamEnum;
import com.github.auties00.cobalt.wam.annotation.WamEnumConstant;

@WamEnum
public enum MessageType {
    @WamEnumConstant(1) INDIVIDUAL,
    @WamEnumConstant(2) GROUP,
    @WamEnumConstant(3) BROADCAST,
    @WamEnumConstant(4) STATUS,
    @WamEnumConstant(5) CHANNEL,
    @WamEnumConstant(6) INTEROP,
    @WamEnumConstant(7) GREETING,
    @WamEnumConstant(8) MEDIA_HUB
}
