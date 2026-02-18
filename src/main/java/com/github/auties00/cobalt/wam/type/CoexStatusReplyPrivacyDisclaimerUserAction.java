package com.github.auties00.cobalt.wam.type;

import com.github.auties00.cobalt.wam.annotation.WamEnum;
import com.github.auties00.cobalt.wam.annotation.WamEnumConstant;

@WamEnum
public enum CoexStatusReplyPrivacyDisclaimerUserAction {
    @WamEnumConstant(1) DISPLAYED,
    @WamEnumConstant(2) TAPPED
}
