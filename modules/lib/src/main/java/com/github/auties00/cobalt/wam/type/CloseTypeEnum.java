package com.github.auties00.cobalt.wam.type;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;

import com.github.auties00.cobalt.wam.annotation.WamEnum;
import com.github.auties00.cobalt.wam.annotation.WamEnumConstant;

@WhatsAppWebModule(moduleName = "WAWebWamEnumCloseTypeEnum")
@WamEnum
public enum CloseTypeEnum {
    @WamEnumConstant(1) SUBMITTED,
    @WamEnumConstant(2) CLOSED
}
