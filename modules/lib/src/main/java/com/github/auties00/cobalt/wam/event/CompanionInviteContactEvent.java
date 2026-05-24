package com.github.auties00.cobalt.wam.event;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;

import com.github.auties00.cobalt.wam.annotation.WamEvent;
import com.github.auties00.cobalt.wam.model.WamEventSpec;
import com.github.auties00.cobalt.wam.annotation.WamProperty;
import com.github.auties00.cobalt.wam.model.WamType;
import com.github.auties00.cobalt.wam.type.CompanionInviteMethodType;
import com.github.auties00.cobalt.wam.type.CompanionInviteOriginType;

import java.util.Optional;
import java.util.OptionalInt;

@WhatsAppWebModule(moduleName = "WAWebCompanionInviteContactWamEvent")
@WamEvent(id = 8230)
public interface CompanionInviteContactEvent extends WamEventSpec {
    @WamProperty(index = 1, type = WamType.STRING)
    Optional<String> companionInviteCodeError();

    @WamProperty(index = 2, type = WamType.ENUM)
    Optional<CompanionInviteMethodType> companionInviteMethod();

    @WamProperty(index = 3, type = WamType.INTEGER)
    OptionalInt companionInviteNumContactsAddressBook();

    @WamProperty(index = 4, type = WamType.INTEGER)
    OptionalInt companionInviteNumContactsWa();

    @WamProperty(index = 5, type = WamType.ENUM)
    Optional<CompanionInviteOriginType> companionInviteOrigin();

    @WamProperty(index = 6, type = WamType.INTEGER)
    OptionalInt companionInviteSessionId();

    @WamProperty(index = 7, type = WamType.BOOLEAN)
    Optional<Boolean> companionValidInviteCode();
}
