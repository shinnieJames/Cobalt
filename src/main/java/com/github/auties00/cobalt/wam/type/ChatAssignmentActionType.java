package com.github.auties00.cobalt.wam.type;

import com.github.auties00.cobalt.wam.annotation.WamEnum;
import com.github.auties00.cobalt.wam.annotation.WamEnumConstant;

@WamEnum
public enum ChatAssignmentActionType {
    @WamEnumConstant(0) ACTION_ASSIGNED,
    @WamEnumConstant(1) ACTION_UNASSIGNED,
    @WamEnumConstant(2) ACTION_REASSIGNED
}
