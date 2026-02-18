package com.github.auties00.cobalt.wam.event;

import com.github.auties00.cobalt.wam.annotation.WamEvent;
import com.github.auties00.cobalt.wam.annotation.WamProperty;
import com.github.auties00.cobalt.wam.type.WamType;
import com.github.auties00.cobalt.wam.type.MdBootstrapHistoryPayloadType;
import com.github.auties00.cobalt.wam.type.MdBootstrapPayloadType;

import java.util.Optional;
import java.util.OptionalInt;

@WamEvent(id = 4650)
public interface MdBootstrapHistoryDataStartDownloadingEvent extends WamEventSpec {
    @WamProperty(index = 1, type = WamType.INTEGER)
    OptionalInt historySyncChunkOrder();

    @WamProperty(index = 9, type = WamType.STRING)
    Optional<String> historySyncRetryRequestId();

    @WamProperty(index = 2, type = WamType.INTEGER)
    OptionalInt historySyncStageProgress();

    @WamProperty(index = 3, type = WamType.ENUM)
    Optional<MdBootstrapHistoryPayloadType> mdBootstrapHistoryPayloadType();

    @WamProperty(index = 4, type = WamType.INTEGER)
    OptionalInt mdBootstrapPayloadSize();

    @WamProperty(index = 5, type = WamType.ENUM)
    Optional<MdBootstrapPayloadType> mdBootstrapPayloadType();

    @WamProperty(index = 6, type = WamType.INTEGER)
    OptionalInt mdBootstrapStepDuration();

    @WamProperty(index = 7, type = WamType.STRING)
    Optional<String> mdSessionId();

    @WamProperty(index = 8, type = WamType.INTEGER)
    OptionalInt mdTimestamp();
}
