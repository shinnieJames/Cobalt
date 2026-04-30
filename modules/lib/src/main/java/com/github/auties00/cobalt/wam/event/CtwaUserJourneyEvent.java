package com.github.auties00.cobalt.wam.event;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;

import com.github.auties00.cobalt.wam.annotation.WamEvent;
import com.github.auties00.cobalt.wam.model.WamEventSpec;
import com.github.auties00.cobalt.wam.annotation.WamProperty;
import com.github.auties00.cobalt.wam.model.WamChannel;
import com.github.auties00.cobalt.wam.model.WamType;
import com.github.auties00.cobalt.wam.type.CtwaAdVariantType;
import com.github.auties00.cobalt.wam.type.CtwaChatCreationMode;
import com.github.auties00.cobalt.wam.type.CtwaUserJourneyOperationType;
import com.github.auties00.cobalt.wam.type.TrustBannerAction;

import java.util.Optional;
import java.util.OptionalInt;

@WhatsAppWebModule(moduleName = "WAWebCtwaUserJourneyWamEvent")
@WamEvent(id = 3466, channel = WamChannel.PRIVATE, privateStatsId = 113760892)
public interface CtwaUserJourneyEvent extends WamEventSpec {
    @WamProperty(index = 1, type = WamType.STRING)
    Optional<String> adId();

    @WamProperty(index = 2, type = WamType.STRING)
    Optional<String> businessJid();

    @WamProperty(index = 14, type = WamType.ENUM)
    Optional<CtwaAdVariantType> ctwaAdVariant();

    @WamProperty(index = 6, type = WamType.ENUM)
    Optional<CtwaChatCreationMode> ctwaChatCreationMode();

    @WamProperty(index = 15, type = WamType.STRING)
    Optional<String> ctwaEventReason();

    @WamProperty(index = 12, type = WamType.STRING)
    Optional<String> ctwaUserJourneyMetadata();

    @WamProperty(index = 3, type = WamType.ENUM)
    Optional<CtwaUserJourneyOperationType> ctwaUserJourneyOperation();

    @WamProperty(index = 13, type = WamType.INTEGER)
    OptionalInt elapsedTimeInMs();

    @WamProperty(index = 9, type = WamType.STRING)
    Optional<String> featureEnabled();

    @WamProperty(index = 4, type = WamType.BOOLEAN)
    Optional<Boolean> icebreakersShown();

    @WamProperty(index = 5, type = WamType.INTEGER)
    OptionalInt sequenceNumber();

    @WamProperty(index = 10, type = WamType.STRING)
    Optional<String> threadCreationDate();

    @WamProperty(index = 16, type = WamType.INTEGER)
    OptionalInt threadEntryCount();

    @WamProperty(index = 11, type = WamType.STRING)
    Optional<String> threadIdHmac();

    @WamProperty(index = 7, type = WamType.ENUM)
    Optional<TrustBannerAction> trustBannerAction();

    @WamProperty(index = 8, type = WamType.STRING)
    Optional<String> trustBannerType();

    @WamProperty(index = 17, type = WamType.INTEGER)
    OptionalInt usyncMode();
}
