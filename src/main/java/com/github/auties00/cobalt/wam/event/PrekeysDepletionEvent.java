package com.github.auties00.cobalt.wam.event;

import com.github.auties00.cobalt.wam.annotation.WamEvent;
import com.github.auties00.cobalt.wam.annotation.WamProperty;
import com.github.auties00.cobalt.wam.type.WamType;
import com.github.auties00.cobalt.wam.type.MessageType;
import com.github.auties00.cobalt.wam.type.PrekeysFetchContext;
import com.github.auties00.cobalt.wam.type.SizeBucket;

import java.util.Optional;

@WamEvent(id = 3014, betaWeight = 20, releaseWeight = 20)
public interface PrekeysDepletionEvent extends WamEventSpec {
    @WamProperty(index = 3, type = WamType.ENUM)
    Optional<SizeBucket> deviceSizeBucket();

    @WamProperty(index = 2, type = WamType.ENUM)
    Optional<MessageType> messageType();

    @WamProperty(index = 1, type = WamType.ENUM)
    Optional<PrekeysFetchContext> prekeysFetchReason();
}
