package com.github.auties00.cobalt.wam.type;

import com.github.auties00.cobalt.wam.annotation.WamEnum;
import com.github.auties00.cobalt.wam.annotation.WamEnumConstant;

@WamEnum
public enum PeerDataResponseApplyResultType {
    @WamEnumConstant(1) SUCCESS,
    @WamEnumConstant(2) OTHER_ERROR,
    @WamEnumConstant(3) INVALID_RESPONSE,
    @WamEnumConstant(4) FAIL_TO_DOWNLOAD,
    @WamEnumConstant(5) REQUEST_TIMEOUT
}
