package com.github.auties00.cobalt.wam.type;

import com.github.auties00.cobalt.wam.annotation.WamEnum;
import com.github.auties00.cobalt.wam.annotation.WamEnumConstant;

@WamEnum
public enum UiRevokeActionType {
    @WamEnumConstant(0) MESSAGE_SELECTED,
    @WamEnumConstant(1) TRASH_CAN_SELECTED,
    @WamEnumConstant(2) ADMIN_DELETE_FOR_EVERYONE,
    @WamEnumConstant(3) SENDER_DELETE_FOR_EVERYONE,
    @WamEnumConstant(4) ADMIN_AND_SENDER_DELETE_FOR_EVERYONE,
    @WamEnumConstant(5) DELETE_FOR_EVERYONE_SELECTED
}
