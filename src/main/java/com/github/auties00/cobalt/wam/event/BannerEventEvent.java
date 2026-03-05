package com.github.auties00.cobalt.wam.event;

import com.github.auties00.cobalt.wam.annotation.WamEvent;
import com.github.auties00.cobalt.wam.annotation.WamProperty;
import com.github.auties00.cobalt.wam.type.WamType;
import com.github.auties00.cobalt.wam.type.BannerOperations;
import com.github.auties00.cobalt.wam.type.BannerTypes;

import java.util.Optional;

@WamEvent(id = 1578)
public interface BannerEventEvent extends WamEventSpec {
    @WamProperty(index = 3, type = WamType.STRING)
    Optional<String> bannerId();

    @WamProperty(index = 2, type = WamType.ENUM)
    Optional<BannerOperations> bannerOperation();

    @WamProperty(index = 1, type = WamType.ENUM)
    Optional<BannerTypes> bannerType();

    @WamProperty(index = 4, type = WamType.STRING)
    Optional<String> deviceId();
}
