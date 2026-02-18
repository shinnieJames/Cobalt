package com.github.auties00.cobalt.wam.event;

import com.github.auties00.cobalt.wam.annotation.WamEvent;
import com.github.auties00.cobalt.wam.annotation.WamProperty;
import com.github.auties00.cobalt.wam.type.WamType;
import com.github.auties00.cobalt.wam.type.PeerDataRequestErrorCode;
import com.github.auties00.cobalt.wam.type.PeerDataRequestType;

import java.util.Optional;
import java.util.OptionalInt;

@WamEvent(id = 3906)
public interface NonMessagePeerDataRequestEvent extends WamEventSpec {
    @WamProperty(index = 1, type = WamType.INTEGER)
    OptionalInt peerDataRequestCount();

    @WamProperty(index = 4, type = WamType.ENUM)
    Optional<PeerDataRequestErrorCode> peerDataRequestErrorCode();

    @WamProperty(index = 2, type = WamType.STRING)
    Optional<String> peerDataRequestSessionId();

    @WamProperty(index = 3, type = WamType.ENUM)
    Optional<PeerDataRequestType> peerDataRequestType();
}
