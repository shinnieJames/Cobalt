package com.github.auties00.cobalt.wam.event;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;

import com.github.auties00.cobalt.wam.annotation.WamEvent;
import com.github.auties00.cobalt.wam.model.WamEventSpec;
import com.github.auties00.cobalt.wam.annotation.WamProperty;
import com.github.auties00.cobalt.wam.model.WamType;
import com.github.auties00.cobalt.wam.type.UiRevokeActionType;

import java.util.Optional;
import java.util.OptionalInt;

@WhatsAppWebModule(moduleName = "WAWebUiRevokeActionWamEvent")
@WamEvent(id = 3298)
public interface UiRevokeActionEvent extends WamEventSpec {
    @WamProperty(index = 1, type = WamType.ENUM)
    Optional<UiRevokeActionType> messageAction();

    @WamProperty(index = 2, type = WamType.INTEGER)
    OptionalInt uiRevokeActionDuration();

    @WamProperty(index = 3, type = WamType.STRING)
    Optional<String> uiRevokeActionSessionId();
}
