package com.github.auties00.cobalt.wam.type;

import com.github.auties00.cobalt.wam.annotation.WamEnum;
import com.github.auties00.cobalt.wam.annotation.WamEnumConstant;

@WamEnum
public enum NoticeType {
    @WamEnumConstant(0) LEGACY_USER_NOTICE,
    @WamEnumConstant(1) BADGED_USER_NOTICE,
    @WamEnumConstant(2) PDFN_DISCLOSURE
}
