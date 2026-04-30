package com.github.auties00.cobalt.wam.event;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;

import com.github.auties00.cobalt.wam.annotation.WamEvent;
import com.github.auties00.cobalt.wam.model.WamEventSpec;
import com.github.auties00.cobalt.wam.annotation.WamProperty;
import com.github.auties00.cobalt.wam.model.WamType;

import java.util.OptionalInt;

@WhatsAppWebModule(moduleName = "WAWebWebcStatusSessionWamEvent")
@WamEvent(id = 1880)
public interface WebcStatusSessionEvent extends WamEventSpec {
    @WamProperty(index = 4, type = WamType.INTEGER)
    OptionalInt webcStatusMutedItemCount();

    @WamProperty(index = 7, type = WamType.INTEGER)
    OptionalInt webcStatusMutedRowCount();

    @WamProperty(index = 2, type = WamType.INTEGER)
    OptionalInt webcStatusRecentItemCount();

    @WamProperty(index = 5, type = WamType.INTEGER)
    OptionalInt webcStatusRecentRowCount();

    @WamProperty(index = 1, type = WamType.INTEGER)
    OptionalInt webcStatusSessionId();

    @WamProperty(index = 3, type = WamType.INTEGER)
    OptionalInt webcStatusViewedItemCount();

    @WamProperty(index = 6, type = WamType.INTEGER)
    OptionalInt webcStatusViewedRowCount();
}
