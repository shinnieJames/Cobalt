package com.github.auties00.cobalt.wam.event;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;

import com.github.auties00.cobalt.wam.annotation.WamEvent;
import com.github.auties00.cobalt.wam.model.WamEventSpec;
import com.github.auties00.cobalt.wam.annotation.WamProperty;
import com.github.auties00.cobalt.wam.model.WamType;
import com.github.auties00.cobalt.wam.type.ClientGroupSizeBucket;
import com.github.auties00.cobalt.wam.type.TypeOfGroupEnum;

import java.util.Optional;
import java.util.OptionalInt;

@WhatsAppWebModule(moduleName = "WAWebMdGroupParticipantMissAckWamEvent")
@WamEvent(id = 4146)
public interface MdGroupParticipantMissAckEvent extends WamEventSpec {
    @WamProperty(index = 1, type = WamType.ENUM)
    Optional<ClientGroupSizeBucket> groupSizeBucket();

    @WamProperty(index = 2, type = WamType.BOOLEAN)
    Optional<Boolean> isLid();

    @WamProperty(index = 3, type = WamType.BOOLEAN)
    Optional<Boolean> messageIsRevoke();

    @WamProperty(index = 4, type = WamType.INTEGER)
    OptionalInt participantAddCount();

    @WamProperty(index = 5, type = WamType.INTEGER)
    OptionalInt participantRemoveCount();

    @WamProperty(index = 6, type = WamType.ENUM)
    Optional<TypeOfGroupEnum> typeOfGroup();
}
