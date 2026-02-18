package com.github.auties00.cobalt.wam.type;

import com.github.auties00.cobalt.wam.annotation.WamEnum;
import com.github.auties00.cobalt.wam.annotation.WamEnumConstant;

@WamEnum
public enum UrlStatusClicked {
    @WamEnumConstant(1) ONE_CLICK,
    @WamEnumConstant(2) TWO_CLICKS,
    @WamEnumConstant(3) NO_CLICK
}
