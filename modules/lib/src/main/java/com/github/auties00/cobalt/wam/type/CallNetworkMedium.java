package com.github.auties00.cobalt.wam.type;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;

import com.github.auties00.cobalt.wam.annotation.WamEnum;
import com.github.auties00.cobalt.wam.annotation.WamEnumConstant;

@WhatsAppWebModule(moduleName = "WAWebWamEnumCallNetworkMedium")
@WamEnum
public enum CallNetworkMedium {
    @WamEnumConstant(1) CELLULAR,
    @WamEnumConstant(2) WIFI,
    @WamEnumConstant(3) NONE
}
