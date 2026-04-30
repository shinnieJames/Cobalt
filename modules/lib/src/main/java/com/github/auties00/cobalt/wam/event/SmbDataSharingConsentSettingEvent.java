package com.github.auties00.cobalt.wam.event;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;

import com.github.auties00.cobalt.wam.annotation.WamEvent;
import com.github.auties00.cobalt.wam.model.WamEventSpec;
import com.github.auties00.cobalt.wam.annotation.WamProperty;
import com.github.auties00.cobalt.wam.model.WamType;
import com.github.auties00.cobalt.wam.type.SmbDataSharingConsentSettingEntryPoint;

import java.util.Optional;
import java.util.OptionalInt;

@WhatsAppWebModule(moduleName = "WAWebSmbDataSharingConsentSettingWamEvent")
@WamEvent(id = 3974)
public interface SmbDataSharingConsentSettingEvent extends WamEventSpec {
    @WamProperty(index = 1, type = WamType.ENUM)
    Optional<SmbDataSharingConsentSettingEntryPoint> smbDataSharingConsentSettingEntryPoint();

    @WamProperty(index = 2, type = WamType.BOOLEAN)
    Optional<Boolean> smbDataSharingConsentSettingType();

    @WamProperty(index = 3, type = WamType.INTEGER)
    OptionalInt smbDataSharingConsentSettingVersion();
}
