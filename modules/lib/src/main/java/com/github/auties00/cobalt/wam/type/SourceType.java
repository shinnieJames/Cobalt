package com.github.auties00.cobalt.wam.type;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;

import com.github.auties00.cobalt.wam.annotation.WamEnum;
import com.github.auties00.cobalt.wam.annotation.WamEnumConstant;

@WhatsAppWebModule(moduleName = "WAWebWamEnumSourceType")
@WamEnum
public enum SourceType {
    @WamEnumConstant(1) NAVBAR,
    @WamEnumConstant(2) SETTINGS,
    @WamEnumConstant(3) CONTEXTUAL
}
