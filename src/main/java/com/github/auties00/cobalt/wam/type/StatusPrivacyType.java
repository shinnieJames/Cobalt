package com.github.auties00.cobalt.wam.type;

import com.github.auties00.cobalt.wam.annotation.WamEnum;
import com.github.auties00.cobalt.wam.annotation.WamEnumConstant;

@WamEnum
public enum StatusPrivacyType {
    @WamEnumConstant(1) ALL_CONTACTS,
    @WamEnumConstant(2) EXCEPT,
    @WamEnumConstant(3) ONLY_WITH,
    @WamEnumConstant(4) CLOSE_FRIENDS
}
