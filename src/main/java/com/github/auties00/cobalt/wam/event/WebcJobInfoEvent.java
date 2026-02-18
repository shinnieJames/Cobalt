package com.github.auties00.cobalt.wam.event;

import com.github.auties00.cobalt.wam.annotation.WamEvent;
import com.github.auties00.cobalt.wam.annotation.WamProperty;
import com.github.auties00.cobalt.wam.type.WamType;
import com.github.auties00.cobalt.wam.type.WebcJobResultTypeCode;
import com.github.auties00.cobalt.wam.type.WebcScenarioType;

import java.util.Optional;
import java.util.OptionalInt;

@WamEvent(id = 3054)
public interface WebcJobInfoEvent extends WamEventSpec {
    @WamProperty(index = 1, type = WamType.STRING)
    Optional<String> jobName();

    @WamProperty(index = 2, type = WamType.STRING)
    Optional<String> jobPriority();

    @WamProperty(index = 5, type = WamType.ENUM)
    Optional<WebcJobResultTypeCode> jobResultType();

    @WamProperty(index = 4, type = WamType.INTEGER)
    OptionalInt pendingJobsCount();

    @WamProperty(index = 3, type = WamType.ENUM)
    Optional<WebcScenarioType> scenario();

    @WamProperty(index = 6, type = WamType.INTEGER)
    OptionalInt webcJobAddedT();

    @WamProperty(index = 8, type = WamType.INTEGER)
    OptionalInt webcJobCompletedT();

    @WamProperty(index = 7, type = WamType.INTEGER)
    OptionalInt webcJobStartedT();
}
