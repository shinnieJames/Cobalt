package com.github.auties00.cobalt.wam.type;

import com.github.auties00.cobalt.wam.annotation.WamEnum;
import com.github.auties00.cobalt.wam.annotation.WamEnumConstant;

@WamEnum
public enum CompanionAddContactSource {
    @WamEnumConstant(0) CONTACT_INFO,
    @WamEnumConstant(1) VCARD,
    @WamEnumConstant(2) CONTACT_LIST,
    @WamEnumConstant(3) NEW_CHAT,
    @WamEnumConstant(4) PHONE_NUMBER_DIALER
}
