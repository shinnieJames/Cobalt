package com.github.auties00.cobalt.wam.event;

import com.github.auties00.cobalt.wam.annotation.WamEvent;
import com.github.auties00.cobalt.wam.annotation.WamProperty;
import com.github.auties00.cobalt.wam.type.WamType;
import com.github.auties00.cobalt.wam.type.DyiReportTypeCode;
import com.github.auties00.cobalt.wam.type.DyiTriggerTypeCode;

import java.util.Optional;

@WamEvent(id = 7166)
public interface DyiReportRequestEvent extends WamEventSpec {
    @WamProperty(index = 1, type = WamType.ENUM)
    Optional<DyiReportTypeCode> dyiReportType();

    @WamProperty(index = 2, type = WamType.ENUM)
    Optional<DyiTriggerTypeCode> dyiTriggerType();
}
