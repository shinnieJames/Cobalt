package com.github.auties00.cobalt.wam.event;

import com.github.auties00.cobalt.wam.annotation.WamEvent;
import com.github.auties00.cobalt.wam.annotation.WamProperty;
import com.github.auties00.cobalt.wam.type.WamType;
import com.github.auties00.cobalt.wam.type.DyiReportTypeCode;

import java.util.Optional;

@WamEvent(id = 7162)
public interface DyiReportDownloadEvent extends WamEventSpec {
    @WamProperty(index = 3, type = WamType.STRING)
    Optional<String> dyiDownloadErrorMessage();

    @WamProperty(index = 1, type = WamType.BOOLEAN)
    Optional<Boolean> dyiDownloadSucceeded();

    @WamProperty(index = 2, type = WamType.ENUM)
    Optional<DyiReportTypeCode> dyiReportType();
}
