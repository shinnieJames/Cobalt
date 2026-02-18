package com.github.auties00.cobalt.wam.event;

import com.github.auties00.cobalt.wam.annotation.WamEvent;
import com.github.auties00.cobalt.wam.annotation.WamProperty;
import com.github.auties00.cobalt.wam.type.WamType;
import com.github.auties00.cobalt.wam.type.DisclosureEventType;
import com.github.auties00.cobalt.wam.type.DisclosureInteraction;
import com.github.auties00.cobalt.wam.type.DisclosureSource;
import com.github.auties00.cobalt.wam.type.DisclosureSuppressionReason;
import com.github.auties00.cobalt.wam.type.DisclosureSurface;

import java.util.Optional;
import java.util.OptionalInt;

@WamEvent(id = 6796)
public interface MmDisclosureStateFsEventEvent extends WamEventSpec {
    @WamProperty(index = 1, type = WamType.ENUM)
    Optional<DisclosureEventType> disclosureEventType();

    @WamProperty(index = 2, type = WamType.ENUM)
    Optional<DisclosureInteraction> disclosureInteraction();

    @WamProperty(index = 3, type = WamType.ENUM)
    Optional<DisclosureSource> disclosureSource();

    @WamProperty(index = 4, type = WamType.ENUM)
    Optional<DisclosureSuppressionReason> disclosureSuppressionReason();

    @WamProperty(index = 5, type = WamType.ENUM)
    Optional<DisclosureSurface> disclosureSurface();

    @WamProperty(index = 6, type = WamType.BOOLEAN)
    Optional<Boolean> isCompanionDevice();

    @WamProperty(index = 7, type = WamType.BOOLEAN)
    Optional<Boolean> isUserDisclosed();

    @WamProperty(index = 12, type = WamType.INTEGER)
    OptionalInt mmDisclosureFlags();

    @WamProperty(index = 8, type = WamType.BOOLEAN)
    Optional<Boolean> mmHasDisclosedUrl();

    @WamProperty(index = 9, type = WamType.BOOLEAN)
    Optional<Boolean> mmHasShowDisclosureFlag();

    @WamProperty(index = 10, type = WamType.STRING)
    Optional<String> threadIdHmac();

    @WamProperty(index = 11, type = WamType.BOOLEAN)
    Optional<Boolean> userBecameDisclosed();
}
