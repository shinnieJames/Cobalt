package com.github.auties00.cobalt.wam.type;

import com.github.auties00.cobalt.wam.annotation.WamEnum;
import com.github.auties00.cobalt.wam.annotation.WamEnumConstant;

@WamEnum
public enum OfflineProcessRunReasons {
    @WamEnumConstant(1) PUSH_NOTIFICATION,
    @WamEnumConstant(2) PERIODIC_BACKGROUND_SYNC
}
