package com.github.auties00.cobalt.wam.type;

import com.github.auties00.cobalt.wam.annotation.WamEnum;
import com.github.auties00.cobalt.wam.annotation.WamEnumConstant;

@WamEnum
public enum BotBizEntryPoint {
    @WamEnumConstant(1) SHARED_BOT_BIZ_CARD,
    @WamEnumConstant(2) SHARED_BOT_BIZ_DEEPLINK,
    @WamEnumConstant(3) BOT_BIZ_CHAT
}
