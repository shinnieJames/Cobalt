package com.github.auties00.cobalt.wam.event;

import com.github.auties00.cobalt.wam.annotation.WamEvent;
import com.github.auties00.cobalt.wam.annotation.WamProperty;
import com.github.auties00.cobalt.wam.type.WamType;
import com.github.auties00.cobalt.wam.type.KicActionNameType;
import com.github.auties00.cobalt.wam.type.KicActorType;
import com.github.auties00.cobalt.wam.type.KicEntryPointType;
import com.github.auties00.cobalt.wam.type.MediaType;

import java.util.Optional;
import java.util.OptionalInt;

@WamEvent(id = 3482)
public interface DisappearingMessageKeepInChatEvent extends WamEventSpec {
    @WamProperty(index = 16, type = WamType.BOOLEAN)
    Optional<Boolean> canEditDmSettings();

    @WamProperty(index = 1, type = WamType.INTEGER)
    OptionalInt chatEphemeralityDuration();

    @WamProperty(index = 2, type = WamType.BOOLEAN)
    Optional<Boolean> isAGroup();

    @WamProperty(index = 3, type = WamType.BOOLEAN)
    Optional<Boolean> isAdmin();

    @WamProperty(index = 15, type = WamType.INTEGER)
    OptionalInt keptCount();

    @WamProperty(index = 4, type = WamType.INTEGER)
    OptionalInt keptDelta();

    @WamProperty(index = 5, type = WamType.ENUM)
    Optional<KicActionNameType> kicActionName();

    @WamProperty(index = 6, type = WamType.ENUM)
    Optional<KicActorType> kicActor();

    @WamProperty(index = 7, type = WamType.ENUM)
    Optional<KicEntryPointType> kicEntryPoint();

    @WamProperty(index = 8, type = WamType.ENUM)
    Optional<MediaType> mediaType();

    @WamProperty(index = 9, type = WamType.BOOLEAN)
    Optional<Boolean> messageExpiredOnUnkeep();

    @WamProperty(index = 10, type = WamType.INTEGER)
    OptionalInt messageExpiryTimer();

    @WamProperty(index = 11, type = WamType.INTEGER)
    OptionalInt messagesInFolder();

    @WamProperty(index = 12, type = WamType.INTEGER)
    OptionalInt messagesSelected();

    @WamProperty(index = 13, type = WamType.STRING)
    Optional<String> threadId();
}
