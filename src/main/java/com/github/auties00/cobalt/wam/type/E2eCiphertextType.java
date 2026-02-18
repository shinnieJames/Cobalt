package com.github.auties00.cobalt.wam.type;

import com.github.auties00.cobalt.wam.annotation.WamEnum;
import com.github.auties00.cobalt.wam.annotation.WamEnumConstant;

@WamEnum
public enum E2eCiphertextType {
    @WamEnumConstant(0) MESSAGE,
    @WamEnumConstant(1) PREKEY_MESSAGE,
    @WamEnumConstant(2) SENDER_KEY_MESSAGE,
    @WamEnumConstant(3) MESSAGE_SECRET_MESSAGE
}
