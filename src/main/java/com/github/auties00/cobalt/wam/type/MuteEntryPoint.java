package com.github.auties00.cobalt.wam.type;

import com.github.auties00.cobalt.wam.annotation.WamEnum;
import com.github.auties00.cobalt.wam.annotation.WamEnumConstant;

@WamEnum
public enum MuteEntryPoint {
    @WamEnumConstant(1) CHAT_LIST_SCREEN,
    @WamEnumConstant(2) CONTACT_INFO,
    @WamEnumConstant(3) CONVERSATION_SCREEN,
    @WamEnumConstant(4) CHAT_MORE_OPTIONS,
    @WamEnumConstant(5) CHAT_LONG_PRESS_OPTIONS,
    @WamEnumConstant(6) INORGANIC_NOTIFICATION,
    @WamEnumConstant(7) LIST_BASED_MUTE
}
