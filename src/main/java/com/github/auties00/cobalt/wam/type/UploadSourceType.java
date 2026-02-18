package com.github.auties00.cobalt.wam.type;

import com.github.auties00.cobalt.wam.annotation.WamEnum;
import com.github.auties00.cobalt.wam.annotation.WamEnumConstant;

@WamEnum
public enum UploadSourceType {
    @WamEnumConstant(1) OTHER,
    @WamEnumConstant(2) CAMERA,
    @WamEnumConstant(3) GALLERY,
    @WamEnumConstant(4) SHARE
}
