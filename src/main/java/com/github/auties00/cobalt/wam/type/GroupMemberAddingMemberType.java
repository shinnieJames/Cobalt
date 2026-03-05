package com.github.auties00.cobalt.wam.type;

import com.github.auties00.cobalt.wam.annotation.WamEnum;
import com.github.auties00.cobalt.wam.annotation.WamEnumConstant;

@WamEnum
public enum GroupMemberAddingMemberType {
    @WamEnumConstant(0) OPEN_META_AI,
    @WamEnumConstant(1) TEE_BOT,
    @WamEnumConstant(2) WA_USER
}
