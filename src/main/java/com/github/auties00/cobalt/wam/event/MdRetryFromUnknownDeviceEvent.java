package com.github.auties00.cobalt.wam.event;

import com.github.auties00.cobalt.wam.annotation.WamEvent;
import com.github.auties00.cobalt.wam.annotation.WamProperty;
import com.github.auties00.cobalt.wam.type.WamType;
import com.github.auties00.cobalt.wam.type.DeviceType;

import java.util.Optional;

@WamEvent(id = 2178)
public interface MdRetryFromUnknownDeviceEvent extends WamEventSpec {
    @WamProperty(index = 2, type = WamType.BOOLEAN)
    Optional<Boolean> offline();

    @WamProperty(index = 1, type = WamType.ENUM)
    Optional<DeviceType> senderType();
}
