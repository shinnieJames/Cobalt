package com.github.auties00.cobalt.wam.event;

import com.github.auties00.cobalt.wam.annotation.WamEvent;
import com.github.auties00.cobalt.wam.annotation.WamProperty;
import com.github.auties00.cobalt.wam.type.WamType;
import com.github.auties00.cobalt.wam.type.WebcRuntimeEnvCode;
import com.github.auties00.cobalt.wam.type.WebcScenarioType;

import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;

@WamEvent(id = 1188, releaseWeight = 10)
public interface WebcMemoryStatEvent extends WamEventSpec {
    @WamProperty(index = 26, type = WamType.STRING)
    Optional<String> appContext();

    @WamProperty(index = 27, type = WamType.INTEGER)
    OptionalInt appContextBitfield();

    @WamProperty(index = 17, type = WamType.INTEGER)
    OptionalInt chatCollectionSize();

    @WamProperty(index = 18, type = WamType.INTEGER)
    OptionalInt chatDbSize();

    @WamProperty(index = 19, type = WamType.INTEGER)
    OptionalInt contactCollectionSize();

    @WamProperty(index = 20, type = WamType.INTEGER)
    OptionalInt contactDbSize();

    @WamProperty(index = 13, type = WamType.BOOLEAN)
    Optional<Boolean> isForeground();

    @WamProperty(index = 9, type = WamType.INTEGER)
    OptionalInt jsHeapSizeLimit();

    @WamProperty(index = 24, type = WamType.INTEGER)
    OptionalInt messageCollectionSize();

    @WamProperty(index = 25, type = WamType.INTEGER)
    OptionalInt messageDbSize();

    @WamProperty(index = 14, type = WamType.INTEGER)
    OptionalInt peakUsedJsHeapSize();

    @WamProperty(index = 15, type = WamType.ENUM)
    Optional<WebcScenarioType> scenario();

    @WamProperty(index = 10, type = WamType.INTEGER)
    OptionalInt totalJsHeapSize();

    @WamProperty(index = 6, type = WamType.FLOAT)
    OptionalDouble uptime();

    @WamProperty(index = 11, type = WamType.INTEGER)
    OptionalInt usedJsHeapSize();

    @WamProperty(index = 16, type = WamType.INTEGER)
    OptionalInt usedJsHeapSizeDelta();

    @WamProperty(index = 23, type = WamType.ENUM)
    Optional<WebcRuntimeEnvCode> webcRuntimeEnv();
}
