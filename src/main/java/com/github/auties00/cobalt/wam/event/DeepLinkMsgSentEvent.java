package com.github.auties00.cobalt.wam.event;

import com.github.auties00.cobalt.wam.annotation.WamEvent;
import com.github.auties00.cobalt.wam.annotation.WamProperty;
import com.github.auties00.cobalt.wam.type.WamType;
import com.github.auties00.cobalt.wam.type.DeepLinkAction;

import java.util.Optional;

@WamEvent(id = 3198)
public interface DeepLinkMsgSentEvent extends WamEventSpec {
    @WamProperty(index = 1, type = WamType.ENUM)
    Optional<DeepLinkAction> deepLinkAction();

    @WamProperty(index = 2, type = WamType.STRING)
    Optional<String> deepLinkSessionId();
}
