package com.github.auties00.cobalt.wam.type;

import com.github.auties00.cobalt.wam.annotation.WamEnum;
import com.github.auties00.cobalt.wam.annotation.WamEnumConstant;

@WamEnum
public enum MetaAiUpsellCtaOperationType {
    @WamEnumConstant(1) IMPRESSION,
    @WamEnumConstant(2) BUTTON_CLICK,
    @WamEnumConstant(3) DISMISS
}
