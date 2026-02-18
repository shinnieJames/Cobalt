package com.github.auties00.cobalt.wam.type;

import com.github.auties00.cobalt.wam.annotation.WamEnum;
import com.github.auties00.cobalt.wam.annotation.WamEnumConstant;

@WamEnum
public enum PlaceholderType {
    @WamEnumConstant(0) OTHER,
    @WamEnumConstant(1) CIPHERTEXT,
    @WamEnumConstant(2) FANOUT,
    @WamEnumConstant(3) DOWNGRADE,
    @WamEnumConstant(4) FIXED_CONTENT,
    @WamEnumConstant(5) TEMPORARY,
    @WamEnumConstant(6) DROP
}
