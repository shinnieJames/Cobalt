package com.github.auties00.cobalt.wam.event;

import com.github.auties00.cobalt.wam.annotation.WamEvent;
import com.github.auties00.cobalt.wam.annotation.WamProperty;
import com.github.auties00.cobalt.wam.type.WamType;
import com.github.auties00.cobalt.wam.type.ReportToAdminInteraction;

import java.util.Optional;

@WamEvent(id = 4420)
public interface ReportToAdminEventsEvent extends WamEventSpec {
    @WamProperty(index = 1, type = WamType.ENUM)
    Optional<ReportToAdminInteraction> reportToAdminInteraction();

    @WamProperty(index = 2, type = WamType.STRING)
    Optional<String> rtaGroupId();
}
