package com.github.auties00.cobalt.wam.event;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;

import com.github.auties00.cobalt.wam.annotation.WamEvent;
import com.github.auties00.cobalt.wam.model.WamEventSpec;
import com.github.auties00.cobalt.wam.annotation.WamProperty;
import com.github.auties00.cobalt.wam.model.WamType;

import java.util.Optional;
import java.util.OptionalInt;

@WhatsAppWebModule(moduleName = "WAWebCommunityHomeActionWamEvent")
@WamEvent(id = 3494)
public interface CommunityHomeActionEvent extends WamEventSpec {
    @WamProperty(index = 1, type = WamType.INTEGER)
    OptionalInt communityHomeGroupDiscoveries();

    @WamProperty(index = 2, type = WamType.INTEGER)
    OptionalInt communityHomeGroupJoins();

    @WamProperty(index = 3, type = WamType.INTEGER)
    OptionalInt communityHomeGroupNavigations();

    @WamProperty(index = 4, type = WamType.STRING)
    Optional<String> communityHomeId();

    @WamProperty(index = 5, type = WamType.INTEGER)
    OptionalInt communityHomeViews();
}
