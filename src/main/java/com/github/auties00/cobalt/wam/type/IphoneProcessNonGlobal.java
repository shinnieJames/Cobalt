package com.github.auties00.cobalt.wam.type;

import com.github.auties00.cobalt.wam.annotation.WamEnum;
import com.github.auties00.cobalt.wam.annotation.WamEnumConstant;

@WamEnum
public enum IphoneProcessNonGlobal {
    @WamEnumConstant(1) MAIN,
    @WamEnumConstant(2) SHARE_EXT
}
