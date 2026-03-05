package com.github.auties00.cobalt.wam.event;

import com.github.auties00.cobalt.wam.annotation.WamEvent;
import com.github.auties00.cobalt.wam.annotation.WamProperty;
import com.github.auties00.cobalt.wam.type.WamType;
import com.github.auties00.cobalt.wam.type.ChatFilterActionTypes;
import com.github.auties00.cobalt.wam.type.EntryPoint;
import com.github.auties00.cobalt.wam.type.OppositePlatformEnum;
import com.github.auties00.cobalt.wam.type.SmbFeatureNameEnum;
import com.github.auties00.cobalt.wam.type.SmbUserActionTypeEnum;
import com.github.auties00.cobalt.wam.type.SurfaceType;

import java.util.Optional;
import java.util.OptionalInt;

@WamEvent(id = 5462)
public interface SmbUserJourneyEvent extends WamEventSpec {
    @WamProperty(index = 1, type = WamType.ENUM)
    Optional<ChatFilterActionTypes> actionType();

    @WamProperty(index = 2, type = WamType.BOOLEAN)
    Optional<Boolean> contactIsSaved();

    @WamProperty(index = 3, type = WamType.ENUM)
    Optional<EntryPoint> entryPoint();

    @WamProperty(index = 18, type = WamType.STRING)
    Optional<String> entryPointDetails();

    @WamProperty(index = 4, type = WamType.STRING)
    Optional<String> extraAttributes();

    @WamProperty(index = 5, type = WamType.ENUM)
    Optional<OppositePlatformEnum> oppositePlatform();

    @WamProperty(index = 14, type = WamType.ENUM)
    Optional<SurfaceType> prevSurface();

    @WamProperty(index = 12, type = WamType.INTEGER)
    OptionalInt recipientSize();

    @WamProperty(index = 7, type = WamType.INTEGER)
    OptionalInt seqId();

    @WamProperty(index = 8, type = WamType.ENUM)
    Optional<SmbFeatureNameEnum> smbFeatureName();

    @WamProperty(index = 13, type = WamType.ENUM)
    Optional<SmbUserActionTypeEnum> smbUserActionType();

    @WamProperty(index = 9, type = WamType.STRING)
    Optional<String> smbUserSessionId();

    @WamProperty(index = 10, type = WamType.ENUM)
    Optional<SurfaceType> surface();

    @WamProperty(index = 11, type = WamType.STRING)
    Optional<String> userActionTarget();
}
