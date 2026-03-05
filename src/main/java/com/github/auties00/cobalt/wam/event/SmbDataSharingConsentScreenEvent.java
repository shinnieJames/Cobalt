package com.github.auties00.cobalt.wam.event;

import com.github.auties00.cobalt.wam.annotation.WamEvent;
import com.github.auties00.cobalt.wam.annotation.WamProperty;
import com.github.auties00.cobalt.wam.type.WamType;
import com.github.auties00.cobalt.wam.type.SmbDataSharingConsentScreenEntryPoint;
import com.github.auties00.cobalt.wam.type.SmbDataSharingConsentScreenType;

import java.util.Optional;
import java.util.OptionalInt;

@WamEvent(id = 3972)
public interface SmbDataSharingConsentScreenEvent extends WamEventSpec {
    @WamProperty(index = 3, type = WamType.ENUM)
    Optional<SmbDataSharingConsentScreenEntryPoint> smbDataSharingConsentScreenEntryPoint();

    @WamProperty(index = 1, type = WamType.ENUM)
    Optional<SmbDataSharingConsentScreenType> smbDataSharingConsentScreenType();

    @WamProperty(index = 2, type = WamType.INTEGER)
    OptionalInt smbDataSharingConsentScreenVersion();
}
