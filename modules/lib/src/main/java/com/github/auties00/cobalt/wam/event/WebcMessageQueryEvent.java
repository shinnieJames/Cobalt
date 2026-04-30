package com.github.auties00.cobalt.wam.event;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;

import com.github.auties00.cobalt.wam.annotation.WamEvent;
import com.github.auties00.cobalt.wam.model.WamEventSpec;
import com.github.auties00.cobalt.wam.annotation.WamProperty;
import com.github.auties00.cobalt.wam.model.WamType;
import com.github.auties00.cobalt.wam.type.WebcChatType;
import com.github.auties00.cobalt.wam.type.WebcMessageQueryDirection;
import com.github.auties00.cobalt.wam.type.WebcQueryTriggerType;

import java.time.Instant;
import java.util.Optional;
import java.util.OptionalInt;

@WhatsAppWebModule(moduleName = "WAWebWebcMessageQueryWamEvent")
@WamEvent(id = 1876, releaseWeight = 5)
public interface WebcMessageQueryEvent extends WamEventSpec {
    @WamProperty(index = 14, type = WamType.INTEGER)
    OptionalInt webcAudioMessageCount();

    @WamProperty(index = 1, type = WamType.STRING)
    Optional<String> webcBrowserNetworkType();

    @WamProperty(index = 20, type = WamType.INTEGER)
    OptionalInt webcBrowserStorageQuotaBytes();

    @WamProperty(index = 21, type = WamType.INTEGER)
    OptionalInt webcBrowserStorageQuotaUsedBytes();

    @WamProperty(index = 2, type = WamType.INTEGER)
    OptionalInt webcChatPosition();

    @WamProperty(index = 13, type = WamType.ENUM)
    Optional<WebcChatType> webcChatType();

    @WamProperty(index = 16, type = WamType.INTEGER)
    OptionalInt webcDocumentMessageCount();

    @WamProperty(index = 11, type = WamType.INTEGER)
    OptionalInt webcEarliestMessageIndex();

    @WamProperty(index = 12, type = WamType.TIMER)
    Optional<Instant> webcEarliestMessageT();

    @WamProperty(index = 4, type = WamType.INTEGER)
    OptionalInt webcMessageCount();

    @WamProperty(index = 19, type = WamType.ENUM)
    Optional<WebcQueryTriggerType> webcMessageQueryTrigger();

    @WamProperty(index = 3, type = WamType.ENUM)
    Optional<WebcMessageQueryDirection> webcMessageQueryType();

    @WamProperty(index = 18, type = WamType.INTEGER)
    OptionalInt webcOtherMessageCount();

    @WamProperty(index = 7, type = WamType.INTEGER)
    OptionalInt webcPhotoMessageCount();

    @WamProperty(index = 15, type = WamType.INTEGER)
    OptionalInt webcPttMessageCount();

    @WamProperty(index = 9, type = WamType.TIMER)
    Optional<Instant> webcQueryT();

    @WamProperty(index = 10, type = WamType.INTEGER)
    OptionalInt webcResponseBytes();

    @WamProperty(index = 17, type = WamType.INTEGER)
    OptionalInt webcStickerMessageCount();

    @WamProperty(index = 5, type = WamType.INTEGER)
    OptionalInt webcTextMessageCount();

    @WamProperty(index = 6, type = WamType.INTEGER)
    OptionalInt webcVideoMessageCount();
}
