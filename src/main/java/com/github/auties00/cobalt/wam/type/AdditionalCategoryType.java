package com.github.auties00.cobalt.wam.type;

import com.github.auties00.cobalt.wam.annotation.WamEnum;
import com.github.auties00.cobalt.wam.annotation.WamEnumConstant;

@WamEnum
public enum AdditionalCategoryType {
    @WamEnumConstant(1) META_AI_MODEL_BASE,
    @WamEnumConstant(2) META_AI_MODEL_PREMIUM
}
