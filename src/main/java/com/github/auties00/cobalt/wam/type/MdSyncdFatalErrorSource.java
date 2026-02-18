package com.github.auties00.cobalt.wam.type;

import com.github.auties00.cobalt.wam.annotation.WamEnum;
import com.github.auties00.cobalt.wam.annotation.WamEnumConstant;

@WamEnum
public enum MdSyncdFatalErrorSource {
    @WamEnumConstant(1) SNAPSHOT,
    @WamEnumConstant(2) EXTERNAL_PATCH,
    @WamEnumConstant(3) INLINE_PATCH
}
