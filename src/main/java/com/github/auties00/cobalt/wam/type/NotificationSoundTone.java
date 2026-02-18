package com.github.auties00.cobalt.wam.type;

import com.github.auties00.cobalt.wam.annotation.WamEnum;
import com.github.auties00.cobalt.wam.annotation.WamEnumConstant;

@WamEnum
public enum NotificationSoundTone {
    @WamEnumConstant(1) DEFAULT,
    @WamEnumConstant(2) CUSTOM
}
