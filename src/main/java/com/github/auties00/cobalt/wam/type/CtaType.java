package com.github.auties00.cobalt.wam.type;

import com.github.auties00.cobalt.wam.annotation.WamEnum;
import com.github.auties00.cobalt.wam.annotation.WamEnumConstant;

@WamEnum
public enum CtaType {
    @WamEnumConstant(0) COPY_CODE,
    @WamEnumConstant(1) AUTOFILL
}
