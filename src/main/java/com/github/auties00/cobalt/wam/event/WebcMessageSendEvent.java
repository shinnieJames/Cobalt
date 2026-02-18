package com.github.auties00.cobalt.wam.event;

import com.github.auties00.cobalt.wam.annotation.WamEvent;
import com.github.auties00.cobalt.wam.annotation.WamProperty;
import com.github.auties00.cobalt.wam.type.WamType;
import com.github.auties00.cobalt.wam.type.MediaType;
import com.github.auties00.cobalt.wam.type.MessageType;

import java.time.Instant;
import java.util.Optional;

@WamEvent(id = 2072)
public interface WebcMessageSendEvent extends WamEventSpec {
    @WamProperty(index = 3, type = WamType.BOOLEAN)
    Optional<Boolean> messageIsForward();

    @WamProperty(index = 2, type = WamType.ENUM)
    Optional<MediaType> messageMediaType();

    @WamProperty(index = 4, type = WamType.TIMER)
    Optional<Instant> messageSendT();

    @WamProperty(index = 1, type = WamType.ENUM)
    Optional<MessageType> messageType();
}
