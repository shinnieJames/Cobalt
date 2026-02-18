package com.github.auties00.cobalt.wam.type;

import com.github.auties00.cobalt.wam.annotation.WamEnum;
import com.github.auties00.cobalt.wam.annotation.WamEnumConstant;

@WamEnum
public enum AndroidKeystoreStateType {
    @WamEnumConstant(1) NOT_AVAILABLE,
    @WamEnumConstant(2) SELF_TEST_FAILURE,
    @WamEnumConstant(3) ENC_KEY_READ_FAILURE,
    @WamEnumConstant(4) ENC_KEY_STORED_USED,
    @WamEnumConstant(5) ENC_KEY_PLAIN_DELETED,
    @WamEnumConstant(6) ENC_KEY_PLAIN_RECOVERED
}
