package com.github.auties00.cobalt.wam.type;

import com.github.auties00.cobalt.wam.annotation.WamEnum;
import com.github.auties00.cobalt.wam.annotation.WamEnumConstant;

@WamEnum
public enum WebcDisplayStatusType {
    @WamEnumConstant(1) SHOWED_PREVIEW_TO_USER,
    @WamEnumConstant(2) PREVIEW_TIMEOUT,
    @WamEnumConstant(3) PREVIEW_MALFORMED,
    @WamEnumConstant(4) PREVIEW_NOT_FOUND,
    @WamEnumConstant(5) PREVIEW_GENERAL_ERROR,
    @WamEnumConstant(6) PREVIEW_DECRYPTION_ERROR
}
