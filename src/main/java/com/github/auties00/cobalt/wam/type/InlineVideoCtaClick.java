package com.github.auties00.cobalt.wam.type;

import com.github.auties00.cobalt.wam.annotation.WamEnum;
import com.github.auties00.cobalt.wam.annotation.WamEnumConstant;

@WamEnum
public enum InlineVideoCtaClick {
    @WamEnumConstant(1) LOGO,
    @WamEnumConstant(2) MUSIC,
    @WamEnumConstant(3) AUTHOR,
    @WamEnumConstant(4) WATCH_MORE_END
}
