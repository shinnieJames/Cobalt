package com.github.auties00.cobalt.wam.event;

import com.github.auties00.cobalt.wam.annotation.WamEvent;
import com.github.auties00.cobalt.wam.annotation.WamProperty;
import com.github.auties00.cobalt.wam.type.WamType;
import com.github.auties00.cobalt.wam.type.PnhActionType;
import com.github.auties00.cobalt.wam.type.PnhChatTypeType;
import com.github.auties00.cobalt.wam.type.PnhEntryPointType;
import com.github.auties00.cobalt.wam.type.PnhMessageChatParty;

import java.util.Optional;

@WamEvent(id = 3808)
public interface PnhRequestRevealActionEvent extends WamEventSpec {
    @WamProperty(index = 1, type = WamType.ENUM)
    Optional<PnhActionType> pnhAction();

    @WamProperty(index = 2, type = WamType.ENUM)
    Optional<PnhMessageChatParty> pnhChatParty();

    @WamProperty(index = 3, type = WamType.ENUM)
    Optional<PnhChatTypeType> pnhChatType();

    @WamProperty(index = 4, type = WamType.ENUM)
    Optional<PnhEntryPointType> pnhEntryPoint();

    @WamProperty(index = 5, type = WamType.STRING)
    Optional<String> threadId();
}
