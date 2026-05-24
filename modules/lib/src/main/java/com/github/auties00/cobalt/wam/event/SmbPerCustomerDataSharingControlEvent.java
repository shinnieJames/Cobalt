package com.github.auties00.cobalt.wam.event;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;

import com.github.auties00.cobalt.wam.annotation.WamEvent;
import com.github.auties00.cobalt.wam.model.WamEventSpec;
import com.github.auties00.cobalt.wam.annotation.WamProperty;
import com.github.auties00.cobalt.wam.model.WamType;
import com.github.auties00.cobalt.wam.type.SmbPerCustomerDataSharingControlAction;
import com.github.auties00.cobalt.wam.type.SmbPerCustomerDataSharingControlEntryPoint;

import java.util.Optional;
import java.util.OptionalInt;

@WhatsAppWebModule(moduleName = "WAWebSmbPerCustomerDataSharingControlWamEvent")
@WamEvent(id = 8232)
public interface SmbPerCustomerDataSharingControlEvent extends WamEventSpec {
    @WamProperty(index = 1, type = WamType.ENUM)
    Optional<SmbPerCustomerDataSharingControlAction> smbPerCustomerDataSharingControlAction();

    @WamProperty(index = 2, type = WamType.BOOLEAN)
    Optional<Boolean> smbPerCustomerDataSharingControlActionOptInStatus();

    @WamProperty(index = 3, type = WamType.BOOLEAN)
    Optional<Boolean> smbPerCustomerDataSharingControlCurrentOptInStatus();

    @WamProperty(index = 4, type = WamType.ENUM)
    Optional<SmbPerCustomerDataSharingControlEntryPoint> smbPerCustomerDataSharingControlEntryPoint();

    @WamProperty(index = 5, type = WamType.INTEGER)
    OptionalInt smbPerCustomerDataSharingControlVersion();
}
