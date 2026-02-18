package com.github.auties00.cobalt.wam.type;

import com.github.auties00.cobalt.wam.annotation.WamEnum;
import com.github.auties00.cobalt.wam.annotation.WamEnumConstant;

@WamEnum
public enum PsaBlockReason {
    @WamEnumConstant(0) OTHER,
    @WamEnumConstant(1) MESSAGES_ARENT_HELPFUL,
    @WamEnumConstant(2) TOO_MANY_MESSAGES,
    @WamEnumConstant(3) IT_LOOKS_SUSPICIOUS
}
