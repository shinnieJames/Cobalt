package com.github.auties00.cobalt.wam.event;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;

import com.github.auties00.cobalt.wam.annotation.WamEvent;
import com.github.auties00.cobalt.wam.model.WamEventSpec;
import com.github.auties00.cobalt.wam.annotation.WamProperty;
import com.github.auties00.cobalt.wam.model.WamType;
import com.github.auties00.cobalt.wam.type.MediaType;
import com.github.auties00.cobalt.wam.type.SnackbarActionType;

import java.util.Optional;
import java.util.OptionalInt;

@WhatsAppWebModule(moduleName = "WAWebSnackbarDeleteUndoWamEvent")
@WamEvent(id = 3628)
public interface SnackbarDeleteUndoEvent extends WamEventSpec {
    @WamProperty(index = 1, type = WamType.BOOLEAN)
    Optional<Boolean> isAGroup();

    @WamProperty(index = 6, type = WamType.ENUM)
    Optional<MediaType> mediaType();

    @WamProperty(index = 2, type = WamType.INTEGER)
    OptionalInt messagesUndeleted();

    @WamProperty(index = 3, type = WamType.ENUM)
    Optional<SnackbarActionType> snackbarActionType();

    @WamProperty(index = 4, type = WamType.STRING)
    Optional<String> threadId();
}
