package com.github.auties00.cobalt.wam.event;

import com.github.auties00.cobalt.wam.annotation.WamEvent;
import com.github.auties00.cobalt.wam.annotation.WamProperty;
import com.github.auties00.cobalt.wam.type.WamType;
import com.github.auties00.cobalt.wam.type.ContactSyncSource;

import java.time.Instant;
import java.util.Optional;
import java.util.OptionalInt;

@WamEvent(id = 1006, betaWeight = 20, releaseWeight = 100)
public interface ContactSyncEventEvent extends WamEventSpec {
    @WamProperty(index = 20, type = WamType.INTEGER)
    OptionalInt contactSyncBusinessResponseNew();

    @WamProperty(index = 10, type = WamType.INTEGER)
    OptionalInt contactSyncChangedVersionRowCount();

    @WamProperty(index = 26, type = WamType.INTEGER)
    OptionalInt contactSyncConsecutiveCount();

    @WamProperty(index = 19, type = WamType.INTEGER)
    OptionalInt contactSyncDeviceResponseNew();

    @WamProperty(index = 22, type = WamType.INTEGER)
    OptionalInt contactSyncDisappearingModeResponseNew();

    @WamProperty(index = 23, type = WamType.TIMER)
    Optional<Instant> contactSyncEndTimestamp();

    @WamProperty(index = 14, type = WamType.INTEGER)
    OptionalInt contactSyncErrorCode();

    @WamProperty(index = 16, type = WamType.INTEGER)
    OptionalInt contactSyncFailureProtocol();

    @WamProperty(index = 17, type = WamType.INTEGER)
    OptionalInt contactSyncLatency();

    @WamProperty(index = 12, type = WamType.BOOLEAN)
    Optional<Boolean> contactSyncNoop();

    @WamProperty(index = 21, type = WamType.INTEGER)
    OptionalInt contactSyncPayResponseNew();

    @WamProperty(index = 6, type = WamType.BOOLEAN)
    Optional<Boolean> contactSyncRequestClearWaSyncData();

    @WamProperty(index = 5, type = WamType.BOOLEAN)
    Optional<Boolean> contactSyncRequestIsUrgent();

    @WamProperty(index = 28, type = WamType.INTEGER)
    OptionalInt contactSyncRequestOrigin();

    @WamProperty(index = 27, type = WamType.INTEGER)
    OptionalInt contactSyncRequestPreparationLatency();

    @WamProperty(index = 15, type = WamType.INTEGER)
    OptionalInt contactSyncRequestProtocol();

    @WamProperty(index = 7, type = WamType.INTEGER)
    OptionalInt contactSyncRequestRetryCount();

    @WamProperty(index = 8, type = WamType.BOOLEAN)
    Optional<Boolean> contactSyncRequestShouldRetry();

    @WamProperty(index = 11, type = WamType.INTEGER)
    OptionalInt contactSyncRequestedCount();

    @WamProperty(index = 13, type = WamType.INTEGER)
    OptionalInt contactSyncResponseCount();

    @WamProperty(index = 30, type = WamType.INTEGER)
    OptionalInt contactSyncSidelistRequestedCount();

    @WamProperty(index = 31, type = WamType.INTEGER)
    OptionalInt contactSyncSidelistResponseCount();

    @WamProperty(index = 24, type = WamType.ENUM)
    Optional<ContactSyncSource> contactSyncSource();

    @WamProperty(index = 25, type = WamType.TIMER)
    Optional<Instant> contactSyncStartTimestamp();

    @WamProperty(index = 18, type = WamType.INTEGER)
    OptionalInt contactSyncStatusResponseNew();

    @WamProperty(index = 9, type = WamType.BOOLEAN)
    Optional<Boolean> contactSyncSuccess();

    @WamProperty(index = 1, type = WamType.STRING)
    Optional<String> contactSyncType();

    @WamProperty(index = 4, type = WamType.INTEGER)
    OptionalInt contactSyncTypeCode();

    @WamProperty(index = 3, type = WamType.BOOLEAN)
    Optional<Boolean> contactSyncTypeIsBackground();

    @WamProperty(index = 2, type = WamType.BOOLEAN)
    Optional<Boolean> contactSyncTypeIsFull();

    @WamProperty(index = 29, type = WamType.BOOLEAN)
    Optional<Boolean> contactSyncTypeIsMetadata();

    @WamProperty(index = 32, type = WamType.BOOLEAN)
    Optional<Boolean> contactSyncTypeIsSnapshot();
}
