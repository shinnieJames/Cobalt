package com.github.auties00.cobalt.wam.type;

import com.github.auties00.cobalt.wam.annotation.WamEnum;
import com.github.auties00.cobalt.wam.annotation.WamEnumConstant;

@WamEnum
public enum ChannelEntryPointApp {
    @WamEnumConstant(1) EXTERNAL_UNKNOWN,
    @WamEnumConstant(2) WHATSAPP
}
