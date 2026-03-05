package com.github.auties00.cobalt.wam.event;

import com.github.auties00.cobalt.wam.annotation.WamEvent;
import com.github.auties00.cobalt.wam.annotation.WamProperty;
import com.github.auties00.cobalt.wam.type.WamType;
import com.github.auties00.cobalt.wam.type.NativeContactsNuxEntryPoint;
import com.github.auties00.cobalt.wam.type.NativeContactsNuxEventType;

import java.util.Optional;

@WamEvent(id = 5788)
public interface NativeContactsNuxEventEvent extends WamEventSpec {
    @WamProperty(index = 3, type = WamType.ENUM)
    Optional<NativeContactsNuxEntryPoint> nativeContactsNuxEntryPoint();

    @WamProperty(index = 1, type = WamType.ENUM)
    Optional<NativeContactsNuxEventType> nativeContactsNuxEventType();
}
