package com.github.auties00.cobalt.wam.type;

import com.github.auties00.cobalt.wam.annotation.WamEnum;
import com.github.auties00.cobalt.wam.annotation.WamEnumConstant;

@WamEnum
public enum CtwaLoginType {
    @WamEnumConstant(0) CTWA_LOGIN_TYPE_FB_NATIVE,
    @WamEnumConstant(1) CTWA_LOGIN_TYPE_FB_WEB,
    @WamEnumConstant(2) CTWA_LOGIN_TYPE_WA_AD_ACCOUNT
}
