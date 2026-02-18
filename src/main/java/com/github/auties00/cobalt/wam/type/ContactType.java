package com.github.auties00.cobalt.wam.type;

import com.github.auties00.cobalt.wam.annotation.WamEnum;
import com.github.auties00.cobalt.wam.annotation.WamEnumConstant;

@WamEnum
public enum ContactType {
    @WamEnumConstant(1) CONSUMER,
    @WamEnumConstant(2) SMB,
    @WamEnumConstant(3) ENTERPRISE
}
