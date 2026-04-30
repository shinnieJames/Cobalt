package com.github.auties00.cobalt.wam.event;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;

import com.github.auties00.cobalt.wam.annotation.WamEvent;
import com.github.auties00.cobalt.wam.model.WamEventSpec;
import com.github.auties00.cobalt.wam.annotation.WamProperty;
import com.github.auties00.cobalt.wam.model.WamType;
import com.github.auties00.cobalt.wam.type.ApplicationState;
import com.github.auties00.cobalt.wam.type.MdBootstrapHistoryPayloadType;
import com.github.auties00.cobalt.wam.type.MdBootstrapPayloadType;
import com.github.auties00.cobalt.wam.type.MdBootstrapStepResult;

import java.util.Optional;
import java.util.OptionalInt;

@WhatsAppWebModule(moduleName = "WAWebMdBootstrapAppStateDataDownloadedWamEvent")
@WamEvent(id = 2294)
public interface MdBootstrapAppStateDataDownloadedEvent extends WamEventSpec {
    @WamProperty(index = 14, type = WamType.STRING)
    Optional<String> appContext();

    @WamProperty(index = 15, type = WamType.INTEGER)
    OptionalInt appContextBitfield();

    @WamProperty(index = 13, type = WamType.ENUM)
    Optional<ApplicationState> applicationState();

    @WamProperty(index = 16, type = WamType.STRING)
    Optional<String> historySyncRetryRequestId();

    @WamProperty(index = 5, type = WamType.INTEGER)
    OptionalInt mdBootstrapContactsCount();

    @WamProperty(index = 11, type = WamType.ENUM)
    Optional<MdBootstrapHistoryPayloadType> mdBootstrapHistoryPayloadType();

    @WamProperty(index = 4, type = WamType.INTEGER)
    OptionalInt mdBootstrapPayloadSize();

    @WamProperty(index = 2, type = WamType.ENUM)
    Optional<MdBootstrapPayloadType> mdBootstrapPayloadType();

    @WamProperty(index = 6, type = WamType.INTEGER)
    OptionalInt mdBootstrapStepDuration();

    @WamProperty(index = 7, type = WamType.ENUM)
    Optional<MdBootstrapStepResult> mdBootstrapStepResult();

    @WamProperty(index = 10, type = WamType.STRING)
    Optional<String> mdRegAttemptId();

    @WamProperty(index = 1, type = WamType.STRING)
    Optional<String> mdSessionId();

    @WamProperty(index = 8, type = WamType.INTEGER)
    OptionalInt mdStorageQuotaBytes();

    @WamProperty(index = 9, type = WamType.INTEGER)
    OptionalInt mdStorageQuotaUsedBytes();

    @WamProperty(index = 17, type = WamType.STRING)
    Optional<String> mdSyncFailureReason();

    @WamProperty(index = 3, type = WamType.INTEGER)
    OptionalInt mdTimestamp();
}
