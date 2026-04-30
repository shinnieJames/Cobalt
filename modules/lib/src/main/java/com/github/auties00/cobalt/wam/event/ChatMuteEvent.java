package com.github.auties00.cobalt.wam.event;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;

import com.github.auties00.cobalt.wam.annotation.WamEvent;
import com.github.auties00.cobalt.wam.model.WamEventSpec;
import com.github.auties00.cobalt.wam.annotation.WamProperty;
import com.github.auties00.cobalt.wam.model.WamType;
import com.github.auties00.cobalt.wam.type.ActionConducted;
import com.github.auties00.cobalt.wam.type.ChatMuteNotificationChoice;
import com.github.auties00.cobalt.wam.type.MuteChatType;
import com.github.auties00.cobalt.wam.type.MuteEntryPoint;
import com.github.auties00.cobalt.wam.type.WaOfficialAccountName;

import java.time.Instant;
import java.util.Optional;
import java.util.OptionalInt;

@WhatsAppWebModule(moduleName = "WAWebChatMuteWamEvent")
@WamEvent(id = 2280)
public interface ChatMuteEvent extends WamEventSpec {
    @WamProperty(index = 3, type = WamType.ENUM)
    Optional<ActionConducted> actionConducted();

    @WamProperty(index = 5, type = WamType.ENUM)
    Optional<ChatMuteNotificationChoice> chatMuteNotificationChoice();

    @WamProperty(index = 4, type = WamType.ENUM)
    Optional<MuteChatType> muteChatType();

    @WamProperty(index = 1, type = WamType.TIMER)
    Optional<Instant> muteDuration();

    @WamProperty(index = 6, type = WamType.ENUM)
    Optional<MuteEntryPoint> muteEntryPoint();

    @WamProperty(index = 2, type = WamType.INTEGER)
    OptionalInt muteGroupSize();

    @WamProperty(index = 7, type = WamType.ENUM)
    Optional<WaOfficialAccountName> waOfficialAccountName();
}
