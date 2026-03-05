package com.github.auties00.cobalt.wam.type;

import com.github.auties00.cobalt.wam.annotation.WamEnum;
import com.github.auties00.cobalt.wam.annotation.WamEnumConstant;

@WamEnum
public enum DisappearingModeSettingEventNameType {
    @WamEnumConstant(1) DEFAULT_MESSAGE_TIMER_OPEN,
    @WamEnumConstant(2) DEFAULT_MESSAGE_TIMER_SET,
    @WamEnumConstant(3) DEFAULT_MESSAGE_TIMER_EXIT,
    @WamEnumConstant(4) LEARN_MORE_CLICK
}
