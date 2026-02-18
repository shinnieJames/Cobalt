package com.github.auties00.cobalt.wam.event;

import com.github.auties00.cobalt.wam.annotation.WamEvent;
import com.github.auties00.cobalt.wam.annotation.WamProperty;
import com.github.auties00.cobalt.wam.type.WamType;
import com.github.auties00.cobalt.wam.type.OfflineResumeModes;
import com.github.auties00.cobalt.wam.type.OfflineResumeStages;

import java.time.Instant;
import java.util.Optional;
import java.util.OptionalInt;

@WamEvent(id = 3536)
public interface OfflineResumeStageEvent extends WamEventSpec {
    @WamProperty(index = 13, type = WamType.INTEGER)
    OptionalInt attemptId();

    @WamProperty(index = 4, type = WamType.INTEGER)
    OptionalInt chatThreadCount();

    @WamProperty(index = 1, type = WamType.ENUM)
    Optional<OfflineResumeStages> currentOfflineStage();

    @WamProperty(index = 5, type = WamType.BOOLEAN)
    Optional<Boolean> isResumeInForeground();

    @WamProperty(index = 14, type = WamType.BOOLEAN)
    Optional<Boolean> isResumeStartedInForeground();

    @WamProperty(index = 12, type = WamType.INTEGER)
    OptionalInt lastPushTimestampMs();

    @WamProperty(index = 6, type = WamType.INTEGER)
    OptionalInt mailboxAge();

    @WamProperty(index = 15, type = WamType.INTEGER)
    OptionalInt offlineCallCount();

    @WamProperty(index = 7, type = WamType.INTEGER)
    OptionalInt offlineDecryptErrorCount();

    @WamProperty(index = 8, type = WamType.INTEGER)
    OptionalInt offlineMessageCount();

    @WamProperty(index = 9, type = WamType.INTEGER)
    OptionalInt offlineNotificationCount();

    @WamProperty(index = 10, type = WamType.INTEGER)
    OptionalInt offlineReceiptCount();

    @WamProperty(index = 11, type = WamType.ENUM)
    Optional<OfflineResumeModes> offlineResumeMode();

    @WamProperty(index = 2, type = WamType.STRING)
    Optional<String> offlineSessionId();

    @WamProperty(index = 16, type = WamType.INTEGER)
    OptionalInt offlineSizeBytes();

    @WamProperty(index = 3, type = WamType.INTEGER)
    OptionalInt offlineStageTimestampMs();

    @WamProperty(index = 17, type = WamType.TIMER)
    Optional<Instant> passiveModeT();
}
