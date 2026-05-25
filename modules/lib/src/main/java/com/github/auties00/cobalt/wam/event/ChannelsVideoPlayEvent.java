package com.github.auties00.cobalt.wam.event;

import com.github.auties00.cobalt.meta.annotation.WhatsAppWebModule;

import com.github.auties00.cobalt.wam.annotation.WamEvent;
import com.github.auties00.cobalt.wam.model.WamEventSpec;
import com.github.auties00.cobalt.wam.annotation.WamProperty;
import com.github.auties00.cobalt.wam.model.WamType;
import com.github.auties00.cobalt.wam.type.VideoPlayOrigin;
import com.github.auties00.cobalt.wam.type.VideoPlayResult;
import com.github.auties00.cobalt.wam.type.VideoPlayType;

import java.time.Instant;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;

@WhatsAppWebModule(moduleName = "WAWebChannelsVideoPlayWamEvent")
@WamEvent(id = 6556)
public interface ChannelsVideoPlayEvent extends WamEventSpec {
    @WamProperty(index = 1, type = WamType.INTEGER)
    OptionalInt autoPlayT();

    @WamProperty(index = 2, type = WamType.STRING)
    Optional<String> cid();

    @WamProperty(index = 13, type = WamType.INTEGER)
    OptionalInt finishCount();

    @WamProperty(index = 3, type = WamType.INTEGER)
    OptionalInt height();

    @WamProperty(index = 4, type = WamType.STRING)
    Optional<String> postId();

    @WamProperty(index = 5, type = WamType.INTEGER)
    OptionalInt videoDuration();

    @WamProperty(index = 6, type = WamType.TIMER)
    Optional<Instant> videoInitialBufferingT();

    @WamProperty(index = 7, type = WamType.ENUM)
    Optional<VideoPlayOrigin> videoPlayOrigin();

    @WamProperty(index = 8, type = WamType.ENUM)
    Optional<VideoPlayResult> videoPlayResult();

    @WamProperty(index = 9, type = WamType.INTEGER)
    OptionalInt videoPlayT();

    @WamProperty(index = 10, type = WamType.ENUM)
    Optional<VideoPlayType> videoPlayType();

    @WamProperty(index = 11, type = WamType.FLOAT)
    OptionalDouble videoSize();

    @WamProperty(index = 14, type = WamType.STRING)
    Optional<String> watchingModule();

    @WamProperty(index = 12, type = WamType.INTEGER)
    OptionalInt width();
}
