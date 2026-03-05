package com.github.auties00.cobalt.wam.type;

import com.github.auties00.cobalt.wam.annotation.WamEnum;
import com.github.auties00.cobalt.wam.annotation.WamEnumConstant;

@WamEnum
public enum StwFormat {
    @WamEnumConstant(0) SINGLE_LINK,
    @WamEnumConstant(1) SINGLE_IMAGE,
    @WamEnumConstant(2) SINGLE_TEXT,
    @WamEnumConstant(3) MULTIPLE_LINK_IMAGE,
    @WamEnumConstant(4) MULTIPLE_LINK_TEXT,
    @WamEnumConstant(5) MULTIPLE_IMAGE_TEXT,
    @WamEnumConstant(6) MULTIPLE_LINK_IMAGE_TEXT
}
