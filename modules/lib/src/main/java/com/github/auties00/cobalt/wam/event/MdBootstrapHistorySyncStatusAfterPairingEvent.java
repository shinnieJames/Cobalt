package com.github.auties00.cobalt.wam.event;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;

import com.github.auties00.cobalt.wam.annotation.WamEvent;
import com.github.auties00.cobalt.wam.model.WamEventSpec;
import com.github.auties00.cobalt.wam.annotation.WamProperty;
import com.github.auties00.cobalt.wam.model.WamType;
import com.github.auties00.cobalt.wam.type.ActiveTimeAfterPairing;
import com.github.auties00.cobalt.wam.type.MdBootstrapHistoryPayloadType;
import com.github.auties00.cobalt.wam.type.MdHistorySyncStatusResult;

import java.util.Optional;
import java.util.OptionalInt;

@WhatsAppWebModule(moduleName = "WAWebMdBootstrapHistorySyncStatusAfterPairingWamEvent")
@WamEvent(id = 4652)
public interface MdBootstrapHistorySyncStatusAfterPairingEvent extends WamEventSpec {
    @WamProperty(index = 1, type = WamType.ENUM)
    Optional<ActiveTimeAfterPairing> activeTimeAfterPairing();

    @WamProperty(index = 12, type = WamType.BOOLEAN)
    Optional<Boolean> isLoopRunning();

    @WamProperty(index = 2, type = WamType.INTEGER)
    OptionalInt lastProcessedNotificationChunkOrder();

    @WamProperty(index = 3, type = WamType.INTEGER)
    OptionalInt lastProcessedNotificationChunkProgress();

    @WamProperty(index = 4, type = WamType.ENUM)
    Optional<MdBootstrapHistoryPayloadType> mdBootstrapHistoryPayloadType();

    @WamProperty(index = 5, type = WamType.ENUM)
    Optional<MdHistorySyncStatusResult> mdHistorySyncStatusResult();

    @WamProperty(index = 6, type = WamType.STRING)
    Optional<String> mdSessionId();

    @WamProperty(index = 7, type = WamType.INTEGER)
    OptionalInt mdTimestamp();

    @WamProperty(index = 8, type = WamType.INTEGER)
    OptionalInt missingNotificationCount();

    @WamProperty(index = 9, type = WamType.INTEGER)
    OptionalInt nextNotificationChunkOrder();

    @WamProperty(index = 10, type = WamType.INTEGER)
    OptionalInt totalProcessedMessageCount();

    @WamProperty(index = 11, type = WamType.INTEGER)
    OptionalInt unprocessedNotificationCount();
}
