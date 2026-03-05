package com.github.auties00.cobalt.wam.type;

import com.github.auties00.cobalt.wam.annotation.WamEnum;
import com.github.auties00.cobalt.wam.annotation.WamEnumConstant;

@WamEnum
public enum SystemMessageCategoryType {
    @WamEnumConstant(1) PRIVACY,
    @WamEnumConstant(2) GROUPS
}
