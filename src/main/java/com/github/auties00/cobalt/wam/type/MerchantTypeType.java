package com.github.auties00.cobalt.wam.type;

import com.github.auties00.cobalt.wam.annotation.WamEnum;
import com.github.auties00.cobalt.wam.annotation.WamEnumConstant;

@WamEnum
public enum MerchantTypeType {
    @WamEnumConstant(1) API,
    @WamEnumConstant(2) SMB
}
