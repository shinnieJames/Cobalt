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

@WhatsAppWebModule(moduleName = "WAWebMdBootstrapHistoryDataDownloadedWamEvent")
@WamEvent(id = 2296)
public interface MdBootstrapHistoryDataDownloadedEvent extends WamEventSpec {
    @WamProperty(index = 18, type = WamType.STRING)
    Optional<String> appContext();

    @WamProperty(index = 19, type = WamType.INTEGER)
    OptionalInt appContextBitfield();

    @WamProperty(index = 17, type = WamType.ENUM)
    Optional<ApplicationState> applicationState();

    @WamProperty(index = 15, type = WamType.INTEGER)
    OptionalInt historySyncChunkOrder();

    @WamProperty(index = 20, type = WamType.STRING)
    Optional<String> historySyncRetryRequestId();

    @WamProperty(index = 14, type = WamType.INTEGER)
    OptionalInt historySyncStageProgress();

    @WamProperty(index = 6, type = WamType.INTEGER)
    OptionalInt mdBootstrapChatsCount();

    @WamProperty(index = 13, type = WamType.ENUM)
    Optional<MdBootstrapHistoryPayloadType> mdBootstrapHistoryPayloadType();

    @WamProperty(index = 5, type = WamType.INTEGER)
    OptionalInt mdBootstrapMessagesCount();

    @WamProperty(index = 4, type = WamType.INTEGER)
    OptionalInt mdBootstrapPayloadSize();

    @WamProperty(index = 21, type = WamType.INTEGER)
    OptionalInt mdBootstrapPayloadThumbnailsSize();

    @WamProperty(index = 2, type = WamType.ENUM)
    Optional<MdBootstrapPayloadType> mdBootstrapPayloadType();

    @WamProperty(index = 7, type = WamType.INTEGER)
    OptionalInt mdBootstrapStepDuration();

    @WamProperty(index = 8, type = WamType.ENUM)
    Optional<MdBootstrapStepResult> mdBootstrapStepResult();

    @WamProperty(index = 11, type = WamType.INTEGER)
    OptionalInt mdHsOldestMessageTimestamp();

    @WamProperty(index = 12, type = WamType.STRING)
    Optional<String> mdRegAttemptId();

    @WamProperty(index = 1, type = WamType.STRING)
    Optional<String> mdSessionId();

    @WamProperty(index = 9, type = WamType.INTEGER)
    OptionalInt mdStorageQuotaBytes();

    @WamProperty(index = 10, type = WamType.INTEGER)
    OptionalInt mdStorageQuotaUsedBytes();

    @WamProperty(index = 16, type = WamType.STRING)
    Optional<String> mdSyncFailureReason();

    @WamProperty(index = 3, type = WamType.INTEGER)
    OptionalInt mdTimestamp();
}
