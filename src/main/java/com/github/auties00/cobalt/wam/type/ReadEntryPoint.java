package com.github.auties00.cobalt.wam.type;

import com.github.auties00.cobalt.wam.annotation.WamEnum;
import com.github.auties00.cobalt.wam.annotation.WamEnumConstant;

@WamEnum
public enum ReadEntryPoint {
    @WamEnumConstant(1) CHAT_LIST,
    @WamEnumConstant(2) CHAT
}
