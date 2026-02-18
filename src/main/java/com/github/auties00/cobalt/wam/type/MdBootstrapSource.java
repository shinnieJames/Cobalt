package com.github.auties00.cobalt.wam.type;

import com.github.auties00.cobalt.wam.annotation.WamEnum;
import com.github.auties00.cobalt.wam.annotation.WamEnumConstant;

@WamEnum
public enum MdBootstrapSource {
    @WamEnumConstant(1) APP_STATE,
    @WamEnumConstant(2) HISTORY
}
