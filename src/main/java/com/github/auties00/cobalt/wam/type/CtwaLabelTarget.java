package com.github.auties00.cobalt.wam.type;

import com.github.auties00.cobalt.wam.annotation.WamEnum;
import com.github.auties00.cobalt.wam.annotation.WamEnumConstant;

@WamEnum
public enum CtwaLabelTarget {
    @WamEnumConstant(0) CHAT,
    @WamEnumConstant(1) MESSAGE
}
