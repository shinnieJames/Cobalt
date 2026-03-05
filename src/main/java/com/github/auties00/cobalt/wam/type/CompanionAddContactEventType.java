package com.github.auties00.cobalt.wam.type;

import com.github.auties00.cobalt.wam.annotation.WamEnum;
import com.github.auties00.cobalt.wam.annotation.WamEnumConstant;

@WamEnum
public enum CompanionAddContactEventType {
    @WamEnumConstant(0) CREATE_NEW,
    @WamEnumConstant(1) EDIT
}
