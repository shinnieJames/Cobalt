package com.github.auties00.cobalt.wam.type;

import com.github.auties00.cobalt.wam.annotation.WamEnum;
import com.github.auties00.cobalt.wam.annotation.WamEnumConstant;

@WamEnum
public enum ErrorType {
    @WamEnumConstant(0) ERROR_FETCHING_AGENT_NAME,
    @WamEnumConstant(1) ERROR_FETCHING_CHAT,
    @WamEnumConstant(2) ERROR_OTHER
}
