package com.github.auties00.cobalt.wam.type;

import com.github.auties00.cobalt.wam.annotation.WamEnum;
import com.github.auties00.cobalt.wam.annotation.WamEnumConstant;

@WamEnum
public enum GroupRoleType {
    @WamEnumConstant(1) ADMIN,
    @WamEnumConstant(2) MEMBER
}
