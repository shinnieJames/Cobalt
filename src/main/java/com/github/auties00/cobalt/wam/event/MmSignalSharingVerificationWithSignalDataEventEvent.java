package com.github.auties00.cobalt.wam.event;

import com.github.auties00.cobalt.wam.annotation.WamEvent;
import com.github.auties00.cobalt.wam.annotation.WamProperty;
import com.github.auties00.cobalt.wam.type.WamChannel;
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

@WamEvent(id = 6856, channel = WamChannel.PRIVATE, privateStatsId = 0)
public interface MmSignalSharingVerificationWithSignalDataEventEvent extends WamEventSpec {
    @WamProperty(index = 1, type = WamType.STRING)
    Optional<String> entSourceSubplatform();

    @WamProperty(index = 2, type = WamType.BOOLEAN)
    Optional<Boolean> isCompanionDevice();

    @WamProperty(index = 16, type = WamType.BOOLEAN)
    Optional<Boolean> isNetworkAvailable();

    @WamProperty(index = 17, type = WamType.BOOLEAN)
    Optional<Boolean> isShimmingSignal();

    @WamProperty(index = 3, type = WamType.BOOLEAN)
    Optional<Boolean> isUserDisclosed();

    @WamProperty(index = 4, type = WamType.BOOLEAN)
    Optional<Boolean> isUserMatched();

    @WamProperty(index = 5, type = WamType.STRING)
    Optional<String> mmSignalData();

    @WamProperty(index = 6, type = WamType.ENUM)
    Optional<OnePdSignalNotSharedReason> onePdSignalNotSharedReason();

    @WamProperty(index = 7, type = WamType.ENUM)
    Optional<SignalCanceledReason> signalCanceledReason();

    @WamProperty(index = 8, type = WamType.ENUM)
    Optional<SignalMessageState> signalMessageState();

    @WamProperty(index = 9, type = WamType.ENUM)
    Optional<SignalMessageType> signalMessageType();

    @WamProperty(index = 10, type = WamType.ENUM)
    Optional<SignalOrigin> signalOrigin();

    @WamProperty(index = 11, type = WamType.ENUM)
    Optional<SignalSharingStatus> signalSharingStatus();

    @WamProperty(index = 12, type = WamType.ENUM)
    Optional<SignalSurface> signalSurface();

    @WamProperty(index = 13, type = WamType.ENUM)
    Optional<SignalType> signalType();

    @WamProperty(index = 14, type = WamType.ENUM)
    Optional<SpSignalNotSharedReason> spSignalNotSharedReason();
}
