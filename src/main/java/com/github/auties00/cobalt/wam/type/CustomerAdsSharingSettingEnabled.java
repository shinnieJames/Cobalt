package com.github.auties00.cobalt.wam.type;

import com.github.auties00.cobalt.wam.annotation.WamEnum;
import com.github.auties00.cobalt.wam.annotation.WamEnumConstant;

@WamEnum
public enum CustomerAdsSharingSettingEnabled {
    @WamEnumConstant(0) UNSET,
    @WamEnumConstant(1) TRUE,
    @WamEnumConstant(2) FALSE
}
