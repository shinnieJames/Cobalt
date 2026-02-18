package com.github.auties00.cobalt.wam.type;

import com.github.auties00.cobalt.wam.annotation.WamEnum;
import com.github.auties00.cobalt.wam.annotation.WamEnumConstant;

@WamEnum
public enum StickerErrorType {
    @WamEnumConstant(2) DECOMPRESSION,
    @WamEnumConstant(3) SENDER_VALIDATION,
    @WamEnumConstant(4) RECEIVER_VALIDATION
}
