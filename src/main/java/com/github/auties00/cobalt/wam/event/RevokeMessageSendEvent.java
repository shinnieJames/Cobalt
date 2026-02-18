package com.github.auties00.cobalt.wam.event;

import com.github.auties00.cobalt.wam.annotation.WamEvent;
import com.github.auties00.cobalt.wam.annotation.WamProperty;
import com.github.auties00.cobalt.wam.type.WamType;
import com.github.auties00.cobalt.wam.type.MessageType;
import com.github.auties00.cobalt.wam.type.RevokeType;

import java.util.Optional;
import java.util.OptionalInt;

@WamEvent(id = 3656)
public interface RevokeMessageSendEvent extends WamEventSpec {
    @WamProperty(index = 1, type = WamType.BOOLEAN)
    Optional<Boolean> messageSendResultIsTerminal();

    @WamProperty(index = 2, type = WamType.ENUM)
    Optional<MessageType> messageType();

    @WamProperty(index = 3, type = WamType.INTEGER)
    OptionalInt resendCount();

    @WamProperty(index = 4, type = WamType.INTEGER)
    OptionalInt retryCount();

    @WamProperty(index = 5, type = WamType.INTEGER)
    OptionalInt revokeDuration();

    @WamProperty(index = 6, type = WamType.ENUM)
    Optional<RevokeType> revokeType();
}
