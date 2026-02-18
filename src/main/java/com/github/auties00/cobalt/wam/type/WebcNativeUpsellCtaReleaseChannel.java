package com.github.auties00.cobalt.wam.type;

import com.github.auties00.cobalt.wam.annotation.WamEnum;
import com.github.auties00.cobalt.wam.annotation.WamEnumConstant;

@WamEnum
public enum WebcNativeUpsellCtaReleaseChannel {
    @WamEnumConstant(1) PRODUCTION,
    @WamEnumConstant(2) BETA
}
