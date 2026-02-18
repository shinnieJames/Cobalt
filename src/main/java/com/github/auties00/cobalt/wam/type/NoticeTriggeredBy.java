package com.github.auties00.cobalt.wam.type;

import com.github.auties00.cobalt.wam.annotation.WamEnum;
import com.github.auties00.cobalt.wam.annotation.WamEnumConstant;

@WamEnum
public enum NoticeTriggeredBy {
    @WamEnumConstant(0) AUTO_START,
    @WamEnumConstant(1) BANNER,
    @WamEnumConstant(2) DEEP_LINK,
    @WamEnumConstant(3) JUST_IN_TIME
}
