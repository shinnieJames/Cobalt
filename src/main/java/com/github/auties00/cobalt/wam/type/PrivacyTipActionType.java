package com.github.auties00.cobalt.wam.type;

import com.github.auties00.cobalt.wam.annotation.WamEnum;
import com.github.auties00.cobalt.wam.annotation.WamEnumConstant;

@WamEnum
public enum PrivacyTipActionType {
    @WamEnumConstant(1) VIEW,
    @WamEnumConstant(2) CLICK_PRIVACY_TIP,
    @WamEnumConstant(3) CLICK_OK,
    @WamEnumConstant(4) CLICK_OUTSIDE
}
