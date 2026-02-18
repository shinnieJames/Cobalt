package com.github.auties00.cobalt.wam.type;

import com.github.auties00.cobalt.wam.annotation.WamEnum;
import com.github.auties00.cobalt.wam.annotation.WamEnumConstant;

@WamEnum
public enum BlockReason {
    @WamEnumConstant(0) OTHER,
    @WamEnumConstant(1) NO_LONGER_NEEDED,
    @WamEnumConstant(2) NO_SIGN_UP,
    @WamEnumConstant(3) SPAM,
    @WamEnumConstant(4) OFFENSIVE_MESSAGES,
    @WamEnumConstant(5) OTP_DID_NOT_REQUEST,
    @WamEnumConstant(6) SCAM_OR_FRAUD
}
