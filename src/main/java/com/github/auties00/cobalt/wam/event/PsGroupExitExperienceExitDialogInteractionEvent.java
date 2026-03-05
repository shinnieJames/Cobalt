package com.github.auties00.cobalt.wam.event;

import com.github.auties00.cobalt.wam.annotation.WamEvent;
import com.github.auties00.cobalt.wam.annotation.WamProperty;
import com.github.auties00.cobalt.wam.type.WamChannel;
import com.github.auties00.cobalt.wam.type.WamType;
import com.github.auties00.cobalt.wam.type.GroupExitExperienceOrigin;
import com.github.auties00.cobalt.wam.type.PsGroupExitExperienceExitDialogActions;

import java.util.Optional;

@WamEvent(id = 6318, channel = WamChannel.PRIVATE, privateStatsId = 152546501)
public interface PsGroupExitExperienceExitDialogInteractionEvent extends WamEventSpec {
    @WamProperty(index = 1, type = WamType.BOOLEAN)
    Optional<Boolean> psExitExperienceReportingEnabled();

    @WamProperty(index = 2, type = WamType.BOOLEAN)
    Optional<Boolean> psGroupExitExperienceEnabled();

    @WamProperty(index = 3, type = WamType.ENUM)
    Optional<PsGroupExitExperienceExitDialogActions> psGroupExitExperienceExitDialogAction();

    @WamProperty(index = 4, type = WamType.STRING)
    Optional<String> psGroupExitExperienceGroupJid();

    @WamProperty(index = 6, type = WamType.ENUM)
    Optional<GroupExitExperienceOrigin> psGroupExitExperienceTouchPoint();
}
