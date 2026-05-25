package com.github.auties00.cobalt.wam.event;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;

import com.github.auties00.cobalt.wam.annotation.WamEvent;
import com.github.auties00.cobalt.wam.model.WamEventSpec;
import com.github.auties00.cobalt.wam.annotation.WamProperty;
import com.github.auties00.cobalt.wam.model.WamType;
import com.github.auties00.cobalt.wam.type.CallSizeBucket;
import com.github.auties00.cobalt.wam.type.CallSizeType;
import com.github.auties00.cobalt.wam.type.CallType;
import com.github.auties00.cobalt.wam.type.ParticipantActionSource;
import com.github.auties00.cobalt.wam.type.PreCallActionType;

import java.util.Optional;
import java.util.OptionalInt;

@WhatsAppWebModule(moduleName = "WAWebCallInfoUserJourneyWamEvent")
@WamEvent(id = 6034)
public interface CallInfoUserJourneyEvent extends WamEventSpec {
    @WamProperty(index = 1, type = WamType.STRING)
    Optional<String> appSessionId();

    @WamProperty(index = 2, type = WamType.ENUM)
    Optional<CallSizeBucket> callGroupSizeBucket();

    @WamProperty(index = 3, type = WamType.ENUM)
    Optional<CallSizeType> callSizeType();

    @WamProperty(index = 4, type = WamType.ENUM)
    Optional<CallType> callType();

    @WamProperty(index = 9, type = WamType.INTEGER)
    OptionalInt numParticipantsShown();

    @WamProperty(index = 10, type = WamType.ENUM)
    Optional<ParticipantActionSource> participantActionSource();

    @WamProperty(index = 5, type = WamType.ENUM)
    Optional<PreCallActionType> preCallActionType();

    @WamProperty(index = 6, type = WamType.STRING)
    Optional<String> surfaceSessionId();

    @WamProperty(index = 7, type = WamType.INTEGER)
    OptionalInt userJourneyEventMs();

    @WamProperty(index = 8, type = WamType.STRING)
    Optional<String> userJourneyFunnelId();
}
