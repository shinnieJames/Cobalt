package com.github.auties00.cobalt.wam.event;

import com.github.auties00.cobalt.wam.annotation.WamEvent;
import com.github.auties00.cobalt.wam.annotation.WamProperty;
import com.github.auties00.cobalt.wam.type.WamType;
import com.github.auties00.cobalt.wam.type.OnePdSignalNotSharedReason;
import com.github.auties00.cobalt.wam.type.SignalCanceledReason;
import com.github.auties00.cobalt.wam.type.SignalMessageState;
import com.github.auties00.cobalt.wam.type.SignalMessageType;
import com.github.auties00.cobalt.wam.type.SignalOrigin;
import com.github.auties00.cobalt.wam.type.SignalSharingStatus;
import com.github.auties00.cobalt.wam.type.SignalSurface;
import com.github.auties00.cobalt.wam.type.SignalType;
import com.github.auties00.cobalt.wam.type.SpSignalNotSharedReason;

import java.util.Optional;

@WamEvent(id = 6798)
public interface MmSignalSharingVerificationFsEventEvent extends WamEventSpec {
    @WamProperty(index = 1, type = WamType.BOOLEAN)
    Optional<Boolean> isCompanionDevice();

    @WamProperty(index = 13, type = WamType.BOOLEAN)
    Optional<Boolean> isShimmingSignal();

    @WamProperty(index = 2, type = WamType.BOOLEAN)
    Optional<Boolean> isUserDisclosed();

    @WamProperty(index = 3, type = WamType.ENUM)
    Optional<OnePdSignalNotSharedReason> onePdSignalNotSharedReason();

    @WamProperty(index = 4, type = WamType.ENUM)
    Optional<SignalCanceledReason> signalCanceledReason();

    @WamProperty(index = 5, type = WamType.ENUM)
    Optional<SignalMessageState> signalMessageState();

    @WamProperty(index = 6, type = WamType.ENUM)
    Optional<SignalMessageType> signalMessageType();

    @WamProperty(index = 7, type = WamType.ENUM)
    Optional<SignalOrigin> signalOrigin();

    @WamProperty(index = 8, type = WamType.ENUM)
    Optional<SignalSharingStatus> signalSharingStatus();

    @WamProperty(index = 9, type = WamType.ENUM)
    Optional<SignalSurface> signalSurface();

    @WamProperty(index = 10, type = WamType.ENUM)
    Optional<SignalType> signalType();

    @WamProperty(index = 11, type = WamType.ENUM)
    Optional<SpSignalNotSharedReason> spSignalNotSharedReason();

    @WamProperty(index = 12, type = WamType.STRING)
    Optional<String> threadIdHmac();
}
