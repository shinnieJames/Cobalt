package com.github.auties00.cobalt.wam.type;

import com.github.auties00.cobalt.wam.annotation.WamEnum;
import com.github.auties00.cobalt.wam.annotation.WamEnumConstant;

@WamEnum
public enum TsExternalEventSource {
    @WamEnumConstant(1) CALL,
    @WamEnumConstant(2) VIDEO,
    @WamEnumConstant(3) PTT_RECORD,
    @WamEnumConstant(4) PTT_PLAY,
    @WamEnumConstant(5) MESSAGE_SEND_BACKGROUND
}
