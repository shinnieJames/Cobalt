package com.github.auties00.cobalt.wam.type;

import com.github.auties00.cobalt.wam.annotation.WamEnum;
import com.github.auties00.cobalt.wam.annotation.WamEnumConstant;

@WamEnum
public enum GuestUpsellEntryPointType {
    @WamEnumConstant(1) BANNER_DOWNLOAD_CTA,
    @WamEnumConstant(2) AUDIO_CALL,
    @WamEnumConstant(3) VIDEO_CALL,
    @WamEnumConstant(4) ATTACHMENT,
    @WamEnumConstant(5) NEW_INVITE,
    @WamEnumConstant(6) LANDING_SCREEN_DOWNLOAD_CTA,
    @WamEnumConstant(7) SESSION_REOPEN
}
