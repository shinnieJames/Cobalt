package com.github.auties00.cobalt.wam.type;

import com.github.auties00.cobalt.wam.annotation.WamEnum;
import com.github.auties00.cobalt.wam.annotation.WamEnumConstant;

@WamEnum
public enum ActionThreadTypeType {
    @WamEnumConstant(1) GROUP_CHAT,
    @WamEnumConstant(2) P2P_THREAD,
    @WamEnumConstant(3) MESSAGE_YOURSELF,
    @WamEnumConstant(4) META_AI,
    @WamEnumConstant(5) TO_CHARACTER,
    @WamEnumConstant(6) TO_UGC
}
