package com.github.auties00.cobalt.wam.type;

import com.github.auties00.cobalt.wam.annotation.WamEnum;
import com.github.auties00.cobalt.wam.annotation.WamEnumConstant;

@WamEnum
public enum LoginResultType {
    @WamEnumConstant(1) OK,
    @WamEnumConstant(2) ERROR_UNKNOWN,
    @WamEnumConstant(3) SERVER_ERROR,
    @WamEnumConstant(4) SERVER_GOAWAY,
    @WamEnumConstant(5) NETWORK_ERROR,
    @WamEnumConstant(6) ANDROID_KEYSTORE_ERROR,
    @WamEnumConstant(7) CERTIFICATE_ERROR
}
