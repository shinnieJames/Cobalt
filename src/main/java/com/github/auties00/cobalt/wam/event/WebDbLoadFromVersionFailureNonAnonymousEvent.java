package com.github.auties00.cobalt.wam.event;

import com.github.auties00.cobalt.wam.annotation.WamEvent;
import com.github.auties00.cobalt.wam.annotation.WamProperty;
import com.github.auties00.cobalt.wam.type.WamType;
import com.github.auties00.cobalt.wam.type.WebDbLoaderType;
import com.github.auties00.cobalt.wam.type.WebDbNameType;

import java.util.Optional;

@WamEvent(id = 4814)
public interface WebDbLoadFromVersionFailureNonAnonymousEvent extends WamEventSpec {
    @WamProperty(index = 1, type = WamType.ENUM)
    Optional<WebDbLoaderType> webDbLoader();

    @WamProperty(index = 2, type = WamType.ENUM)
    Optional<WebDbNameType> webDbName();
}
