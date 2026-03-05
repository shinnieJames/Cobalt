package com.github.auties00.cobalt.wam.event;

import com.github.auties00.cobalt.wam.annotation.WamEvent;
import com.github.auties00.cobalt.wam.annotation.WamProperty;
import com.github.auties00.cobalt.wam.type.WamType;
import com.github.auties00.cobalt.wam.type.PrivacyControlEntryPointType;
import com.github.auties00.cobalt.wam.type.PrivacyControlItemType;

import java.util.Optional;

@WamEvent(id = 3726)
public interface PrivacySettingsClickEvent extends WamEventSpec {
    @WamProperty(index = 1, type = WamType.ENUM)
    Optional<PrivacyControlEntryPointType> privacyControlEntryPoint();

    @WamProperty(index = 2, type = WamType.ENUM)
    Optional<PrivacyControlItemType> privacyControlItem();
}
