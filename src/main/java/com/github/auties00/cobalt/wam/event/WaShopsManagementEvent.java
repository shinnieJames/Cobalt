package com.github.auties00.cobalt.wam.event;

import com.github.auties00.cobalt.wam.annotation.WamEvent;
import com.github.auties00.cobalt.wam.annotation.WamProperty;
import com.github.auties00.cobalt.wam.type.WamType;
import com.github.auties00.cobalt.wam.type.ShopsManagementAction;

import java.util.Optional;

@WamEvent(id = 2908)
public interface WaShopsManagementEvent extends WamEventSpec {
    @WamProperty(index = 2, type = WamType.BOOLEAN)
    Optional<Boolean> isShopsProductPreviewVisible();

    @WamProperty(index = 1, type = WamType.ENUM)
    Optional<ShopsManagementAction> shopsManagementAction();

    @WamProperty(index = 3, type = WamType.STRING)
    Optional<String> shopsSellerJid();
}
