package com.github.auties00.cobalt.wam.type;

import com.github.auties00.cobalt.wam.annotation.WamEnum;
import com.github.auties00.cobalt.wam.annotation.WamEnumConstant;

@WamEnum
public enum SearchSelectedMessageSource {
    @WamEnumConstant(1) FTS,
    @WamEnumConstant(2) SEMANTIC,
    @WamEnumConstant(3) FTS_AND_SEMANTIC
}
