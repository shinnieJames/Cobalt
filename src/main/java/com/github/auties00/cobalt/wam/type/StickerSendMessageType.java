package com.github.auties00.cobalt.wam.type;

import com.github.auties00.cobalt.wam.annotation.WamEnum;
import com.github.auties00.cobalt.wam.annotation.WamEnumConstant;

@WamEnum
public enum StickerSendMessageType {
    @WamEnumConstant(1) REGULAR,
    @WamEnumConstant(2) PAYMENTS
}
