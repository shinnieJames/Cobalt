package com.github.auties00.cobalt.wam.type;

import com.github.auties00.cobalt.wam.annotation.WamEnum;
import com.github.auties00.cobalt.wam.annotation.WamEnumConstant;

@WamEnum
public enum GroupEncryptionType {
    @WamEnumConstant(1) E2EE,
    @WamEnumConstant(2) HOSTED
}
