package com.github.auties00.cobalt.wam.event;

import com.github.auties00.cobalt.wam.annotation.WamEvent;
import com.github.auties00.cobalt.wam.annotation.WamProperty;
import com.github.auties00.cobalt.wam.type.WamType;
import com.github.auties00.cobalt.wam.type.OfflineResumeResultType;

import java.time.Instant;
import java.util.Optional;
import java.util.OptionalInt;

@WamEvent(id = 3112)
public interface OfflineResumeEvent extends WamEventSpec {
    @WamProperty(index = 35, type = WamType.BOOLEAN)
    Optional<Boolean> affectedBySleepMode();

    @WamProperty(index = 49, type = WamType.STRING)
    Optional<String> appContext();

    @WamProperty(index = 50, type = WamType.INTEGER)
    OptionalInt appContextBitfield();

    @WamProperty(index = 36, type = WamType.INTEGER)
    OptionalInt attemptNumber();

    @WamProperty(index = 55, type = WamType.INTEGER)
    OptionalInt chatQueueSize();

    @WamProperty(index = 1, type = WamType.INTEGER)
    OptionalInt chatThreadCount();

    @WamProperty(index = 39, type = WamType.TIMER)
    Optional<Instant> dbDurationT();

    @WamProperty(index = 40, type = WamType.TIMER)
    Optional<Instant> dbMainThreadDurationT();

    @WamProperty(index = 41, type = WamType.INTEGER)
    OptionalInt dbMainThreadReadsCount();

    @WamProperty(index = 42, type = WamType.INTEGER)
    OptionalInt dbMainThreadWritesCount();

    @WamProperty(index = 43, type = WamType.INTEGER)
    OptionalInt dbReadsCount();

    @WamProperty(index = 44, type = WamType.INTEGER)
    OptionalInt dbWritesCount();

    @WamProperty(index = 45, type = WamType.BOOLEAN)
    Optional<Boolean> disconnected();

    @WamProperty(index = 56, type = WamType.INTEGER)
    OptionalInt e2eeQueueSize();

    @WamProperty(index = 23, type = WamType.INTEGER)
    OptionalInt expectedOfflineCallCount();

    @WamProperty(index = 17, type = WamType.INTEGER)
    OptionalInt expectedOfflineMessageCount();

    @WamProperty(index = 18, type = WamType.INTEGER)
    OptionalInt expectedOfflineNotificationCount();

    @WamProperty(index = 19, type = WamType.INTEGER)
    OptionalInt expectedOfflineReceiptCount();

    @WamProperty(index = 2, type = WamType.BOOLEAN)
    Optional<Boolean> isOfflineCompleteMissed();

    @WamProperty(index = 13, type = WamType.BOOLEAN)
    Optional<Boolean> isResumeInForeground();

    @WamProperty(index = 37, type = WamType.BOOLEAN)
    Optional<Boolean> isResumeStartedInForeground();

    @WamProperty(index = 22, type = WamType.BOOLEAN)
    Optional<Boolean> isRunningFromServiceExtension();

    @WamProperty(index = 3, type = WamType.TIMER)
    Optional<Instant> lastStanzaT();

    @WamProperty(index = 38, type = WamType.INTEGER)
    OptionalInt logoutSessionId();

    @WamProperty(index = 14, type = WamType.INTEGER)
    OptionalInt mailboxAge();

    @WamProperty(index = 4, type = WamType.TIMER)
    Optional<Instant> mainScreenLoadT();

    @WamProperty(index = 54, type = WamType.TIMER)
    Optional<Instant> nseMergeT();

    @WamProperty(index = 24, type = WamType.INTEGER)
    OptionalInt offlineCallCount();

    @WamProperty(index = 5, type = WamType.INTEGER)
    OptionalInt offlineDecryptErrorCount();

    @WamProperty(index = 6, type = WamType.INTEGER)
    OptionalInt offlineMessageCount();

    @WamProperty(index = 7, type = WamType.INTEGER)
    OptionalInt offlineNotificationCount();

    @WamProperty(index = 8, type = WamType.TIMER)
    Optional<Instant> offlinePreviewT();

    @WamProperty(index = 20, type = WamType.TIMER)
    Optional<Instant> offlineProcessingT();

    @WamProperty(index = 9, type = WamType.INTEGER)
    OptionalInt offlineReceiptCount();

    @WamProperty(index = 21, type = WamType.ENUM)
    Optional<OfflineResumeResultType> offlineResumeResult();

    @WamProperty(index = 46, type = WamType.TIMER)
    Optional<Instant> offlineSessionT();

    @WamProperty(index = 10, type = WamType.INTEGER)
    OptionalInt offlineSizeBytes();

    @WamProperty(index = 15, type = WamType.BOOLEAN)
    Optional<Boolean> onTrickleMode();

    @WamProperty(index = 11, type = WamType.TIMER)
    Optional<Instant> pageLoadT();

    @WamProperty(index = 25, type = WamType.TIMER)
    Optional<Instant> passiveModeT();

    @WamProperty(index = 26, type = WamType.INTEGER)
    OptionalInt preackCallCount();

    @WamProperty(index = 27, type = WamType.INTEGER)
    OptionalInt preackMessageCount();

    @WamProperty(index = 28, type = WamType.INTEGER)
    OptionalInt preackNotificationCount();

    @WamProperty(index = 29, type = WamType.INTEGER)
    OptionalInt preackReceiptCount();

    @WamProperty(index = 47, type = WamType.INTEGER)
    OptionalInt preacksCount();

    @WamProperty(index = 30, type = WamType.INTEGER)
    OptionalInt processedCallCount();

    @WamProperty(index = 31, type = WamType.INTEGER)
    OptionalInt processedMessageCount();

    @WamProperty(index = 32, type = WamType.INTEGER)
    OptionalInt processedNotificationCount();

    @WamProperty(index = 33, type = WamType.INTEGER)
    OptionalInt processedReceiptCount();

    @WamProperty(index = 51, type = WamType.INTEGER)
    OptionalInt queuedMessageCount();

    @WamProperty(index = 52, type = WamType.INTEGER)
    OptionalInt queuedNotificationCount();

    @WamProperty(index = 53, type = WamType.INTEGER)
    OptionalInt queuedReceiptCount();

    @WamProperty(index = 48, type = WamType.STRING)
    Optional<String> runningTasks();

    @WamProperty(index = 12, type = WamType.TIMER)
    Optional<Instant> socketConnectT();

    @WamProperty(index = 34, type = WamType.STRING)
    Optional<String> transientOfflineSessionId();

    @WamProperty(index = 57, type = WamType.INTEGER)
    OptionalInt unorderedQueueSize();
}
