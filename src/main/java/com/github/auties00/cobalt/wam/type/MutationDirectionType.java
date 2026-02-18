package com.github.auties00.cobalt.wam.type;

import com.github.auties00.cobalt.wam.annotation.WamEnum;
import com.github.auties00.cobalt.wam.annotation.WamEnumConstant;

@WamEnum
public enum MutationDirectionType {
    @WamEnumConstant(0) INCOMING,
    @WamEnumConstant(1) OUTGOING
}
