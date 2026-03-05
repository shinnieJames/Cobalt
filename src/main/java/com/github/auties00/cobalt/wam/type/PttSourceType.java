package com.github.auties00.cobalt.wam.type;

import com.github.auties00.cobalt.wam.annotation.WamEnum;
import com.github.auties00.cobalt.wam.annotation.WamEnumConstant;

@WamEnum
public enum PttSourceType {
    @WamEnumConstant(0) FROM_CONVERSATION,
    @WamEnumConstant(1) FROM_VOICEMAIL
}
