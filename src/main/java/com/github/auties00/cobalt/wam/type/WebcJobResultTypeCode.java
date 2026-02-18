package com.github.auties00.cobalt.wam.type;

import com.github.auties00.cobalt.wam.annotation.WamEnum;
import com.github.auties00.cobalt.wam.annotation.WamEnumConstant;

@WamEnum
public enum WebcJobResultTypeCode {
    @WamEnumConstant(0) COMPLETED,
    @WamEnumConstant(1) ERROR,
    @WamEnumConstant(2) TIMEOUT,
    @WamEnumConstant(3) ABORTED
}
