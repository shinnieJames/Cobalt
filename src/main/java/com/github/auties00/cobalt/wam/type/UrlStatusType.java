package com.github.auties00.cobalt.wam.type;

import com.github.auties00.cobalt.wam.annotation.WamEnum;
import com.github.auties00.cobalt.wam.annotation.WamEnumConstant;

@WamEnum
public enum UrlStatusType {
    @WamEnumConstant(1) NO_PREVIEW,
    @WamEnumConstant(2) TRUNCATED,
    @WamEnumConstant(3) NON_TRUNCATED,
    @WamEnumConstant(4) INTERACTABLE,
    @WamEnumConstant(5) TOP_BAR_ATTRIBUTION
}
