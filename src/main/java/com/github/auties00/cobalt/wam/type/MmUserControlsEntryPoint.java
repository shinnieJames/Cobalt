package com.github.auties00.cobalt.wam.type;

import com.github.auties00.cobalt.wam.annotation.WamEnum;
import com.github.auties00.cobalt.wam.annotation.WamEnumConstant;

@WamEnum
public enum MmUserControlsEntryPoint {
    @WamEnumConstant(1) MESSAGE_BUBBLE,
    @WamEnumConstant(2) BOTTOM_SHEET,
    @WamEnumConstant(3) SYSTEM_MESSAGE,
    @WamEnumConstant(4) FMX_CARD,
    @WamEnumConstant(5) BUSINESS_PROFILE,
    @WamEnumConstant(6) TOAST,
    @WamEnumConstant(7) BLOCK_BOTTOM_SHEET_OTHER,
    @WamEnumConstant(8) BLOCK_BOTTOM_SHEET_CHAT_LIST,
    @WamEnumConstant(9) BLOCK_BOTTOM_SHEET_PROFILE,
    @WamEnumConstant(10) BLOCK_BOTTOM_SHEET_FMX,
    @WamEnumConstant(11) MESSAGE_CONTEXT_MENU,
    @WamEnumConstant(12) MESSAGE_THUMBS
}
