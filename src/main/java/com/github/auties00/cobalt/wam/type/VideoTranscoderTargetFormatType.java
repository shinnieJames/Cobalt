package com.github.auties00.cobalt.wam.type;

import com.github.auties00.cobalt.wam.annotation.WamEnum;
import com.github.auties00.cobalt.wam.annotation.WamEnumConstant;

@WamEnum
public enum VideoTranscoderTargetFormatType {
    @WamEnumConstant(0) IMAGE,
    @WamEnumConstant(1) VIDEO,
    @WamEnumConstant(2) GIF
}
