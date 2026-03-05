package com.github.auties00.cobalt.wam.type;

import com.github.auties00.cobalt.wam.annotation.WamEnum;
import com.github.auties00.cobalt.wam.annotation.WamEnumConstant;

@WamEnum
public enum NativeContactsNuxEventType {
    @WamEnumConstant(1) VIEW_NATIVE_CONTACTS_NUX,
    @WamEnumConstant(2) VIEW_MANAGE_CONTACTS_FROM_COMPANION,
    @WamEnumConstant(3) VIEW_MANAGE_CONTACTS_FROM_COMPANION_NATIVE_CONTACTS_SETTING_OFF
}
