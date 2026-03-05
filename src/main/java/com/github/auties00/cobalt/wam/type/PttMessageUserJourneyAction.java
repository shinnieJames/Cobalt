package com.github.auties00.cobalt.wam.type;

import com.github.auties00.cobalt.wam.annotation.WamEnum;
import com.github.auties00.cobalt.wam.annotation.WamEnumConstant;

@WamEnum
public enum PttMessageUserJourneyAction {
    @WamEnumConstant(1) START,
    @WamEnumConstant(2) PAUSE,
    @WamEnumConstant(3) RESUME,
    @WamEnumConstant(4) FAIL,
    @WamEnumConstant(5) SEND,
    @WamEnumConstant(6) DELETE,
    @WamEnumConstant(7) AUTO_CANCEL,
    @WamEnumConstant(8) LOCK,
    @WamEnumConstant(9) DRAFT_SAVED,
    @WamEnumConstant(10) DRAFT_LOADED
}
