package com.github.auties00.cobalt.wam.type;

import com.github.auties00.cobalt.wam.annotation.WamEnum;
import com.github.auties00.cobalt.wam.annotation.WamEnumConstant;

@WamEnum
public enum EntryPointType {
    @WamEnumConstant(1) MAIN_SCREEN,
    @WamEnumConstant(2) CONTACT_INFO
}
