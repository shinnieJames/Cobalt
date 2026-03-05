package com.github.auties00.cobalt.wam.event;

import com.github.auties00.cobalt.wam.annotation.WamEvent;
import com.github.auties00.cobalt.wam.annotation.WamProperty;
import com.github.auties00.cobalt.wam.type.WamType;
import com.github.auties00.cobalt.wam.type.MediaType;
import com.github.auties00.cobalt.wam.type.MessageType;
import com.github.auties00.cobalt.wam.type.ReactionActionType;

import java.util.Optional;

@WamEvent(id = 3184, betaWeight = 10, releaseWeight = 20)
public interface ReactionActionsEvent extends WamEventSpec {
    @WamProperty(index = 3, type = WamType.ENUM)
    Optional<MediaType> mediaType();

    @WamProperty(index = 1, type = WamType.ENUM)
    Optional<MessageType> messageType();

    @WamProperty(index = 2, type = WamType.ENUM)
    Optional<ReactionActionType> reactionAction();
}
