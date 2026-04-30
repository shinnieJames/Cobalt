package com.github.auties00.cobalt.wam.event;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;

import com.github.auties00.cobalt.wam.annotation.WamEvent;
import com.github.auties00.cobalt.wam.model.WamEventSpec;
import com.github.auties00.cobalt.wam.annotation.WamProperty;
import com.github.auties00.cobalt.wam.model.WamType;

import java.util.OptionalInt;

@WhatsAppWebModule(moduleName = "WAWebKeepInChatNotifWamEvent")
@WamEvent(id = 3484)
public interface KeepInChatNotifEvent extends WamEventSpec {
    @WamProperty(index = 3, type = WamType.INTEGER)
    OptionalInt kicGroupNotificationTaps();

    @WamProperty(index = 4, type = WamType.INTEGER)
    OptionalInt kicGroupNotifications();

    @WamProperty(index = 5, type = WamType.INTEGER)
    OptionalInt kicNotificationTaps();

    @WamProperty(index = 6, type = WamType.INTEGER)
    OptionalInt kicNotifications();
}
