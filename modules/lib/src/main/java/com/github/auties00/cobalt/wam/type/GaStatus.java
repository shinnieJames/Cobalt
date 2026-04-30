package com.github.auties00.cobalt.wam.type;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;

import com.github.auties00.cobalt.wam.annotation.WamEnum;
import com.github.auties00.cobalt.wam.annotation.WamEnumConstant;

@WhatsAppWebModule(moduleName = "WAWebWamEnumGaStatus")
@WamEnum
public enum GaStatus {
    @WamEnumConstant(0) NEW,
    @WamEnumConstant(1) RETAINED,
    @WamEnumConstant(2) RESURRECTED
}
