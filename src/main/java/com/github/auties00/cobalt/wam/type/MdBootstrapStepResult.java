package com.github.auties00.cobalt.wam.type;

import com.github.auties00.cobalt.wam.annotation.WamEnum;
import com.github.auties00.cobalt.wam.annotation.WamEnumConstant;

@WamEnum
public enum MdBootstrapStepResult {
    @WamEnumConstant(1) SUCCESS,
    @WamEnumConstant(2) FAILURE
}
