package com.github.auties00.cobalt.wam.event;

import com.github.auties00.cobalt.wam.annotation.WamEvent;
import com.github.auties00.cobalt.wam.annotation.WamProperty;
import com.github.auties00.cobalt.wam.type.WamType;

import java.util.Optional;
import java.util.OptionalInt;

@WamEvent(id = 4214)
public interface ChatLockDailyEvent extends WamEventSpec {
    @WamProperty(index = 1, type = WamType.INTEGER)
    OptionalInt folderChatsCount();

    @WamProperty(index = 2, type = WamType.INTEGER)
    OptionalInt folderOpenCount();

    @WamProperty(index = 5, type = WamType.BOOLEAN)
    Optional<Boolean> lockFolderHidden();

    @WamProperty(index = 3, type = WamType.INTEGER)
    OptionalInt newAddChatCount();

    @WamProperty(index = 4, type = WamType.INTEGER)
    OptionalInt newRemoveChatCount();

    @WamProperty(index = 6, type = WamType.BOOLEAN)
    Optional<Boolean> secretCodeActive();
}
