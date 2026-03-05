package com.github.auties00.cobalt.wam.type;

import com.github.auties00.cobalt.wam.annotation.WamEnum;
import com.github.auties00.cobalt.wam.annotation.WamEnumConstant;

@WamEnum
public enum BusinessInteractionActionType {
    @WamEnumConstant(1) ACTION_CLICK,
    @WamEnumConstant(2) ACTION_MSG_SENT
}
