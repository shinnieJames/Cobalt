package com.github.auties00.cobalt.wam.type;

import com.github.auties00.cobalt.wam.annotation.WamEnum;
import com.github.auties00.cobalt.wam.annotation.WamEnumConstant;

@WamEnum
public enum PeerDataRequestErrorCode {
    @WamEnumConstant(0) NOT_SUPPORTED,
    @WamEnumConstant(1) GENERATION_ERROR,
    @WamEnumConstant(2) CHUNK_CONSUMED,
    @WamEnumConstant(3) TIMEOUT,
    @WamEnumConstant(4) SESSION_EXHAUSTED,
    @WamEnumConstant(5) CHUNK_EXHAUSTED,
    @WamEnumConstant(6) DUPLICATE_REQUEST
}
