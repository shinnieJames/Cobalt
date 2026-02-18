package com.github.auties00.cobalt.wam.event;

import com.github.auties00.cobalt.wam.annotation.WamEvent;
import com.github.auties00.cobalt.wam.annotation.WamProperty;
import com.github.auties00.cobalt.wam.type.WamType;
import com.github.auties00.cobalt.wam.type.WebcAssetCacheTypeCode;

import java.time.Instant;
import java.util.Optional;
import java.util.OptionalDouble;

@WamEvent(id = 1358)
public interface WebcAssetLoadEvent extends WamEventSpec {
    @WamProperty(index = 4, type = WamType.ENUM)
    Optional<WebcAssetCacheTypeCode> webcAssetCacheType();

    @WamProperty(index = 2, type = WamType.BOOLEAN)
    Optional<Boolean> webcAssetFromCache();

    @WamProperty(index = 3, type = WamType.TIMER)
    Optional<Instant> webcAssetLoadT();

    @WamProperty(index = 1, type = WamType.STRING)
    Optional<String> webcAssetName();

    @WamProperty(index = 5, type = WamType.FLOAT)
    OptionalDouble webcAssetSize();
}
