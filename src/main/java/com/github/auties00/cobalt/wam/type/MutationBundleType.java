package com.github.auties00.cobalt.wam.type;

import com.github.auties00.cobalt.wam.annotation.WamEnum;
import com.github.auties00.cobalt.wam.annotation.WamEnumConstant;

@WamEnum
public enum MutationBundleType {
    @WamEnumConstant(0) SNAPSHOT,
    @WamEnumConstant(1) PATCH
}
