package com.github.auties00.cobalt.wam.type;

import com.github.auties00.cobalt.wam.annotation.WamEnum;
import com.github.auties00.cobalt.wam.annotation.WamEnumConstant;

@WamEnum
public enum EncryptionTypeCode {
    @WamEnumConstant(1) E2EE,
    @WamEnumConstant(2) COEX,
    @WamEnumConstant(3) SELF_COEX,
    @WamEnumConstant(4) CAPI,
    @WamEnumConstant(5) BSP,
    @WamEnumConstant(6) GUEST,
    @WamEnumConstant(7) TEE
}
