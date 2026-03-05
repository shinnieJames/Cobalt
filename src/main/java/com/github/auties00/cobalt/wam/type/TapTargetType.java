package com.github.auties00.cobalt.wam.type;

import com.github.auties00.cobalt.wam.annotation.WamEnum;
import com.github.auties00.cobalt.wam.annotation.WamEnumConstant;

@WamEnum
public enum TapTargetType {
    @WamEnumConstant(0) FULL,
    @WamEnumConstant(1) WITHOUT_TITLE,
    @WamEnumConstant(2) WITHOUT_THUMBNAIL,
    @WamEnumConstant(3) WITHOUT_TITLE_AND_THUMBNAIL
}
