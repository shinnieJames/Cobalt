package com.github.auties00.cobalt.wam.type;

import com.github.auties00.cobalt.wam.annotation.WamEnum;
import com.github.auties00.cobalt.wam.annotation.WamEnumConstant;

@WamEnum
public enum KicActionNameType {
    @WamEnumConstant(1) KEEP_MESSAGE,
    @WamEnumConstant(2) UNKEEP_MESSAGE,
    @WamEnumConstant(3) VIEW_KEPT_MESSAGES,
    @WamEnumConstant(4) SEARCH_RESULTS_DISPLAY,
    @WamEnumConstant(5) SEARCH_RESULTS_TAP
}
