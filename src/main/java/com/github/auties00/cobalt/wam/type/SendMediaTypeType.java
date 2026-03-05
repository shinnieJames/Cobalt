package com.github.auties00.cobalt.wam.type;

import com.github.auties00.cobalt.wam.annotation.WamEnum;
import com.github.auties00.cobalt.wam.annotation.WamEnumConstant;

@WamEnum
public enum SendMediaTypeType {
    @WamEnumConstant(1) PHOTO,
    @WamEnumConstant(2) VIDEO,
    @WamEnumConstant(3) MIXED
}
