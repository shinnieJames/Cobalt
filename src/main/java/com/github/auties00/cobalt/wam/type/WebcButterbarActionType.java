package com.github.auties00.cobalt.wam.type;

import com.github.auties00.cobalt.wam.annotation.WamEnum;
import com.github.auties00.cobalt.wam.annotation.WamEnumConstant;

@WamEnum
public enum WebcButterbarActionType {
    @WamEnumConstant(1) IMPRESSION,
    @WamEnumConstant(2) CLICK_CTA,
    @WamEnumConstant(3) CLICK_DISMISS,
    @WamEnumConstant(4) AUTO_DISMISS
}
