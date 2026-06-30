package com.github.auties00.cobalt.wam.type;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;

import com.github.auties00.cobalt.wam.annotation.WamEnum;
import com.github.auties00.cobalt.wam.annotation.WamEnumConstant;

@WhatsAppWebModule(moduleName = "WAWebWamEnumCallLinkType")
@WamEnum
public enum CallLinkType {
    @WamEnumConstant(1) STANDARD,
    @WamEnumConstant(2) EVENT
}
