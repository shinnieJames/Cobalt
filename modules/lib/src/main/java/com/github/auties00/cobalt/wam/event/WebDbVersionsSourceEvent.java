package com.github.auties00.cobalt.wam.event;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;

import com.github.auties00.cobalt.wam.annotation.WamEvent;
import com.github.auties00.cobalt.wam.model.WamEventSpec;
import com.github.auties00.cobalt.wam.annotation.WamProperty;
import com.github.auties00.cobalt.wam.model.WamType;
import com.github.auties00.cobalt.wam.type.WebDbVersionSourceType;
import com.github.auties00.cobalt.wam.type.WebSchemaInitiatorType;

import java.util.Optional;

@WhatsAppWebModule(moduleName = "WAWebWebDbVersionsSourceWamEvent")
@WamEvent(id = 4784)
public interface WebDbVersionsSourceEvent extends WamEventSpec {
    @WamProperty(index = 1, type = WamType.ENUM)
    Optional<WebDbVersionSourceType> webDbVersionSource();

    @WamProperty(index = 2, type = WamType.ENUM)
    Optional<WebSchemaInitiatorType> webSchemaInitiator();
}
