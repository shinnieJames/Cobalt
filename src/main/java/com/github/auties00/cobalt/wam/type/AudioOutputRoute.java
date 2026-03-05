package com.github.auties00.cobalt.wam.type;

import com.github.auties00.cobalt.wam.annotation.WamEnum;
import com.github.auties00.cobalt.wam.annotation.WamEnumConstant;

@WamEnum
public enum AudioOutputRoute {
    @WamEnumConstant(0) DEFAULT,
    @WamEnumConstant(1) SPEAKER,
    @WamEnumConstant(2) EARPIECE,
    @WamEnumConstant(3) BLUETOOTH,
    @WamEnumConstant(4) HEADSET,
    @WamEnumConstant(5) CARPLAY
}
