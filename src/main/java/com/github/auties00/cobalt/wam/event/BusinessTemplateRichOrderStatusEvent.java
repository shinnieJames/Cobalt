package com.github.auties00.cobalt.wam.event;

import com.github.auties00.cobalt.wam.annotation.WamEvent;
import com.github.auties00.cobalt.wam.annotation.WamProperty;
import com.github.auties00.cobalt.wam.type.WamChannel;
import com.github.auties00.cobalt.wam.type.WamType;
import com.github.auties00.cobalt.wam.type.ChatsFolderType;
import com.github.auties00.cobalt.wam.type.ContactType;

import java.util.Optional;

@WamEvent(id = 7076, channel = WamChannel.PRIVATE, privateStatsId = 113760892)
public interface BusinessTemplateRichOrderStatusEvent extends WamEventSpec {
    @WamProperty(index = 1, type = WamType.STRING)
    Optional<String> actionTypeRichOrderStatus();

    @WamProperty(index = 2, type = WamType.STRING)
    Optional<String> businessJid();

    @WamProperty(index = 3, type = WamType.ENUM)
    Optional<ChatsFolderType> chatsFolderType();

    @WamProperty(index = 4, type = WamType.ENUM)
    Optional<ContactType> contactType();

    @WamProperty(index = 5, type = WamType.BOOLEAN)
    Optional<Boolean> isBizIntent();

    @WamProperty(index = 6, type = WamType.BOOLEAN)
    Optional<Boolean> isInsubContact();

    @WamProperty(index = 7, type = WamType.BOOLEAN)
    Optional<Boolean> isMuted();

    @WamProperty(index = 8, type = WamType.BOOLEAN)
    Optional<Boolean> readReceiptsEnabled();

    @WamProperty(index = 9, type = WamType.STRING)
    Optional<String> templateId();
}
