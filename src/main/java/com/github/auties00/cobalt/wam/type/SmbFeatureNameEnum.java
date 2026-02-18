package com.github.auties00.cobalt.wam.type;

import com.github.auties00.cobalt.wam.annotation.WamEnum;
import com.github.auties00.cobalt.wam.annotation.WamEnumConstant;

@WamEnum
public enum SmbFeatureNameEnum {
    @WamEnumConstant(0) NOTES,
    @WamEnumConstant(1) GEN_AI_AGENT,
    @WamEnumConstant(2) BROADCAST_LIST,
    @WamEnumConstant(3) BIZ_APP_ONBOARDING,
    @WamEnumConstant(4) BUSINESS_TOOLS_HOME,
    @WamEnumConstant(5) CATALOG,
    @WamEnumConstant(6) BUSINESS_PROFILE,
    @WamEnumConstant(7) LEARNING_HUB,
    @WamEnumConstant(8) BUSINESS_BROADCAST,
    @WamEnumConstant(9) ALERTS_CENTER,
    @WamEnumConstant(10) GOOGLE_ELIGIBILITY_OPT_OUT
}
