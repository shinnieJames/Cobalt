package com.github.auties00.cobalt.wam.event;

import com.github.auties00.cobalt.wam.annotation.WamEvent;
import com.github.auties00.cobalt.wam.annotation.WamProperty;
import com.github.auties00.cobalt.wam.type.WamType;
import com.github.auties00.cobalt.wam.type.PrivacyTipActionType;

import java.util.Optional;

@WamEvent(id = 4794)
public interface PrivacyTipActionEvent extends WamEventSpec {
    @WamProperty(index = 1, type = WamType.ENUM)
    Optional<PrivacyTipActionType> privacyTipActionType();
}
