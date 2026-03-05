package com.github.auties00.cobalt.wam.event;

import com.github.auties00.cobalt.wam.annotation.WamEvent;
import com.github.auties00.cobalt.wam.annotation.WamProperty;
import com.github.auties00.cobalt.wam.type.WamType;
import com.github.auties00.cobalt.wam.type.GifSearchProvider;

import java.util.Optional;

@WamEvent(id = 1128)
public interface GifSearchNoResultsEvent extends WamEventSpec {
    @WamProperty(index = 1, type = WamType.ENUM)
    Optional<GifSearchProvider> gifSearchProvider();

    @WamProperty(index = 3, type = WamType.STRING)
    Optional<String> inputLanguageCode();

    @WamProperty(index = 2, type = WamType.STRING)
    Optional<String> languageCode();
}
