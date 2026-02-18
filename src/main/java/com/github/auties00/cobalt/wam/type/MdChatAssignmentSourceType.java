package com.github.auties00.cobalt.wam.type;

import com.github.auties00.cobalt.wam.annotation.WamEnum;
import com.github.auties00.cobalt.wam.annotation.WamEnumConstant;

@WamEnum
public enum MdChatAssignmentSourceType {
    @WamEnumConstant(0) NONE,
    @WamEnumConstant(1) BOOTSTRAP
}
