package com.github.auties00.cobalt.wam.type;

import com.github.auties00.cobalt.wam.annotation.WamEnum;
import com.github.auties00.cobalt.wam.annotation.WamEnumConstant;

@WamEnum
public enum ChatMutedType {
    @WamEnumConstant(1) NOT_MUTED,
    @WamEnumConstant(2) MUTED_NO_NOTIFICATIONS,
    @WamEnumConstant(3) MUTED_SILENT_NOTIFICATIONS
}
