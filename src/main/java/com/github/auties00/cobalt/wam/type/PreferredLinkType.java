package com.github.auties00.cobalt.wam.type;

import com.github.auties00.cobalt.wam.annotation.WamEnum;
import com.github.auties00.cobalt.wam.annotation.WamEnumConstant;

@WamEnum
public enum PreferredLinkType {
    @WamEnumConstant(0) LOCAL,
    @WamEnumConstant(1) UNIVERSAL
}
