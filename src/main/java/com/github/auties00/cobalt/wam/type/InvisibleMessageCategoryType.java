package com.github.auties00.cobalt.wam.type;

import com.github.auties00.cobalt.wam.annotation.WamEnum;
import com.github.auties00.cobalt.wam.annotation.WamEnumConstant;

@WamEnum
public enum InvisibleMessageCategoryType {
    @WamEnumConstant(1) PEER,
    @WamEnumConstant(2) INVISIBLE_KEY_DISTRIBUTION,
    @WamEnumConstant(3) OTHER,
    @WamEnumConstant(4) MEDIA_EXPRESS_NOTIFY,
    @WamEnumConstant(5) EPHEMERAL_SYNC_RESPONSE,
    @WamEnumConstant(6) GROUP_MEMBER_TAG
}
