package com.github.auties00.cobalt.wam.type;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;

import com.github.auties00.cobalt.wam.annotation.WamEnum;
import com.github.auties00.cobalt.wam.annotation.WamEnumConstant;

@WhatsAppWebModule(moduleName = "WAWebWamEnumSmbPerCustomerDataSharingControlEntryPoint")
@WamEnum
public enum SmbPerCustomerDataSharingControlEntryPoint {
    @WamEnumConstant(0) THREAD_ENTRY,
    @WamEnumConstant(1) THREAD_CREATION,
    @WamEnumConstant(2) SYSTEM_MESSAGE,
    @WamEnumConstant(3) CONTACT_INFO_CARD,
    @WamEnumConstant(4) SYNCD_MUTATION
}
