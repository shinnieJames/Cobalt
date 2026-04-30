package com.github.auties00.cobalt.wam.event;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;

import com.github.auties00.cobalt.wam.annotation.WamEvent;
import com.github.auties00.cobalt.wam.model.WamEventSpec;
import com.github.auties00.cobalt.wam.annotation.WamProperty;
import com.github.auties00.cobalt.wam.model.WamType;
import com.github.auties00.cobalt.wam.type.ImageSearchFailedErrorType;
import com.github.auties00.cobalt.wam.type.MessageType;
import com.github.auties00.cobalt.wam.type.StwEntryPoint;
import com.github.auties00.cobalt.wam.type.StwFormat;
import com.github.auties00.cobalt.wam.type.StwInteraction;

import java.util.Optional;

@WhatsAppWebModule(moduleName = "WAWebSearchTheWebFunnelWamEvent")
@WamEvent(id = 5702)
public interface SearchTheWebFunnelEvent extends WamEventSpec {
    @WamProperty(index = 5, type = WamType.ENUM)
    Optional<ImageSearchFailedErrorType> imageSearchFailedErrorType();

    @WamProperty(index = 1, type = WamType.ENUM)
    Optional<MessageType> messageType();

    @WamProperty(index = 2, type = WamType.ENUM)
    Optional<StwEntryPoint> stwEntryPoint();

    @WamProperty(index = 3, type = WamType.ENUM)
    Optional<StwFormat> stwFormat();

    @WamProperty(index = 4, type = WamType.ENUM)
    Optional<StwInteraction> stwInteraction();
}
