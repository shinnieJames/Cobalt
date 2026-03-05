package com.github.auties00.cobalt.wam.type;

import com.github.auties00.cobalt.wam.annotation.WamEnum;
import com.github.auties00.cobalt.wam.annotation.WamEnumConstant;

@WamEnum
public enum PollActionType {
    @WamEnumConstant(1) OPEN_CREATE_MODAL,
    @WamEnumConstant(2) CREATE_POLL,
    @WamEnumConstant(4) VIEW_RESULTS_MODAL,
    @WamEnumConstant(5) REMOVE_VOTE,
    @WamEnumConstant(6) VOTE,
    @WamEnumConstant(7) CHANGE_VOTE
}
