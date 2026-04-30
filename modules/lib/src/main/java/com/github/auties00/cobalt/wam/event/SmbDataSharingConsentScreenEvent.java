package com.github.auties00.cobalt.wam.event;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;

import com.github.auties00.cobalt.wam.annotation.WamEvent;
import com.github.auties00.cobalt.wam.model.WamEventSpec;
import com.github.auties00.cobalt.wam.annotation.WamProperty;
import com.github.auties00.cobalt.wam.model.WamType;
import com.github.auties00.cobalt.wam.type.SmbDataSharingConsentScreenEntryPoint;
import com.github.auties00.cobalt.wam.type.SmbDataSharingConsentScreenType;

import java.util.Optional;
import java.util.OptionalInt;

@WhatsAppWebModule(moduleName = "WAWebSmbDataSharingConsentScreenWamEvent")
@WamEvent(id = 3972)
public interface SmbDataSharingConsentScreenEvent extends WamEventSpec {
    @WamProperty(index = 4, type = WamType.INTEGER)
    OptionalInt elapsedTimeMs();

    @WamProperty(index = 5, type = WamType.INTEGER)
    OptionalInt previousImpressionCount();

    @WamProperty(index = 6, type = WamType.INTEGER)
    OptionalInt previousOptOutImpressionCount();

    @WamProperty(index = 3, type = WamType.ENUM)
    Optional<SmbDataSharingConsentScreenEntryPoint> smbDataSharingConsentScreenEntryPoint();

    @WamProperty(index = 1, type = WamType.ENUM)
    Optional<SmbDataSharingConsentScreenType> smbDataSharingConsentScreenType();

    @WamProperty(index = 2, type = WamType.INTEGER)
    OptionalInt smbDataSharingConsentScreenVersion();
}
