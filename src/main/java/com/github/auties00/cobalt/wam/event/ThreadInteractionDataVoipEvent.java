package com.github.auties00.cobalt.wam.event;

import com.github.auties00.cobalt.wam.annotation.WamEvent;
import com.github.auties00.cobalt.wam.annotation.WamProperty;
import com.github.auties00.cobalt.wam.type.WamType;

import java.util.Optional;
import java.util.OptionalInt;

@WamEvent(id = 6362)
public interface ThreadInteractionDataVoipEvent extends WamEventSpec {
    @WamProperty(index = 1, type = WamType.INTEGER)
    OptionalInt callOffersReceived();

    @WamProperty(index = 2, type = WamType.INTEGER)
    OptionalInt callOffersSent();

    @WamProperty(index = 3, type = WamType.INTEGER)
    OptionalInt callsResultBusy();

    @WamProperty(index = 4, type = WamType.INTEGER)
    OptionalInt callsResultCancelled();

    @WamProperty(index = 5, type = WamType.INTEGER)
    OptionalInt callsResultConnected();

    @WamProperty(index = 6, type = WamType.INTEGER)
    OptionalInt callsResultError();

    @WamProperty(index = 7, type = WamType.INTEGER)
    OptionalInt callsResultMissed();

    @WamProperty(index = 8, type = WamType.INTEGER)
    OptionalInt callsResultRejected();

    @WamProperty(index = 16, type = WamType.STRING)
    Optional<String> threadCreationDate();

    @WamProperty(index = 9, type = WamType.STRING)
    Optional<String> threadDs();

    @WamProperty(index = 14, type = WamType.STRING)
    Optional<String> threadId();

    @WamProperty(index = 15, type = WamType.STRING)
    Optional<String> threadIdByLid();

    @WamProperty(index = 11, type = WamType.INTEGER)
    OptionalInt totalCallDuration();

    @WamProperty(index = 12, type = WamType.INTEGER)
    OptionalInt videoCallsOffered();

    @WamProperty(index = 13, type = WamType.INTEGER)
    OptionalInt voiceCallsOffered();
}
