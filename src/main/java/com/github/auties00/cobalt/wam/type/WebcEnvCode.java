package com.github.auties00.cobalt.wam.type;

import com.github.auties00.cobalt.wam.annotation.WamEnum;
import com.github.auties00.cobalt.wam.annotation.WamEnumConstant;

@WamEnum
public enum WebcEnvCode {
    @WamEnumConstant(0) PROD,
    @WamEnumConstant(1) INTERN,
    @WamEnumConstant(2) DEV,
    @WamEnumConstant(3) E2E
}
