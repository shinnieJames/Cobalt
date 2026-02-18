package com.github.auties00.cobalt.wam.type;

import com.github.auties00.cobalt.wam.annotation.WamEnum;
import com.github.auties00.cobalt.wam.annotation.WamEnumConstant;

@WamEnum
public enum DisclosureSurface {
    @WamEnumConstant(0) BIZ_PROFILE_SCREEN,
    @WamEnumConstant(1) CHAT_THREAD
}
