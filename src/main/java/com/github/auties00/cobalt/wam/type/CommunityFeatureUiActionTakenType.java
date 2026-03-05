package com.github.auties00.cobalt.wam.type;

import com.github.auties00.cobalt.wam.annotation.WamEnum;
import com.github.auties00.cobalt.wam.annotation.WamEnumConstant;

@WamEnum
public enum CommunityFeatureUiActionTakenType {
    @WamEnumConstant(1) ENTRY,
    @WamEnumConstant(2) GROUP_NAV,
    @WamEnumConstant(3) GROUP_ADD,
    @WamEnumConstant(4) COMMUNITY_NAV
}
