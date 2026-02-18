package com.github.auties00.cobalt.wam.type;

import com.github.auties00.cobalt.wam.annotation.WamEnum;
import com.github.auties00.cobalt.wam.annotation.WamEnumConstant;

@WamEnum
public enum AddressingMode {
    @WamEnumConstant(1) PN,
    @WamEnumConstant(2) LID
}
