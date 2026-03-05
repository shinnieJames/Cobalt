package com.github.auties00.cobalt.wam.event;

import com.github.auties00.cobalt.wam.annotation.WamEvent;
import com.github.auties00.cobalt.wam.annotation.WamProperty;
import com.github.auties00.cobalt.wam.type.WamType;
import com.github.auties00.cobalt.wam.type.CtwaLabelTarget;
import com.github.auties00.cobalt.wam.type.CtwaLabelType;
import com.github.auties00.cobalt.wam.type.CustomerAdsSharingSettingEnabled;

import java.util.Optional;
import java.util.OptionalInt;

@WamEvent(id = 4662)
public interface CtwaLabelSignalEvent extends WamEventSpec {
    @WamProperty(index = 11, type = WamType.INTEGER)
    OptionalInt ctwaConversationDepth();

    @WamProperty(index = 1, type = WamType.INTEGER)
    OptionalInt ctwaLabelSignalVersion();

    @WamProperty(index = 2, type = WamType.ENUM)
    Optional<CtwaLabelTarget> ctwaLabelTarget();

    @WamProperty(index = 3, type = WamType.ENUM)
    Optional<CtwaLabelType> ctwaLabelType();

    @WamProperty(index = 9, type = WamType.STRING)
    Optional<String> ctwaSignalMetadata();

    @WamProperty(index = 10, type = WamType.ENUM)
    Optional<CustomerAdsSharingSettingEnabled> customerAdsSharingSettingEnabled();

    @WamProperty(index = 4, type = WamType.STRING)
    Optional<String> deepLinkConversionData();

    @WamProperty(index = 5, type = WamType.STRING)
    Optional<String> deepLinkConversionSource();

    @WamProperty(index = 6, type = WamType.BOOLEAN)
    Optional<Boolean> eventSharingSettingEnabled();

    @WamProperty(index = 7, type = WamType.BOOLEAN)
    Optional<Boolean> globalSharingSettingEnabled();

    @WamProperty(index = 8, type = WamType.STRING)
    Optional<String> threadIdHmac();
}
