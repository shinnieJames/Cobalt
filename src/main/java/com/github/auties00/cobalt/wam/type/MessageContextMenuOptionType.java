package com.github.auties00.cobalt.wam.type;

import com.github.auties00.cobalt.wam.annotation.WamEnum;
import com.github.auties00.cobalt.wam.annotation.WamEnumConstant;

@WamEnum
public enum MessageContextMenuOptionType {
    @WamEnumConstant(1) UNKNOWN,
    @WamEnumConstant(2) OVERFLOW,
    @WamEnumConstant(3) FORWARD,
    @WamEnumConstant(4) DELETE,
    @WamEnumConstant(5) REPLY,
    @WamEnumConstant(6) REPLY_PRIVATELY,
    @WamEnumConstant(7) STAR_OR_UNSTAR,
    @WamEnumConstant(8) COPY,
    @WamEnumConstant(9) REPORT,
    @WamEnumConstant(10) MESSAGE_CONTACT,
    @WamEnumConstant(11) MESSAGE_INFO,
    @WamEnumConstant(12) EDIT,
    @WamEnumConstant(13) FORWARD_SELECT_MESSAGES,
    @WamEnumConstant(14) DELETE_SELECT_MESSAGES,
    @WamEnumConstant(15) SELECT,
    @WamEnumConstant(16) ADD_TO_CALENDAR
}
