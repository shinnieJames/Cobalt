package com.github.auties00.cobalt.wam.event;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;

import com.github.auties00.cobalt.wam.annotation.WamEvent;
import com.github.auties00.cobalt.wam.model.WamEventSpec;
import com.github.auties00.cobalt.wam.annotation.WamProperty;
import com.github.auties00.cobalt.wam.model.WamType;

import java.util.Optional;
import java.util.OptionalInt;

@WhatsAppWebModule(moduleName = "WAWebWebcMessageProcessingPerfWamEvent")
@WamEvent(id = 5790, releaseWeight = 100)
public interface WebcMessageProcessingPerfEvent extends WamEventSpec {
    @WamProperty(index = 7, type = WamType.INTEGER)
    OptionalInt dbStoringT();

    @WamProperty(index = 1, type = WamType.INTEGER)
    OptionalInt decryptionT();

    @WamProperty(index = 2, type = WamType.BOOLEAN)
    Optional<Boolean> isOffline();

    @WamProperty(index = 8, type = WamType.INTEGER)
    OptionalInt lidProcessingT();

    @WamProperty(index = 3, type = WamType.INTEGER)
    OptionalInt parsingT();

    @WamProperty(index = 4, type = WamType.INTEGER)
    OptionalInt postProcessingT();

    @WamProperty(index = 5, type = WamType.INTEGER)
    OptionalInt preProcessingT();

    @WamProperty(index = 6, type = WamType.INTEGER)
    OptionalInt processingT();

    @WamProperty(index = 9, type = WamType.INTEGER)
    OptionalInt reportTokenValidationT();
}
