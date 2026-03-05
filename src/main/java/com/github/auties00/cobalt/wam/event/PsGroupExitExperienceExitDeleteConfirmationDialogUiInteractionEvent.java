package com.github.auties00.cobalt.wam.event;

import com.github.auties00.cobalt.wam.annotation.WamEvent;
import com.github.auties00.cobalt.wam.annotation.WamProperty;
import com.github.auties00.cobalt.wam.type.WamChannel;
import com.github.auties00.cobalt.wam.type.WamType;
import com.github.auties00.cobalt.wam.type.GroupExitExperienceOrigin;
import com.github.auties00.cobalt.wam.type.PsGroupExitExperienceDeleteConfirmationDialogActions;

import java.util.Optional;

@WamEvent(id = 6316, channel = WamChannel.PRIVATE, privateStatsId = 152546501)
public interface PsGroupExitExperienceExitDeleteConfirmationDialogUiInteractionEvent extends WamEventSpec {
    @WamProperty(index = 1, type = WamType.ENUM)
    Optional<PsGroupExitExperienceDeleteConfirmationDialogActions> psGroupExitExperienceDeleteConfirmationDialogAction();

    @WamProperty(index = 2, type = WamType.STRING)
    Optional<String> psGroupExitExperienceGroupJid();

    @WamProperty(index = 4, type = WamType.ENUM)
    Optional<GroupExitExperienceOrigin> psGroupExitExperienceTouchPoint();
}
