package com.github.auties00.cobalt.wam.type;

import com.github.auties00.cobalt.wam.annotation.WamEnum;
import com.github.auties00.cobalt.wam.annotation.WamEnumConstant;

@WamEnum
public enum ChatActionChatType {
    @WamEnumConstant(1) INDIVIDUAL,
    @WamEnumConstant(2) GROUP,
    @WamEnumConstant(3) BUSINESS,
    @WamEnumConstant(4) BROADCAST_LIST
}
