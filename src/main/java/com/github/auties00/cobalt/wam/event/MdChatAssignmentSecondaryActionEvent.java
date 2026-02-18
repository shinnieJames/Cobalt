package com.github.auties00.cobalt.wam.event;

import com.github.auties00.cobalt.wam.annotation.WamEvent;
import com.github.auties00.cobalt.wam.annotation.WamProperty;
import com.github.auties00.cobalt.wam.type.WamType;
import com.github.auties00.cobalt.wam.type.ErrorType;
import com.github.auties00.cobalt.wam.type.MdChatAssignmentAssignmentType;
import com.github.auties00.cobalt.wam.type.MdChatAssignmentChatType;
import com.github.auties00.cobalt.wam.type.MdChatAssignmentSecondaryActionType;
import com.github.auties00.cobalt.wam.type.MdChatAssignmentSourceType;

import java.util.Optional;
import java.util.OptionalInt;

@WamEvent(id = 3716)
public interface MdChatAssignmentSecondaryActionEvent extends WamEventSpec {
    @WamProperty(index = 1, type = WamType.STRING)
    Optional<String> mdChatAssignmentSecondaryActionAgentId();

    @WamProperty(index = 8, type = WamType.ENUM)
    Optional<MdChatAssignmentAssignmentType> mdChatAssignmentSecondaryActionAssignmentType();

    @WamProperty(index = 2, type = WamType.STRING)
    Optional<String> mdChatAssignmentSecondaryActionBrowserId();

    @WamProperty(index = 3, type = WamType.ENUM)
    Optional<MdChatAssignmentChatType> mdChatAssignmentSecondaryActionChatType();

    @WamProperty(index = 4, type = WamType.ENUM)
    Optional<ErrorType> mdChatAssignmentSecondaryActionError();

    @WamProperty(index = 5, type = WamType.INTEGER)
    OptionalInt mdChatAssignmentSecondaryActionMdId();

    @WamProperty(index = 6, type = WamType.ENUM)
    Optional<MdChatAssignmentSourceType> mdChatAssignmentSecondaryActionSource();

    @WamProperty(index = 7, type = WamType.ENUM)
    Optional<MdChatAssignmentSecondaryActionType> mdChatAssignmentSecondaryActionType();
}
