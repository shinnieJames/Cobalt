package com.github.auties00.cobalt.wam.type;

import com.github.auties00.cobalt.wam.annotation.WamEnum;
import com.github.auties00.cobalt.wam.annotation.WamEnumConstant;

@WamEnum
public enum WebcAppcacheStatusCode {
    @WamEnumConstant(0) UNCACHED,
    @WamEnumConstant(1) IDLE,
    @WamEnumConstant(2) CHECKING,
    @WamEnumConstant(3) DOWNLOADING,
    @WamEnumConstant(4) UPDATEREADY,
    @WamEnumConstant(5) OBSOLETE
}
