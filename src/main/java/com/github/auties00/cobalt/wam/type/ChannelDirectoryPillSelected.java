package com.github.auties00.cobalt.wam.type;

import com.github.auties00.cobalt.wam.annotation.WamEnum;
import com.github.auties00.cobalt.wam.annotation.WamEnumConstant;

@WamEnum
public enum ChannelDirectoryPillSelected {
    @WamEnumConstant(1) RECOMMENDED,
    @WamEnumConstant(2) TRENDING,
    @WamEnumConstant(3) FEATURED,
    @WamEnumConstant(4) NEW,
    @WamEnumConstant(5) POPULAR
}
