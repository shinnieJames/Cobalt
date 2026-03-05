package com.github.auties00.cobalt.wam.type;

import com.github.auties00.cobalt.wam.annotation.WamEnum;
import com.github.auties00.cobalt.wam.annotation.WamEnumConstant;

@WamEnum
public enum ListUpdateUserJourneyAction {
    @WamEnumConstant(0) START,
    @WamEnumConstant(1) SELECT_PREDEFINED,
    @WamEnumConstant(2) CREATE_LIST
}
