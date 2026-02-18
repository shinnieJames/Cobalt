package com.github.auties00.cobalt.model.device;

import it.auties.protobuf.annotation.ProtobufEnum;
import it.auties.protobuf.annotation.ProtobufEnumIndex;

@ProtobufEnum(name = "DeviceProps.PlatformType")
public enum DevicePlatformType {
    UNKNOWN(0),
    CHROME(1),
    FIREFOX(2),
    IE(3),
    OPERA(4),
    SAFARI(5),
    EDGE(6),
    DESKTOP(7),
    IPAD(8),
    ANDROID_TABLET(9),
    OHANA(10),
    ALOHA(11),
    CATALINA(12),
    TCL_TV(13),
    IOS_PHONE(14),
    IOS_CATALYST(15),
    ANDROID_PHONE(16),
    ANDROID_AMBIGUOUS(17),
    WEAR_OS(18),
    AR_WRIST(19),
    AR_DEVICE(20),
    UWP(21),
    VR(22),
    CLOUD_API(23),
    SMARTGLASSES(24);

    DevicePlatformType(@ProtobufEnumIndex int index) {
        this.index = index;
    }

    final int index;

    public int index() {
        return this.index;
    }
}
