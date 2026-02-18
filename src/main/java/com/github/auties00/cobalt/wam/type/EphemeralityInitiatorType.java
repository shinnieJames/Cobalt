package com.github.auties00.cobalt.wam.type;

import com.github.auties00.cobalt.wam.annotation.WamEnum;
import com.github.auties00.cobalt.wam.annotation.WamEnumConstant;

@WamEnum
public enum EphemeralityInitiatorType {
    @WamEnumConstant(1) INITIATED_BY_ME,
    @WamEnumConstant(2) INITIATED_BY_OTHER,
    @WamEnumConstant(3) BIZ_UPGRADE_FB_HOSTING
}
