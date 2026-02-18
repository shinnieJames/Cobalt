package com.github.auties00.cobalt.wam.type;

import com.github.auties00.cobalt.wam.annotation.WamEnum;
import com.github.auties00.cobalt.wam.annotation.WamEnumConstant;

@WamEnum
public enum TypeOfGroupEnum {
    @WamEnumConstant(1) GROUP,
    @WamEnumConstant(2) SUBGROUP,
    @WamEnumConstant(3) DEFAULT_SUBGROUP
}
