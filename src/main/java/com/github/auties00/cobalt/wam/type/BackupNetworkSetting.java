package com.github.auties00.cobalt.wam.type;

import com.github.auties00.cobalt.wam.annotation.WamEnum;
import com.github.auties00.cobalt.wam.annotation.WamEnumConstant;

@WamEnum
public enum BackupNetworkSetting {
    @WamEnumConstant(0) WIFI_ONLY,
    @WamEnumConstant(1) WIFI_OR_CELLULAR
}
