package com.github.auties00.cobalt.wam.type;

import com.github.auties00.cobalt.wam.annotation.WamEnum;
import com.github.auties00.cobalt.wam.annotation.WamEnumConstant;

@WamEnum
public enum PaidMessagingUserInteractionsHeaderMediaType {
    @WamEnumConstant(0) TEXT,
    @WamEnumConstant(1) IMAGE,
    @WamEnumConstant(2) VIDEO,
    @WamEnumConstant(3) LOCATION,
    @WamEnumConstant(4) DOCUMENT,
    @WamEnumConstant(5) GIF
}
