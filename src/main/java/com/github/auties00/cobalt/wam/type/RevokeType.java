package com.github.auties00.cobalt.wam.type;

import com.github.auties00.cobalt.wam.annotation.WamEnum;
import com.github.auties00.cobalt.wam.annotation.WamEnumConstant;

@WamEnum
public enum RevokeType {
    @WamEnumConstant(0) SENDER,
    @WamEnumConstant(1) ADMIN
}
