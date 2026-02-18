package com.github.auties00.cobalt.wam.event;

import com.github.auties00.cobalt.wam.annotation.WamEvent;
import com.github.auties00.cobalt.wam.annotation.WamProperty;
import com.github.auties00.cobalt.wam.type.WamType;
import com.github.auties00.cobalt.wam.type.MediaType;
import com.github.auties00.cobalt.wam.type.PsaBlockReason;
import com.github.auties00.cobalt.wam.type.PsaMessageRemoveAction;
import com.github.auties00.cobalt.wam.type.PsaMessageRemoveEntryPoint;
import com.github.auties00.cobalt.wam.type.WaOfficialAccountName;

import java.time.Instant;
import java.util.Optional;

@WamEvent(id = 3582)
public interface ChatPsaRemoveEvent extends WamEventSpec {
    @WamProperty(index = 2, type = WamType.ENUM)
    Optional<MediaType> lastReceivedMediaType();

    @WamProperty(index = 4, type = WamType.TIMER)
    Optional<Instant> lastReceivedMessageTs();

    @WamProperty(index = 8, type = WamType.STRING)
    Optional<String> lastReceivedMsgId();

    @WamProperty(index = 10, type = WamType.ENUM)
    Optional<PsaBlockReason> psaBlockReason();

    @WamProperty(index = 9, type = WamType.STRING)
    Optional<String> psaCampaignId();

    @WamProperty(index = 5, type = WamType.ENUM)
    Optional<PsaMessageRemoveAction> psaMessageRemoveAction();

    @WamProperty(index = 6, type = WamType.ENUM)
    Optional<PsaMessageRemoveEntryPoint> psaMessageRemoveEntryPoint();

    @WamProperty(index = 7, type = WamType.ENUM)
    Optional<WaOfficialAccountName> waOfficialAccountName();
}
