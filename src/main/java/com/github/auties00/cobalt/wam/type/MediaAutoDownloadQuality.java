package com.github.auties00.cobalt.wam.type;

import com.github.auties00.cobalt.wam.annotation.WamEnum;
import com.github.auties00.cobalt.wam.annotation.WamEnumConstant;

@WamEnum
public enum MediaAutoDownloadQuality {
    @WamEnumConstant(0) AUTO,
    @WamEnumConstant(1) SD_QUALITY,
    @WamEnumConstant(2) HD_QUALITY
}
