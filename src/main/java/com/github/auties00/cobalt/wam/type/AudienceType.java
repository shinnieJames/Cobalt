package com.github.auties00.cobalt.wam.type;

import com.github.auties00.cobalt.wam.annotation.WamEnum;
import com.github.auties00.cobalt.wam.annotation.WamEnumConstant;

@WamEnum
public enum AudienceType {
    @WamEnumConstant(1) REGION,
    @WamEnumConstant(2) MAP
}
