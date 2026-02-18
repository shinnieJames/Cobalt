package com.github.auties00.cobalt.wam.type;

import com.github.auties00.cobalt.wam.annotation.WamEnum;
import com.github.auties00.cobalt.wam.annotation.WamEnumConstant;

@WamEnum
public enum MessageSecretErrorType {
    @WamEnumConstant(0) MISSING_MESSAGE_SECRET,
    @WamEnumConstant(1) WRONG_LENGTH,
    @WamEnumConstant(2) ENCRYPTION_ERROR,
    @WamEnumConstant(3) DECRYPTION_ERROR
}
