package com.github.auties00.cobalt.wam.type;

import com.github.auties00.cobalt.wam.annotation.WamEnum;
import com.github.auties00.cobalt.wam.annotation.WamEnumConstant;

@WamEnum
public enum CoexSysMsgStateTransitionAttempt {
    @WamEnumConstant(0) E2EE_TO_HOSTED,
    @WamEnumConstant(1) HOSTED_TO_E2EE,
    @WamEnumConstant(2) HOSTED_TO_HOSTED
}
