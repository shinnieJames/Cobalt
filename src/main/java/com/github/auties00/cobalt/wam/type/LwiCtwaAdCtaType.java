package com.github.auties00.cobalt.wam.type;

import com.github.auties00.cobalt.wam.annotation.WamEnum;
import com.github.auties00.cobalt.wam.annotation.WamEnumConstant;

@WamEnum
public enum LwiCtwaAdCtaType {
    @WamEnumConstant(1) PROMOTE_AGAIN,
    @WamEnumConstant(2) PAUSE_AD,
    @WamEnumConstant(3) RESUME_AD,
    @WamEnumConstant(4) ADD_BUDGET,
    @WamEnumConstant(5) VIEW_AD,
    @WamEnumConstant(6) COMPLETE_PAYMENT,
    @WamEnumConstant(7) RECREATE_AD_WITH_RECOMMENDATION,
    @WamEnumConstant(8) EDIT_AD_WITH_RECOMMENDATION,
    @WamEnumConstant(9) DELETE_AD
}
