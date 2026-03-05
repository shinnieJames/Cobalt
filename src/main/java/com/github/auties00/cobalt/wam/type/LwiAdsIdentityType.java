package com.github.auties00.cobalt.wam.type;

import com.github.auties00.cobalt.wam.annotation.WamEnum;
import com.github.auties00.cobalt.wam.annotation.WamEnumConstant;

@WamEnum
public enum LwiAdsIdentityType {
    @WamEnumConstant(1) PAGE,
    @WamEnumConstant(2) WHATSAPP
}
