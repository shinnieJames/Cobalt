package com.github.auties00.cobalt.wam.type;

import com.github.auties00.cobalt.wam.annotation.WamEnum;
import com.github.auties00.cobalt.wam.annotation.WamEnumConstant;

@WamEnum
public enum QbmFlag {
    @WamEnumConstant(0) OTHER,
    @WamEnumConstant(1) TRANSACTIONAL,
    @WamEnumConstant(2) PROMOTIONAL,
    @WamEnumConstant(3) OTP,
    @WamEnumConstant(4) MARKETING_MESSAGE_SMB
}
