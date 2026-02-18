package com.github.auties00.cobalt.wam.type;

import com.github.auties00.cobalt.wam.annotation.WamEnum;
import com.github.auties00.cobalt.wam.annotation.WamEnumConstant;

@WamEnum
public enum AppBuildType {
    @WamEnumConstant(1) DEBUG,
    @WamEnumConstant(2) ALPHA,
    @WamEnumConstant(3) BETA,
    @WamEnumConstant(4) RELEASE
}
